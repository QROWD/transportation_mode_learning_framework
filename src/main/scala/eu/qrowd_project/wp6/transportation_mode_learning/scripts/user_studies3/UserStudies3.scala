package eu.qrowd_project.wp6.transportation_mode_learning.scripts.user_studies3

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDate}
import java.util.Locale
import java.util.concurrent.TimeUnit

import scala.collection.immutable
import com.typesafe.config.ConfigFactory
import eu.qrowd_project.wp6.transportation_mode_learning.Predict
import eu.qrowd_project.wp6.transportation_mode_learning.mapmatching.{GraphhopperMapMatcherHttp, GraphhopperPublicTransitMapMatcherHttp}
import eu.qrowd_project.wp6.transportation_mode_learning.scripts.{ClusterTrip, Trip, TripDetection, WindowDistanceBasedTripDetection}
import eu.qrowd_project.wp6.transportation_mode_learning.util._
import scopt.Read

import scala.collection.JavaConverters._
import scala.util.Try

object UserStudies3
  extends SQLiteAccessUserStudies3
    with OutlierDetecting
    with JSONExporter
    with ReverseGeoCodingTomTom {

  val logger = com.typesafe.scalalogging.Logger("UserStudies3")

  type UserID = String
  private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
  private val outputDir = tmpDir.resolve("ilog-questionaire")
  private lazy val rScriptPath = appConfig.getString("prediction_settings.r_script_path")

  private val appConfig = ConfigFactory.load()
  private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  private lazy val cassandra = new CassandraDBConnector

  private lazy val predicter = new Predict(rScriptPath,
//    s"$rScriptPath/prediction_server.r",
    s"$rScriptPath/run.r",
    s"$rScriptPath/model.rds")
//    .withServerMode()

  private val tripDetector = new TripDetection()
  private val fallbackTripDetector = new WindowDistanceBasedTripDetection(
    windowSize = appConfig.getInt("stop_detection.window_distance.window_size"),
    stepSize = appConfig.getInt("stop_detection.window_distance.step_size"),
    distanceThresholdInKm = appConfig.getDouble("stop_detection.window_distance.distance"),
    minNrOfSegments = appConfig.getInt("stop_detection.window_distance.min_nr_of_segments"),
    noiseSegments = appConfig.getInt("stop_detection.window_distance.noise_segments")
  )

  private lazy val reverseGeoCoder = ReverseGeoCoderOSM(
    appConfig.getString("address_retrieval.reverse_geo_coding.base_url"),
    appConfig.getLong("address_retrieval.reverse_geo_coding.delay"),
    TimeUnit.SECONDS
  )

  /** FIXME: copied over from IlogQuestionnaireGenerator; move to a meaningful place */
  private def toGeoJson(trip: Trip) = {
    val pointsJson = GeoJSONConverter.merge(
      GeoJSONConverter.toGeoJSONPoints(Seq(trip.start), Map("marker-symbol" -> "s")),
      GeoJSONConverter.toGeoJSONPoints(Seq(trip.end), Map("marker-symbol" -> "e")))
    val tripJson = GeoJSONConverter.toGeoJSONLineString(Seq(trip.start) ++ trip.trace ++ Seq(trip.end))
    GeoJSONConverter.merge(pointsJson, tripJson)
  }

  def extractTrips(data: Seq[(String, Seq[LocationEventRecord])], config: Config): Seq[(String, Trip)] = {
    data.flatMap {
      case (userId: String, entries: Seq[LocationEventRecord]) =>
        logger.info(s"processing user $userId ...")

        // extract GPS data
        var trajectory = entries.map(e => TrackPoint(e.latitude, e.longitude, e.timestamp))
          .distinct
        logger.info(s"trajectory size:${trajectory.size}")

        // find outliers
        val outliers = detectOutliers(trajectory)
        logger.info(s"outliers:${outliers.mkString(",")}")
        trajectory = trajectory.filter(!outliers.contains(_))
        logger.info(s"trajectory size (after outlier removal):${trajectory.size}")

        // TODO Patrick: apply trip detection depending on user preferences
        // find trips (start, end, trace)
        var trips: Seq[Trip] = tripDetector.find(trajectory)
        logger.info(s"#trips: ${trips.size}")
        logger.info(s"trip details: ${trips.zipWithIndex.map {
          case (trip, idx) => s"trip$idx ${trip.start} - ${trip.end} : ${trip.trace.size} points"}.mkString("\n")}")

        if (trips.isEmpty) {
          logger.info("trying fallback algorithm...")
          trips = fallbackTripDetector.find(trajectory)
        }

        // debug output if enabled
        if (config.writeDebugOut) {
          val dir = outputDir.resolve(s"debug/${config.date.format(formatter)}/$userId/")
          dir.toFile.mkdirs()

          // write trajectory as GeoJSON
          if (trajectory.nonEmpty) {
            val lines = GeoJSONConverter.toGeoJSONLineString(trajectory)
            val points = GeoJSONConverter.toGeoJSONPoints(trajectory)
            val linesWithPoints = GeoJSONConverter.merge(lines, points)
            write(lines, dir.resolve("lines.json").toString)
            write(points, dir.resolve("points.json").toString)
            write(linesWithPoints, dir.resolve("lines_with_points.json").toString)
          }

          // write trips as GeoJSON
          trips
            .zipWithIndex
            .foreach { case (trip: Trip, index: Int) =>
              write(toGeoJson(trip), dir.resolve(s"trip_$index.json").toString)

              trip match {
                case t: ClusterTrip =>
                  // with cluster points
                  write(
                    GeoJSONConverter.merge(
                      GeoJSONConverter.merge(
                        GeoJSONConverter.toGeoJSONPoints(
                          t.startCluster,
                          Map("marker-symbol" -> "s", "marker-color" -> "#00cd00")),
                        GeoJSONConverter.toGeoJSONPoints(
                          t.endCluster,
                          Map("marker-symbol" -> "e", "marker-color" -> "#ee0000"))
                      ),
                      GeoJSONConverter.toGeoJSONLineString(Seq(t.start) ++ t.trace ++ Seq(t.end))
                    ),
                    dir.resolve(s"trip_${index}_with_clusters.json").toString)
                case _ =>
              }
            }
        }

        trips.map(trip => (userId, trip))
      case other =>
        println(other)
        None
    }
  }

  /**
    * Runs the mode prediction for the given user and trip.
    * The steps to perform are:
    * 1) Taking the accelerometer data and run the prediction on it (which
    *    includes cleaning and smoothing)
    * 2) Map the points where the predicted transportation mode changes back to
    *    a GPS coordinate (based on the timestamp)
    * 3) Compile a new Pilot2Stage object
    *    - Find start/stop points based on the GPS coordinates of the mode
    *      change points
    *    - Add all points in between
    *    - Get the address of start and stop point through reverse geo-coding
    * 4) Save the Pilot2Stage object to the SQLite database
    * 5) Write back meaningful debug files if not done already
    *
    * Sub-tasks still to implement:
    * 2), 3), 4), 5)
    */
  def runModeDetection(userID: UserID,
                       trip: Trip,
                       accelerometerData: Seq[AccelerometerRecord],
                       tripIdx: Int,
                       config: Config): immutable.Seq[UserStudies3Stage] = {
    logger.info(s"Running mode detection for user $userID on trip $tripIdx " +
      s"${trip.start.timestamp.toLocalDateTime.toString} - ${trip.end.timestamp.toLocalDateTime.toString}")

    predicter.debug = config.writeDebugOut

    val modesWProbAndTimeStamp: Seq[(String, Double, Timestamp)] = predicter.predict(trip, accelerometerData, userID, tripIdx)

    modesWProbAndTimeStamp.foreach(e => assert(e._3.after(trip.start.timestamp) && e._3.before(trip.end.timestamp)))

    // step 2
    // compute transition points
    val transitionPointsWithMode: List[(TrackPoint, String)] = computeTransitionPoints(trip, modesWProbAndTimeStamp.toList)
    Files.write(Paths.get(s"/tmp/${userID}_transition_points_trip$tripIdx.out"), transitionPointsWithMode.mkString("\n").getBytes(Charset.forName("UTF-8")))

    (transitionPointsWithMode ++ Seq((trip.end, "dummy"))) // add a dummy element to the end to process last element
      .sliding(2)
      .zipWithIndex
      .map(pointsWModeWithIdx => {
        val pointsWMode = pointsWModeWithIdx._1
        val startPoint: TrackPoint = pointsWMode(0)._1

        val stopPoint = if (pointsWMode.size > 1) {
          pointsWMode(1)._1
        } else {
          // just one transition point means just one mode was used for the whole trip
          // or we're in the last stage, thus we take the end of the whole trip as stop point
          trip.end
        }
        val mode: String = pointsWMode(0)._2
        buildStage(s"$tripIdx.${pointsWModeWithIdx._2}", userID, startPoint, stopPoint, mode, trip)
      })
      .toList
  }

  private def buildStage(stageID: String,
                         userID: UserID,
                         start: TrackPoint,
                         stop: TrackPoint,
                         mode: String,
                         overallTrip: Trip): UserStudies3Stage = {
    // extract the trackpoints from the trip
    var points: Seq[TrackPoint] = overallTrip.trace.filter(point => point.timestamp.after(start.timestamp) && point.timestamp.before(stop.timestamp))

    // add start and end
    points = Seq(start) ++ points ++ Seq(stop)

    // find addresses
    val startAddress = getStreet(start.long, start.lat)
    val stopAddress = getStreet(stop.long, start.lat)

    UserStudies3Stage(stageID, userID, mode, start, startAddress, stop, stopAddress, trajectory = points)
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

        //        println(s"start point:$tp1")
        //        println(s"bearing:$bearing")
        //        println(s"distance(km):$distanceTotalKm")
        //        println(modesBetween.mkString("\n"))

        // the mode after the last point TODO
        //        val nextMode = compressedModes.filter(_._3.after(end)).head


        if(modesBetween.isEmpty) { // handle no mode change between both points
          //          println("no mode")
          // this happens due to compression, just take the last known mode
          // we might not have seen any mode before because
          // i) it might be the first point at all or
          // ii) the first in the trip split
          // -> we take the first mode
          val modesBefore = compressedModes.filter(e => e._3.before(tp1.timestamp))
          val mode = if(modesBefore.nonEmpty) modesBefore.last else compressedModes.head
          Seq((tp1, tp2, mode._1))
        } else if(modesBetween.size == 1) { // handle single mode change between both points
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
          val first = Seq((tp1, intermediatePointsWithMode.head._1, intermediatePointsWithMode.head._2))
          val mid =  (intermediatePointsWithMode zip intermediatePointsWithMode.tail).map{
            case ((p1, mode1), (p2, mode2)) =>
              (p1, p2, mode2)
          }
          val last = Seq((intermediatePointsWithMode.last._1, tp2, intermediatePointsWithMode.last._2))

          first ++ mid ++ last
        }
    }

    // keep only the transition points
    val f2 = (e1: (TrackPoint, TrackPoint, String), e2: (TrackPoint, TrackPoint, String)) => e1._3 == e2._3
    val startEndWithModeCompressed = compress(startEndWithMode.toList, f2)

    startEndWithModeCompressed.map {
      case (t1, t2, mode) => (t1, mode) // drop second trackpoint
    }
  }



  def run(config: Config): Unit = {
    logger.info(s"processing ILog data of date ${config.date} ...")

    // filter by user ID if given for debugging
    if(config.userID != null) {
      cassandra.userIds = Seq(config.userID)
//        data = data.filter(_._1 == config.userID)
    }


    // get the data for the given day, i.e. (user, GPS values)
    var data = cassandra.readData(
      config.date.format(formatter),
      appConfig.getInt("stop_detection.gps_accuracy"))


    // run the trip detection, i.e. find start and stop points
    val trips: Map[UserID, Seq[(UserID, Trip)]] = extractTrips(data, config).groupBy(_._1)

    val users: Set[UserID] = trips.keySet

    // perform mode detection for each user
    users.foreach( user => {
      logger.info(s"user $user")
      // get the accelerometer data from Cassandra DB
      val fullDayAccelerometerData = cassandra.getAccDataForUserAndDay(user, config.date.format(formatter))
      logger.info(s"total accelerometer data = ${fullDayAccelerometerData.size}")

      // for each trip
      trips(user).zipWithIndex.foreach(
        userWithTripIdx => {
          logger.info(s"### trip ${userWithTripIdx._2} ###")
          val trip = userWithTripIdx._1._2
          val tripIdx: Int = userWithTripIdx._2

          // extract the accelerometer data in the time range of the current trip
          val filteredAccelerometerData = filter(fullDayAccelerometerData, trip.start.timestamp, trip.end.timestamp)
          logger.info(s"#accelerometer data = ${filteredAccelerometerData.size}")

          activitySegmentation(filteredAccelerometerData)

          // run the mode detection
          val stages = runModeDetection(user, trip, filteredAccelerometerData, tripIdx, config)

          // perform the map matching
          stages.zipWithIndex.foreach { stageWithIdx =>
            println(s"##### stage  ${stageWithIdx._1.toString} ######")
            val stage = stageWithIdx._1
            val mappedTrajectory = mapMatching(stage.trajectory, stage.mode)

            stage.mappedTrajectory = mappedTrajectory;
          }

          // write to DB
          stages.foreach(writeTripInfo)
        }
      )
    })
  }

  import java.lang.Math.{pow, sqrt}
  // sqrt(x^2 + y^2 + z^2)
  val euclidianLength = (rec: AccelerometerRecord) => {sqrt(pow(rec.x, 2) + pow(rec.y, 2) + pow(rec.z, 2))}
  val euclidianLengthOfSeq = (records: Seq[AccelerometerRecord]) => {records.map(euclidianLength).sum / records.size}

  private def activitySegmentation(accelerometerData: Seq[AccelerometerRecord]) = {
    var t1 = accelerometerData.head.timestamp

    // check the avg. acceleration for a period of t = 5s
    val windows = accelerometerData.sliding(50)
    val windowsWithAvgAcceleration = windows.map(w => (w, euclidianLengthOfSeq(w)))

    windowsWithAvgAcceleration.foreach {
      case (w, value) => println(s"${w.head.timestamp} - ${w.last.timestamp}: $value")
    }

  }

  val mapMatcherURL = appConfig.getString("map_matching.graphhopper.map_matching_url")
  val mapMatcherPublicTransitURL = appConfig.getString("map_matching.graphhopper.routing_url")
  val mapMatcher = new GraphhopperMapMatcherHttp(mapMatcherURL)
  val mapMatcherPublicTransit = new GraphhopperPublicTransitMapMatcherHttp(mapMatcherPublicTransitURL)

  /**
    * perform the map matching here
    *
    * we distinguish between public transit (bus, train) and others
    *
    */
  private def mapMatching(trajectory: Seq[TrackPoint], mode: String): Seq[TrackPoint] = {

    // do map matching in case of "train" via routing API of GraphHopper - might fail ... //TODO fallback?
    val matchedTrip =
      if(mode == "train") {
      mapMatcherPublicTransit.query(trajectory, Some(mode))
    } else {
      mapMatcher.query(trajectory)
    }

    // convert GPX to trajectory
    if (matchedTrip.nonEmpty) {
      // the conversion will fail if there is no matched route in the GPX, in that case we simply return
      // the trajectory itself
      Try(GPXConverter.fromGPX(matchedTrip.get)) getOrElse(trajectory)
    } else {
      trajectory
    }
  }

  private def compress[A](l: List[A], fn: (A, A) => Boolean):List[A] = l.foldLeft(List[A]()) {
    case (List(), e) => List(e)
    case (ls, e) if fn(ls.last, e) => ls
    case (ls, e) => ls:::List(e)
  }

  private def filter(accelerometerData: Seq[AccelerometerRecord], start: Timestamp, stop: Timestamp): Seq[AccelerometerRecord] =
    accelerometerData.filter(e => e.timestamp.after(start) && e.timestamp.before(stop))

  private val today = LocalDate.now()
  case class Config(date: LocalDate = today,
                    writeDebugOut: Boolean = false,
                    userID: String = null)

  import scopt.Read.reads
  implicit val dateRead: Read[LocalDate] =
    reads(x => LocalDate.parse(x, DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)))

  private val parser = new scopt.OptionParser[Config]("UserStudies3") {
    head("UserStudies3", "0.1.0")

    opt[LocalDate]('d', "date")
      //      .required()
      .valueName("<yyyyMMdd>")
      .action((x, c) =>
        c.copy(date = x)).text("Date to be processed (yyyyMMdd), e.g. 20180330 . " +
      "If no date is provided, we'll use the current date.")

    opt[String]('u', "user")
      //      .required()
      .valueName("userID")
      .action((x, c) =>
        c.copy(userID = x)).text("User ID used for debugging only a single user.")

    opt[Unit]("debug")
      .optional()
      //      .hidden()
      .action((x, c) =>
      c.copy(writeDebugOut = true)).text("If enabled, debug information such as GeoJson documents of trips will be written " +
      "to disk. The path of the output is /SYSTEM_TEMP_FOLDER/ilog-questionaire/debug/")

    help("help").text("prints this usage text")
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Config()) match {
      case Some(config) =>
        try {
          // create the output dirs
          Files.createDirectories(outputDir)
          connect(appConfig.getString("sqlite_settings.mode_detection_db_file"))

          run(config)
        } catch {
          case e: IOException => logger.error("Cannot create output directories.", e)
          case t: Throwable => logger.error("Failed to process the ILog data.", t)
        } finally {
          println("finished pilot run")
          // stop Cassandra here because when iterating we want to do it only once
          close()
          cassandra.close()
          predicter.rClient.stop()
        }
      case _ =>
    }
  }
}
