package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}
import java.sql.Timestamp
import java.time.{Duration, LocalDate}
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors
import java.util.{DoubleSummaryStatistics, Locale}

import com.typesafe.config.ConfigFactory
import eu.qrowd_project.wp6.transportation_mode_learning.mapmatching.{GraphhopperMapMatcherHttp, GraphhopperPublicTransitMapMatcherHttp}
import eu.qrowd_project.wp6.transportation_mode_learning.{Pilot2Stage, Predict}
import eu.qrowd_project.wp6.transportation_mode_learning.scripts.ModePredictionPilot2.{UserID, appConfig, rScriptPath}
import eu.qrowd_project.wp6.transportation_mode_learning.user.{ILogUsageMode, UserSettings}
import eu.qrowd_project.wp6.transportation_mode_learning.util.{AccelerometerRecord, CassandraDBConnector, GPXConverter, GeoJSONConverter, HaversineDistance, JSONExporter, LocationEventRecord, ReverseGeoCodingTomTom, SQLiteAcces, SQLiteDB, TrackPoint, TrackpointUtils}
import eu.qrowd_project.wp6.transportation_mode_learning.{Pilot2Stage, Predict}
import scopt.Read

import scala.collection.JavaConverters._
import scala.util.Try


case class TrentoStudyConfig(
                              date: LocalDate = LocalDate.now(),
                              writeDebugOutput: Boolean = false,
                              tripSQLiteFilePath: String =
                                Paths.get(System.getProperty("java.io.tmpdir"))
                                  .resolve("trips.sqlite").toString,
                              stageSQLiteFilePath: String =
                                Paths.get(System.getProperty("java.io.tmpdir"))
                                  .resolve("stages.sqlite").toString,
                              dryRun: Boolean = false
                            )

/**
  * Script to run the 3rd Trento user study.
  * The steps to perform for each user are
  * 1) getting a user's GPS data of a whole day from the QROWD DB (and remove
  *    outliers)
  * 2) getting the user's settings
  * 3) find start and stop points to extract (possibly multi-modal) trips
  * 4) write back the GPS points of the discovered trips to an SQLite database
  * 5) for each trip
  *    a) get the accelerometer reading from the QROWD DB
  *    b) based on the accelerometer data extract stages, i.e. segments within
  *       the respective trip which describe a user's movement entirely
  *       performed with one certain means of transportation
  *    c) for each extracted stage get the GPS data corresponding to stage and
  *       write them to an SQLite database
  *
  * Additionally there should be debug data written to the tmp directory:
  * D1) The whole day GPS trace as GeoJSON
  * D2) The GPS traces of the extracted trips as GeoJSON
  * D3) The mode whole detection output
  * D4) The GPS traces of the extracted stages
  */
object TrentoStudy3rd extends SQLiteAcces with JSONExporter with ReverseGeoCodingTomTom {
  private val logger = com.typesafe.scalalogging.Logger("Mode detection")
  private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
  private val dbgDirName = "3rd_trento_user_study"
  private val dbgDir = tmpDir.resolve(dbgDirName)
  private val appConfig = ConfigFactory.load()
  private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  private lazy val cassandra = new CassandraDBConnector

  private lazy val rScriptPath = appConfig.getString("prediction_settings.r_script_path")
  private lazy val predicter = new Predict(rScriptPath,
    s"$rScriptPath/run.r",
    s"$rScriptPath/model.rds")

  private val clusterBasedTripDetector = new ClusterBasedTripDetection()
  private val windowDistanceBasedTripDetector = new WindowDistanceBasedTripDetection(
    windowSize = appConfig.getInt("stop_detection.window_distance.window_size"),
    stepSize = appConfig.getInt("stop_detection.window_distance.step_size"),
    distanceThresholdInKm = appConfig.getDouble("stop_detection.window_distance.distance"),
    minNrOfSegments = appConfig.getInt("stop_detection.window_distance.min_nr_of_segments"),
    noiseSegments = appConfig.getInt("stop_detection.window_distance.noise_segments"))

  val mapMatcherURL = appConfig.getString("map_matching.graphhopper.map_matching_url")
  val mapMatcherPublicTransitURL = appConfig.getString("map_matching.graphhopper.routing_url")
  val mapMatcher = new GraphhopperMapMatcherHttp(mapMatcherURL)
  val mapMatcherPublicTransit = new GraphhopperPublicTransitMapMatcherHttp(mapMatcherPublicTransitURL)

  import scopt.Read.reads
  implicit val dateRead: Read[LocalDate] =
    reads(x => LocalDate.parse(x, DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)))

  private val parser = new scopt.OptionParser[TrentoStudyConfig]("3rd Trento User Study") {
    head("3rd Trento Pilot", "0.0.1")

    opt[LocalDate]('d', "date")
      .valueName("<yyyMMdd>")
      .action((value, config) => config.copy(date = value))
      .text("Date to be processed (yyyyMMdd), e.g. 20180330 . If no date is " +
        "provided, we'll use the current date.")

    opt[Unit](name = "debug")
      .optional()
      .action((_, config) => config.copy(writeDebugOutput = true))
      .text(s"If added, debug information (GPS traces as GeoJSON files, " +
        s"classifier output, ...)) will be written to SYSTEM_TEMP_FOLDER/" +
        s"$dbgDirName.")

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

  /** 2) Get user settings */
  def getUserSettings(userID: UserID): UserSettings = {
    val collectionMode =
      try {
        getCitizenCollectionMode(userID)
      } catch {
        case e: RuntimeException => {
          logger.error(s"No i-Log usage mode found for user $userID. Using " +
            s"CONTINUOUS as fallback.")
          ILogUsageMode.Continuous
        }
      }

    UserSettings(userID, collectionMode)
  }

  private def extractTrips(userID: UserID, daysGPSData: Seq[LocationEventRecord]): Seq[Trip] = {
    val settings = getUserSettings(userID)

    var tripDetector: TripDetection = null
    var fallbackTripDetector: TripDetection = null
    if (settings.iLogUsageMode.equals(ILogUsageMode.OnOff)) {
      tripDetector = windowDistanceBasedTripDetector
      fallbackTripDetector = clusterBasedTripDetector
    } else {
      tripDetector = clusterBasedTripDetector
      fallbackTripDetector = windowDistanceBasedTripDetector
    }

    logger.info(s"processing user $userID...")
    val trajectory = daysGPSData
      .map(e => TrackPoint(e.latitude, e.longitude, e.timestamp)).distinct
    logger.info(s"trajectory size:${trajectory.size}")

    // TODO: decide whether we want to apply our outlier detection here

    var trips: Seq[Trip] = tripDetector.find(trajectory)
    logger.info(s"#trips: ${trips.size}")
    logger.info(s"trip details: ${trips.zipWithIndex.map {
      case (trip, idx) => s"trip$idx ${trip.start} - ${trip.end} : ${trip.trace.size} points"}.mkString("\n")}")

    if (trips.isEmpty) {
      logger.info("trying fallback algorithm...")
      trips = fallbackTripDetector.find(trajectory)
    }

    trips
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

  private def compress[A](l: List[A], fn: (A, A) => Boolean):List[A] = l.foldLeft(List[A]()) {
    case (List(), e) => List(e)
    case (ls, e) if fn(ls.last, e) => ls
    case (ls, e) => ls:::List(e)
  }

  private def computeTransitionPoints(trip: Trip, bestModes: List[(String, Double, Timestamp)]) = {
    val f = (e1: (String, Double, Timestamp), e2: (String, Double, Timestamp)) => e1._1 == e2._1
    // compress the data
    // i.e. (mode1, mode1, mode1, mode2, mode2, mode1, mode3) -> (mode1, mode2, mode1, mode3)
    val compressedModes = compress(bestModes, f)

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

        if (modesBetween.isEmpty) { // handle no mode change between both points
          // this happens due to compression, just take the last known mode
          // we might not have seen any mode before because
          // i) it might be the first point at all or
          // ii) the first in the trip split
          // -> we take the first mode
          val modesBefore = compressedModes.filter(e => e._3.before(tp1.timestamp))
          val mode = if (modesBefore.nonEmpty) modesBefore.last else compressedModes.head
          Seq((tp1, tp2, mode._1))

        } else if (modesBetween.size == 1) { // handle single mode change between both points
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

  private def buildStage(userID: UserID, start: TrackPoint, stop: TrackPoint,
                         mode: String, overallTrip: Trip, confidence: Double): Pilot2Stage = {

    var points: Seq[TrackPoint] = overallTrip.trace.filter(point => point.timestamp.after(start.timestamp) && point.timestamp.before(stop.timestamp))

    points = Seq(start) ++ points ++ Seq(stop)

    val startAddress = getStreet(start.long, start.lat)
    val stopAddress = getStreet(stop.long, start.lat)

    Pilot2Stage(userID, mode, start, startAddress, stop, stopAddress,
      modeConfidence = confidence, trajectory = points)
  }

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

  private def buildStages(userID: UserID, day: String, trip: Trip, tripIdx: Int,
                          modesWProbAndTimeStamp: Seq[(String, Double, Timestamp)],
                          writeDebugOutput: Boolean): List[Pilot2Stage] = {
    // compute transition points
    val transitionPointsWithMode: List[(TrackPoint, String)] =
      computeTransitionPoints(trip, modesWProbAndTimeStamp.toList)

    if (writeDebugOutput) {
      Files.write(
        dbgDir.resolve(day).resolve(userID)
          .resolve(s"transition_points_trip$tripIdx.out"),
        transitionPointsWithMode.mkString("\n").getBytes(Charset.forName("UTF-8")))
    }

    transitionPointsWithMode.sliding(2).map(pointsWMode => {
      if (pointsWMode.size > 1) {
        val startPoint: TrackPoint = pointsWMode(0)._1
        val stopPoint: TrackPoint = pointsWMode(1)._1
        val mode: String = pointsWMode(0)._2
        val avgProbability = getModeProbabilityAvg(pointsWMode, modesWProbAndTimeStamp)
        buildStage(userID, startPoint, stopPoint, mode, trip, avgProbability)

      } else {
        // just one transition point means just one mode was used for the whole trip
        // or we're in the last stage
        val startPoint: TrackPoint = pointsWMode(0)._1
        val mode: String = pointsWMode(0)._2
        val avgProbability = getModeProbabilityAvg(pointsWMode ++ List((trip.end, "")), modesWProbAndTimeStamp)
        buildStage(userID, startPoint, trip.end, mode, trip, avgProbability)
      }
    }).toList
  }

  /**
    * perform the map matching here
    *
    * we distinguish between public transit (bus, train) and others
    *
    */
  private def mapMatching(trajectory: Seq[TrackPoint], mode: String): Seq[TrackPoint] = {

    // do map matching in case of "train" via routing API of GraphHopper - might fail ... //TODO fallback?

    try {
      val matchedTrip =
        if (mode == "train") {
          mapMatcherPublicTransit.query(trajectory, Some(mode))
        } else {
          mapMatcher.query(trajectory)
        }

      // convert GPX to trajectory
      if (matchedTrip.nonEmpty) {
        // the conversion will fail if there is no matched route in the GPX, in that case we simply return
        // the trajectory itself
        Try(GPXConverter.fromGPX(matchedTrip.get)) getOrElse (trajectory)
      } else {
        trajectory
      }
    } catch {
      case _: RuntimeException => trajectory
    }
  }

  private def run(config: TrentoStudyConfig): Unit = {
    /*
    (df0d,List())
    (c453,List(LocationEventRecord(20180606,2018-06-06 07:11:58.0,8.0,0.0,12.345,12.345,321.7357155313802,gps,0.0), LocationEventRecord(20180606,2018-06-06 07:13:02.0,12.0,0.0,12.345,12.345,356.78779045886023,gps,1.01), LocationEventRecord(20180606,2018-06-06 07:14:06.0,4.0,0.0,12.345,12.345,313.877595387066,gps,0.35), ...
    (a92d,List())
    (ecfb,List(LocationEventRecord(20180606,2018-06-06 00:00:17.0,19.164,-1.0,23.456,23.456,-1.0,network,-1.0), LocationEventRecord(20180606,2018-06-06 00:00:17.0,19.164,-1.0,23.456,23.456,-1.0,network,-1.0), LocationEventRecord(20180606,2018-06-06 00:01:17.0,19.159,-1.0,23.456,23.456,-1.0,network,-1.0), ...
    (feb1,List())
     */

    // 1) Get users' GPS data for the whole $day and filter out those that have
    //    an accuracy worse than $gpsAccuracy
    val day = config.date.format(formatter)
    val gpsAccuracy = appConfig.getInt("stop_detection.gps_accuracy")
    val gpsData: Seq[(UserID, Seq[LocationEventRecord])] = cassandra.readData(
      day, gpsAccuracy)

    for ((userID, daysGPSData) <- gpsData) {
      if (daysGPSData.nonEmpty) {
        val trips: Seq[Trip] = extractTrips(userID, daysGPSData)

        if (config.writeDebugOutput) {
          val targetDir = dbgDir.resolve(config.date.format(formatter)).resolve(userID)
          Files.createDirectories(targetDir)

          val wholeDayTrace: Seq[TrackPoint] = daysGPSData.map(
            e => TrackPoint(e.latitude, e.longitude, e.timestamp))
          val wholeDay: Trip = NonClusterTrip(wholeDayTrace.head, wholeDayTrace.last, wholeDayTrace)

          writeGeoJSON(targetDir.resolve("whole_day"), wholeDay)

          trips.zipWithIndex.foreach {
            case (trip, idx) => {
              val path = targetDir.resolve(s"trip$idx")
              writeGeoJSON(path, trip)
            }
          }
        }

        val fullDayAccelerometerData: Seq[AccelerometerRecord] = if (trips.nonEmpty) {
          cassandra.getAccDataForUserAndDay(userID, config.date.format(formatter))
        } else {
          Seq.empty[AccelerometerRecord]
        }


        for ((trip, tripIdx) <- trips.zipWithIndex) {
          // write to SQLite
          val startAddress = getStreet(trip.start.long, trip.start.lat)
          val stopAddress = getStreet(trip.end.long, trip.end.lat)
          if (!config.dryRun) {
            writeTripInfo(userID, startAddress, stopAddress, trip)
          }
          // get trip ID
          val tripID = getLastIndex(SQLiteDB.Trips)

          val filteredAccelerometerData = fullDayAccelerometerData
            .filter(
              e => e.timestamp.after(trip.start.timestamp)
                && e.timestamp.before(trip.end.timestamp))

          // run mode detection
          logger.info(s"Running mode detection for user $userID on trip " +
            s"$tripIdx ${trip.start.timestamp.toLocalDateTime.toString} - " +
            s"${trip.end.timestamp.toLocalDateTime.toString}")

          predicter.debug = config.writeDebugOutput
          predicter.debugOutputDir = dbgDir.resolve(config.date.format(formatter)).resolve(userID)

          val modesWProbAndTimeStamp: Seq[(String, Double, Timestamp)] =
            predicter.predict(trip, filteredAccelerometerData, userID, tripIdx)

          modesWProbAndTimeStamp.foreach(
            e => assert(e._3.after(trip.start.timestamp)
              && e._3.before(trip.end.timestamp)))
          val stages: List[Pilot2Stage] = buildStages(
            userID, day, trip, tripIdx, modesWProbAndTimeStamp, config.writeDebugOutput)

          if (config.writeDebugOutput) {
            stages.zipWithIndex.foreach(stageWIdx => {
              val s = stageWIdx._1
              val stageIdx = stageWIdx._2
              val mapMatched: Seq[TrackPoint] = mapMatching(s.trajectory, s.mode)
              val path = dbgDir.resolve(day).resolve(userID)
                .resolve(s"map_matched_stage_${tripIdx}_${stageIdx}")
              writeGeoJSON(path, NonClusterTrip(s.start, s.stop, mapMatched))
            })
          }

          // write results to SQLite
          if (!config.dryRun) {
            stages.foreach(s => writeStageInfo(s, tripID))
          }
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, TrentoStudyConfig()) match {
      case Some(config) =>
        try {
          // create the output dirs
          Files.createDirectories(dbgDir)
          connect(config.tripSQLiteFilePath, config.stageSQLiteFilePath)

          run(config)
        } catch {
          case e: IOException => logger.error("Cannot create output directories.", e)
          case t: Throwable => logger.error("Failed to process the ILog data.", t)
        } finally {
          println("finished mode detection run")
          close()
          // stop Cassandra here because when iterating we want to do it only once
          cassandra.close()
          predicter.rClient.stop()
        }
      case _ =>
    }

  }
}
