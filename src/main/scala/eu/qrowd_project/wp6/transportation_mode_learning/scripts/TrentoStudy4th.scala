package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDate}
import java.util.Locale

import com.typesafe.config.ConfigFactory
import eu.qrowd_project.wp6.transportation_mode_learning.util.{AccelerometerRecord, CassandraDBConnector, GeoJSONConverter, HaversineDistance, JSONExporter, LocationEventRecord, OutlierDetecting, ProvenanceRecorder, ReverseGeoCoderOSM, ReverseGeoCodingTomTom, SQLiteAcces, SQLiteDB, TrackPoint, TrackpointUtils}
import eu.qrowd_project.wp6.transportation_mode_learning.{Pilot4Stage, Prediction}
import scopt.Read

/**
  * Object to run the 4th Trento user study.
  *
  * The steps to perform for each user are
  *
  * 1) Check which users were already processed for the given day
  * 2) Get a user's GPS data of a whole day from the QROWD DB, remove
  *    outliers and store it to disk for debugging purposes
  * 3) Find start and stop points to extract (possibly multi-modal) trips
  * 4) Write out trip data for debugging purposes
  * 5) Get a user's accelerometer data of a whole day from the QROWD DB
  * 6) For each detected trip...
  *    a) write trip information to SQLite database
  *    b) extract corresponding accelerometer data from the whole day
  *       accelerometer reading
  *    c) extract stages (i.e. segments within the respective trip which
  *       describe a user's movement entirely performed with just one certain
  *       means of transportation) based on the accelerometer data
  *    d) For each extracted stage get the GPS data corresponding to stage and
  *       write them to an SQLite database
  *    e) Store the extracted trip annotated with the detected transportation
  *       modes
  */
object TrentoStudy4th extends SQLiteAcces with OutlierDetecting with JSONExporter with ReverseGeoCodingTomTom {
  private val logger = com.typesafe.scalalogging.Logger("Mode detection")

  private val appConfig = ConfigFactory.load()
  private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
  private val outputDirPath =
    Paths.get(appConfig.getString("general_stettings.output_data_dir"))

  private lazy val cassandraDB = new CassandraDBConnector

  private val clusterBasedTripDetector = new ClusterBasedTripDetection()
  private val windowDistanceBasedTripDetector = new WindowDistanceBasedTripDetection(
    windowSize = appConfig.getInt("stop_detection.window_distance.window_size"),
    stepSize = appConfig.getInt("stop_detection.window_distance.step_size"),
    distanceThresholdInKm = appConfig.getDouble("stop_detection.window_distance.distance"),
    minNrOfSegments = appConfig.getInt("stop_detection.window_distance.min_nr_of_segments"),
    noiseSegments = appConfig.getInt("stop_detection.window_distance.noise_segments"))

  private lazy val pythonScriptPath =
    appConfig.getString("prediction_settings.python_script_path")
  private lazy val predictionWindowSize =
    appConfig.getInt("prediction_settings.window_size")

  private lazy val predicter = new Prediction(pythonScriptPath,
    s"$pythonScriptPath/run/trentostudy4.py",
    s"$pythonScriptPath/models/")

  private lazy val maxTimeGapForGPSInSecs =
    appConfig.getInt("stop_detection.max_time_gap") * 60

  private val fallbackReverseGeoCoder = ReverseGeoCoderOSM()

  import scopt.Read.reads
  implicit val dateRead: Read[LocalDate] =
    reads(x => LocalDate.parse(x, DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)))

  private val parser = new scopt.OptionParser[TrentoStudyConfig]("4th Trento User Study") {
    head("4th Trento Study", "0.0.1")

    opt[LocalDate]('d', "date")
      .valueName("<yyyMMdd>")
      .action((value, config) => config.copy(date = value))
      .text("Date to be processed (yyyyMMdd), e.g. 20180330 . If no date is " +
        "provided, we'll use the current date.")

    opt[Unit](name = "debug")
      .optional()
      .action((_, config) => config.copy(writeDebugOutput = true))
      .text("If added, debug information (GPS traces as GeoJSON files, " +
        "classifier output, ...)) will be written to the debug directory " +
        "defined in the general settings.")

    opt[String]('t',"tripsqlite")
      .valueName("trip SQLite file path")
      .action((value, config) => config.copy(tripSQLiteFilePath = value))
      .text("The file path to the SQLte DB file where extracted tips will be " +
        "stored")

    opt[String]('s', "stagesqlite")
      .valueName("stage SQLite file path")
      .action((value, config) => config.copy(stageSQLiteFilePath = value))
      .text("The file path to the SQLite DB file where extracted stages will " +
        "be stored")

    opt[Unit]("dryrun")
      .optional()
      .action((_, config) => config.copy(dryRun = true))
      .text("If added, no results will be written to any of the output " +
        "SQLite databases, nor debug output will be written")

    help("help").text("prints this usage text")
  }

  private def getProcessedUsers(dateString: String): Set[String] = {
    getUsersWithDataForDate(dateString)
  }

  private def extractTrips(trajectory: Seq[TrackPoint], provenanceRecorder: ProvenanceRecorder): Seq[Trip] = {
    var tripDetector: TripDetection = null
    var fallbackTripDetector: TripDetection = null

    if (trajectory.size >= appConfig.getInt(
      "stop_detection.clustering.min_required_points")) {
      tripDetector = clusterBasedTripDetector
      fallbackTripDetector = windowDistanceBasedTripDetector

    } else {
      tripDetector = windowDistanceBasedTripDetector
      fallbackTripDetector = clusterBasedTripDetector
    }

    /* Split trajectory at gaps. This should avoid missing start/stop point
     * especially in case of sparse trajectories and clustering-based trip
     * detection. Here it occurred that more or less whole day trajectory were
     * returned since the trajectories in fact did not contain any clusters.
     * The idea here is to assume a start/stop point if there is a longer gap
     * without any GPS information. What a too big gap is, is defined by the
     * configuration parameter max_time_gap.
     */
    val trajectories = trajectory.foldLeft(Seq.empty[Seq[TrackPoint]])((trajectories, point) => {
      if (trajectories.isEmpty) {
        Seq(Seq(point))
      } else {
        val lastTimestamp = trajectories.last.last.timestamp.getTime
        val thisTimestamp = point.timestamp.getTime
        if (((thisTimestamp - lastTimestamp) / 1000.0) > maxTimeGapForGPSInSecs) {
          trajectories ++ List(List(point))
        } else {
          trajectories.reverse match {
            case last :: firsts => firsts.reverse ++ List(last ++ List(point))
          }
        }
      }
    })

    trajectories.flatMap(trajectory => {
      var trips: Seq[Trip] = tripDetector.find(trajectory)

      if (trips.isEmpty) {
        logger.info("trying fallback algorithm...")
        trips = fallbackTripDetector.find(trajectory)

        provenanceRecorder.setTripDetectionSettings(fallbackTripDetector.getSettings)
      } else {
        provenanceRecorder.setTripDetectionSettings(tripDetector.getSettings)
      }

      if (trips.isEmpty) {
        trips
      } else {

        val minimum_trip_distance_in_mins = appConfig.getDouble(
          "stop_detection.mininmum_trip_distance")
        val minimum_trip_distance_in_ms = minimum_trip_distance_in_mins * 60 * 1000
        val initialTripCount = trips.size

        /* Post processing *
         *
         * In some cases we observed the pattern that there were alternating phases
         * of very sparse GPS signals (or even no GPS information at all) and phases
         * with dense GPS readings (i.e. with many GPS points per minute). This
         * confused, e.g. the clustering based trip detection since essentially many
         * clusters were detected which in turn resulted in the (false) detection of
         * a high number of very short and close trips. To remedy this we perform a
         * post processing step where we merge two consecutive trips if the second
         * one started within <minimum_trip_distance> minutes after the first one
         * ended. */
        if (trips.head.isInstanceOf[ClusterTrip]) {
          /* Trips were extracted by space-time clustering approach. */
          trips = trips.foldLeft(Seq.empty[Trip])((acc, trip) => {
            if (acc.isEmpty) {
              Seq(trip)
            } else {
              val before = acc.last.asInstanceOf[ClusterTrip]
              val after = trip.asInstanceOf[ClusterTrip]
              if ((after.start.timestamp.getTime - before.end.timestamp.getTime) < minimum_trip_distance_in_ms) {
                // merge
                logger.info(s"Merging two trips: (${before.start.timestamp} - ${before.end.timestamp}) and (${after.start.timestamp} - ${after.end.timestamp})")
                acc.slice(0, acc.size - 1) ++ Seq(ClusterTrip(
                  start = before.start,
                  end = after.end,
                  trace = before.trace
                    ++ before.endCluster.slice(1, before.endCluster.size - 2)
                    ++ after.trace,
                  startCluster = before.startCluster,
                  endCluster = after.endCluster))
              } else {
                acc ++ Seq(trip)
              }
            }
          })

        } else {
          /* Trips were extracted by looking at fixed time windows and the distance
           * a citizen moved within the given time period. */
          trips = trips.foldLeft(Seq.empty[Trip])((acc, trip) => {
            if (acc.isEmpty) {
              Seq(trip)
            } else {
              val before = acc.last
              val after = trip
              if ((after.start.timestamp.getTime - before.end.timestamp.getTime) < minimum_trip_distance_in_ms) {
                logger.info(s"Merging two trips: (${before.start.timestamp} - ${before.end.timestamp}) and (${after.start.timestamp} - ${after.end.timestamp})")
                // merge
                acc.slice(0, acc.size - 1) ++ Seq(NonClusterTrip(
                  start = before.start,
                  end = after.end,
                  trace = before.trace ++ after.trace))
              } else {
                acc ++ Seq(trip)
              }
            }
          })
        }

        val tripCountAfterPostProcessing = trips.size
        if (tripCountAfterPostProcessing < initialTripCount) {
          provenanceRecorder.tripsWereMergedInPostProcessing = true
        }
        provenanceRecorder.addTrips(trips)

        logger.info(s"#trips: ${trips.size}")
        logger.info(s"trip details: ${
          trips.zipWithIndex.map {
            case (trip, idx) =>
              s"trip$idx ${trip.start} - ${trip.end} : ${trip.trace.size} points"
          }
            .mkString("\n")
        }")

        trips
      }
    })
  }

  private def writeGeoJSON(outFilePath: Path, trip: Trip): Unit = {
    val filePath = outFilePath.toString

    val startStopPointsJSON = GeoJSONConverter.merge(
      GeoJSONConverter.toGeoJSONPoints(Seq(trip.start), Map("marker-symbol" -> "s")),
      GeoJSONConverter.toGeoJSONPoints(Seq(trip.end), Map("marker-symbol" -> "e")))

    val numPoints = trip.trace.size
    val innerPointsJSON = GeoJSONConverter.toGeoJSONPoints(trip.trace.slice(1, numPoints-1))
    val tripJSON = GeoJSONConverter.toGeoJSONLineString(trip.trace)
    val lineJSON = GeoJSONConverter.merge(startStopPointsJSON, tripJSON)
    val pointsJSON = GeoJSONConverter.merge(startStopPointsJSON, innerPointsJSON)
    val lineWithPointsJSON = GeoJSONConverter.merge(lineJSON, pointsJSON)

    write(lineJSON, filePath + "_lines.json")
    write(pointsJSON, filePath + "_points.json")
    write(lineWithPointsJSON, filePath + "_lines_with_points.json")
  }

  private def storeWholeDayTrajectoryData(userID: String, dateStr: String, wholeDaysGPSData: Seq[LocationEventRecord]): Unit = {
    val targetDir = outputDirPath.resolve(dateStr).resolve(userID)
    Files.createDirectories(targetDir)

    val wholeDayTrace: Seq[TrackPoint] = wholeDaysGPSData.map(
      e => TrackPoint(e.latitude, e.longitude, e.timestamp))
    val wholeDay: Trip = NonClusterTrip(wholeDayTrace.head, wholeDayTrace.last, wholeDayTrace)

    writeGeoJSON(targetDir.resolve("whole_day"), wholeDay)
  }

  private def storeExtractedTripsData(userID: String, dateStr: String, trips: Seq[Trip]): Unit = {
    val targetDir = outputDirPath.resolve(dateStr).resolve(userID)
    Files.createDirectories(targetDir)

    trips.zipWithIndex.foreach {
      case (trip, idx) => {
        val path = targetDir.resolve(s"trip$idx")
        writeGeoJSON(path, trip)
      }
    }
  }

  private def compress[A](l: List[A], fn: (A, A) => Boolean):List[A] = l.foldLeft(List[A]()) {
    case (List(), e) => List(e)
    case (ls, e) if fn(ls.last, e) => ls
    case (ls, e) => ls:::List(e)
  }

  // TODO
  private def computeTransitionPoints(trip: Trip, modes: List[(String, Double, Timestamp)]) = {
    val f = (e1: (String, Double, Timestamp), e2: (String, Double, Timestamp)) => e1._1 == e2._1
    // compress the data
    // i.e. (mode1, mode1, mode1, mode2, mode2, mode1, mode3) -> (mode1, mode2, mode1, mode3)
    val compressedModes = compress(modes, f)

    // we might have no points between start and end, thus, we wrap it here
    val trajectory = Seq(trip.start) ++ trip.trace ++ Seq(trip.end)

    // we generate pairs of GPS points, i.e. from (p1, p2, p3, ..., pn) we get ((p1, p2), (p2, p3), ..., (p(n-1), pn)
    val gpsPairs = trajectory zip trajectory.tail

    // compute segments with the used mode
    val startEndWithMode = gpsPairs.flatMap {
      case (tp1, tp2) =>
        val begin = tp1.timestamp
        val end = tp2.timestamp

        // bearing
        val bearing = TrackpointUtils.bearing(tp1, tp2)

        // total time between t2 and t1 in ms
        val timeTotalMs = Duration.between(tp2.timestamp.toLocalDateTime, tp1.timestamp.toLocalDateTime).toMillis

        // total distance
        val distanceTotalKm = HaversineDistance.compute(tp1, tp2)


        // get all modes in time range between both GPS points
        val modesBetween = compressedModes.filter(e => e._3.after(begin) && e._3.before(end))

        if (compressedModes.isEmpty) {
          Seq((tp1, tp2, "car"))  // FIXME
        } else if (modesBetween.isEmpty) { // Handle no mode change between both points
          // This happens due to compression. In this case just take the last
          // known mode. We might not have seen any mode before because
          // i) it might be the first point at all or
          // ii) the first in the trip split
          // -> we take the first mode
          val modesBefore = compressedModes.filter(e => e._3.before(tp1.timestamp))
          val mode = if (modesBefore.nonEmpty) modesBefore.last else compressedModes.head
          Seq((tp1, tp2, mode._1))

        } else if (modesBetween.size == 1) { // Handle single mode change between both points
          // compute the split point
          val modeChange = modesBetween.head
          val timeMs = Duration.between(modeChange._3.toLocalDateTime, begin.toLocalDateTime).toMillis
          val ratio = timeMs.toDouble / timeTotalMs
          val distanceKm = ratio * distanceTotalKm
          val splitPoint = TrackpointUtils.pointFrom(tp1, bearing, distanceKm)
          val newTP = TrackPoint(splitPoint.lat, splitPoint.long, modeChange._3)

          // get the last mode before the starting point if exists, otherwise use mode change inside
          val lastMode = compressedModes.filter(e => e._3.before(tp1.timestamp)).lastOption.getOrElse(modeChange)

          Seq((tp1, newTP, lastMode._1), (newTP, tp2, modeChange._1))

        } else {
          // for each mode we compute the distance taken based on time ratio
          // it contains a point and the mode used to this point
          val intermediatePointsWithMode = (modesBetween zip modesBetween.tail).map {
            case ((mode1, maxValue1, t1),(mode2, maxValue2, t2)) =>
              val timeMs = Duration.between(t2.toLocalDateTime, begin.toLocalDateTime).toMillis

              val ratio = timeMs.toDouble / timeTotalMs

              val distanceKm = ratio * distanceTotalKm

              val newPoint = TrackpointUtils.pointFrom(tp1, bearing, distanceKm)

              (TrackPoint(newPoint.lat, newPoint.long, t2), mode1)
          }

          // generate pairs of points with the mode used in between
          // TODO actually, the first mode should come from before the GPS point instead of the next mode change, but just for rendering it's ok
          val first = Seq(
            (tp1, intermediatePointsWithMode.head._1, intermediatePointsWithMode.head._2))

          val mid =  (intermediatePointsWithMode zip intermediatePointsWithMode.tail).map {
            case ((p1, mode1), (p2, mode2)) =>
              (p1, p2, mode2)
          }
          val last = Seq(
            (intermediatePointsWithMode.last._1, tp2, intermediatePointsWithMode.last._2))

          first ++ mid ++ last
        }
    }

    // keep only the transition points
    val f2 = (e1: (TrackPoint, TrackPoint, String), e2: (TrackPoint, TrackPoint, String)) =>
      e1._3 == e2._3
    val startEndWithModeCompressed = compress(startEndWithMode.toList, f2)

    startEndWithModeCompressed.map {
      case (t1, t2, mode) => (t1, mode)
    }
  }

  /**
    * FIXME: can be removed after clean up
    */
  private def getModeProbabilityAvg(
                                     transitionPoints: List[(TrackPoint, String)],
                                     modesWProbAndTimeStamp: Seq[(String, Double, Timestamp)]): Double = {
    val start: Timestamp = transitionPoints.head._1.timestamp
    val stop: Timestamp = transitionPoints(1)._1.timestamp

    val modeProps: Seq[Double] = modesWProbAndTimeStamp
      .filter(e => (e._3.equals(start) || e._3.after(start))
        && (e._3.equals(stop) || e._3.before(stop)))
      .map(_._2)

    if (modeProps.isEmpty) {
      0
    } else {
      modeProps.sum / modeProps.size
    }
  }

//  private def getStreet(long: Double, lat: Double): String = {
//    fallbackReverseGeoCoder.addressLookup(long, lat)
//  }

  private def buildStage(userID: String, start: TrackPoint, stop: TrackPoint,
                         mode: String, overallTrip: Trip, confidence: Double,
                         jsonFilePath: String): Pilot4Stage = {

    var points: Seq[TrackPoint] = overallTrip.trace.filter(point => point.timestamp.after(start.timestamp) && point.timestamp.before(stop.timestamp))

    points = Seq(start) ++ points ++ Seq(stop)

    val startAddress = getStreet(start.long, start.lat)
    val stopAddress = getStreet(stop.long, start.lat)

    Pilot4Stage(userID, mode, start, startAddress, stop, stopAddress,
      modeConfidence = confidence, trajectory = points, jsonFilePath = jsonFilePath)
  }

  private def buildStages(userID: String, day: String, trip: Trip, tripIdx: Int,
                          modesWProbAndTimeStamp: Seq[(String, Double, Timestamp)],
                          writeDebugOutput: Boolean, jsonFilePath: String): List[Pilot4Stage] = {
    // compute transition points
    val transitionPointsWithMode: List[(TrackPoint, String)] =
      computeTransitionPoints(trip, modesWProbAndTimeStamp.toList)

    Files.write(
      outputDirPath.resolve(day).resolve(userID)
        .resolve(s"transition_points_trip$tripIdx.out"),
      transitionPointsWithMode.mkString("\n").getBytes(Charset.forName("UTF-8")))

    transitionPointsWithMode.sliding(2).map(pointsWMode => {
      if (pointsWMode.size > 1) {
        val startPoint: TrackPoint = pointsWMode(0)._1
        val stopPoint: TrackPoint = pointsWMode(1)._1
        val mode: String = pointsWMode(0)._2
        val avgProbability = getModeProbabilityAvg(pointsWMode, modesWProbAndTimeStamp)
        buildStage(userID, startPoint, stopPoint, mode, trip, avgProbability, jsonFilePath)

      } else {
        // just one transition point means just one mode was used for the whole trip
        // or we're in the last stage
        val startPoint: TrackPoint = pointsWMode(0)._1
        val mode: String = pointsWMode(0)._2
        val avgProbability = getModeProbabilityAvg(pointsWMode ++ List((trip.end, "")), modesWProbAndTimeStamp)
        buildStage(userID, startPoint, trip.end, mode, trip, avgProbability, jsonFilePath)
      }
    }).toList
  }


  def run(config: TrentoStudyConfig): Unit = {
    // set up SQLite DBs
    connect(config.tripSQLiteFilePath, config.stageSQLiteFilePath)

    // 1) Check which users were already processed for the given day
//    val allUsers: List[KeyspaceMetadata] = cassandraDB.getAllUsers()
    val allUsers: Set[String] = getAllUsers()
    val dateStr: String = config.date.format(formatter)
    val processedUsers = getProcessedUsers(dateStr)

////    val usersToProcess = allUsers.filter(ks => !processedUsers.contains(ks.getName))
    val usersToProcess = allUsers.diff(processedUsers).toList
//    val usersToProcess = allUsers.map(_.getName)

    // 2) Get a user's GPS data of a whole day from the QROWD DB...
    val gpsAccuracy = appConfig.getInt("stop_detection.gps_accuracy")
    val gpsData: Seq[(String, Seq[LocationEventRecord])] = cassandraDB.readData(
      dateStr, gpsAccuracy, usersToProcess)

    for ((userID, daysGPSData) <- gpsData) {
      val provRecorder = new ProvenanceRecorder(userID, dateStr)

      logger.info(s"Perfoming mode detection for user $userID")
      if (daysGPSData.nonEmpty) {
        storeWholeDayTrajectoryData(userID, dateStr, daysGPSData)
        // 2) ...and remove the outliers
        val daysFullGPSTrajectory = daysGPSData
          .map(e => TrackPoint(e.latitude, e.longitude, e.timestamp))
        val outliers = detectOutliers(daysFullGPSTrajectory)
        val daysCleanedGPSTrjectory: Seq[TrackPoint] = daysFullGPSTrajectory.diff(outliers)
        assert(daysCleanedGPSTrjectory.size == (daysFullGPSTrajectory.size - outliers.size))

        if (outliers.nonEmpty) {
          logger.info(s"Detected and removed ${outliers.size} outlier(s)")
          provRecorder.setNumDetectedOutliers(outliers.size)
        }

        // 3) Find start and stop usterpoints to extract (possibly multi-modal) trips
        val trips: Seq[Trip] = extractTrips(daysCleanedGPSTrjectory, provRecorder)

        if (trips.isEmpty) {
          logger.info("No trips detected.")
        } else {

          // 4) Write out trip data for debugging purposes
          storeExtractedTripsData(userID, dateStr, trips)

          // 5) Get a user's accelerometer data of a whole day from the QROWD DB
          val fullDayAccelerometerData: Seq[AccelerometerRecord] =
            if (trips.nonEmpty) {
              cassandraDB.getAccDataForUserAndDay(userID, config.date.format(formatter))
            } else {
              Seq.empty[AccelerometerRecord]
            }

          // 6) For each detected trip...
          for ((trip, tripIdx) <- trips.zipWithIndex) {
            // a) write trip information to SQLite database
            val startAddress = getStreet(trip.start.long, trip.start.lat)
            val stopAddress = getStreet(trip.end.long, trip.end.lat)


            // get trip ID
            val tripID = getLastIndex(SQLiteDB.Stages)

            // b) extract corresponding accelerometer data from the whole day
            //    accelerometer reading
            val filteredAccelerometerData = fullDayAccelerometerData
              .filter(
                e => e.timestamp.after(trip.start.timestamp)
                  && e.timestamp.before(trip.end.timestamp))

            // c) extract stages (i.e. segments within the respective trip which
            //    describe a user's movement entirely performed with just one
            //    certain means of transportation) based on the accelerometer data

            // run mode detection
            logger.info(s"Running mode detection for user $userID on trip " +
              s"$tripIdx ${trip.start.timestamp.toLocalDateTime.toString} - " +
              s"${trip.end.timestamp.toLocalDateTime.toString}")

            predicter.outputDir = outputDirPath.resolve(dateStr).resolve(userID)

            // We have to hand in provenance recorder since some information like
            // the algorithm which generated the prediction are of no use here
            // and thus discarded inside the predicter.predict( ) method.
            val res = predicter.predict(
              trip, filteredAccelerometerData, userID, tripIdx, Some(provRecorder))
            // e.g. ArrayBuffer((walk,0.983933,2018-04-13 09:15:07.981))
            val modesWProbAndTimeStamp: Seq[(String, Double, Timestamp)] = res._1
            var jsonFilePath = res._2
            assert(jsonFilePath.startsWith(outputDirPath.toString))
            jsonFilePath = jsonFilePath.substring(outputDirPath.toString.size + 1)

            val stages: List[Pilot4Stage] = buildStages(
              userID, dateStr, trip, tripIdx, modesWProbAndTimeStamp,
              config.writeDebugOutput, jsonFilePath)

            if (!config.dryRun) {
              stages.foreach(writeStageInfoWithJSONFilePath(_))
            }
            //
            //          if (config.writeDebugOutput) {
            //            stages.zipWithIndex.foreach(stageWIdx => {
            //              val s = stageWIdx._1
            //              val stageIdx = stageWIdx._2
            ////              val mapMatched: Seq[TrackPoint] = mapMatching(s.trajectory, s.mode)
            ////              val path = outputDirPath.resolve(dateStr).resolve(userID)
            ////                .resolve(s"map_matched_stage_${tripIdx}_${stageIdx}")
            ////              writeGeoJSON(path, NonClusterTrip(s.start, s.stop, mapMatched))
            //            })
            //          }
          }
        }
      } else {
        logger.info("No GPS data. Skipping...")
        provRecorder.addError("No GPS data")
      }

      provRecorder.close
    }
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, TrentoStudyConfig()) match {
      case Some(config) =>
        try {
          run(config)
        } catch {
          case e: IOException => logger.error("Cannot create output directories.", e)
          case t: Throwable => logger.error("Failed to process the ILog data.", t)
        } finally {
          logger.info("finished mode detection run")
          cassandraDB.close()
          close()
        }
      case _ =>
    }
  }
}
