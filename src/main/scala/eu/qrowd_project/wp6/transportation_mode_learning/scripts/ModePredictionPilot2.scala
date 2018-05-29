package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.IOException
import java.nio.file.{Files, Paths}
import java.sql.Timestamp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import eu.qrowd_project.wp6.transportation_mode_learning.Predict
import eu.qrowd_project.wp6.transportation_mode_learning.util.{AccelerometerRecord, AutoReconnectingCassandraDBConnector, GeoJSONConverter, JSONExporter, LocationEventRecord, OutlierDetecting, ReverseGeoCoderOSM, SQLiteAccess2ndPilot, TrackPoint}
import scopt.Read

object ModePredictionPilot2 extends SQLiteAccess2ndPilot with OutlierDetecting with JSONExporter {
  val logger = com.typesafe.scalalogging.Logger("Mode detection")

  type UserID = String
  private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
  private val outputDir = tmpDir.resolve("ilog-questionaire")
  private lazy val rScriptPath = appConfig.getString("prediction_settings.r_script_path")

  private val appConfig = ConfigFactory.load()
  private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  private lazy val cassandra = new AutoReconnectingCassandraDBConnector

  private val tripDetector = new TripDetection()
  private val fallbackTripDetector = new WindowDistanceTripDetection(
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
  def runModeDetection(userID: UserID, trip: Trip, accelerometerData: Seq[AccelerometerRecord], tripIdx: Int, config: Config): Unit = {
    logger.info(s"Running mode detection for user $userID on trip " +
      s"${trip.start.timestamp.toLocalDateTime.toString} - " +
      s"${trip.end.timestamp.toLocalDateTime.toString}")

    val predicter = new Predict(rScriptPath,
      s"$rScriptPath/prediction_server.r",
      s"$rScriptPath/prediction_client.r",
      s"$rScriptPath/model.rds")

    predicter.debug = config.writeDebugOut

    val modesWProbAndTimeStamp: Seq[(String, Double, Timestamp)] =
      predicter.predict(trip, accelerometerData, userID, tripIdx)

    modesWProbAndTimeStamp.foreach(e => assert(e._3.after(trip.start.timestamp) && e._3.before(trip.end.timestamp)))

    // go on here
  }

  def run(config: Config): Unit = {
    logger.info(s"processing ILog data of date ${config.date} ...")

    // get the data for the given day, i.e. (user, GPS values)
    val data = cassandra.readData(
      config.date.format(formatter),
      appConfig.getInt("stop_detection.gps_accuracy"))

    // run the trip detection, i.e. find start and stop points
    val trips: Map[UserID, Seq[(UserID, Trip)]] = extractTrips(data, config).groupBy(_._1)

    val users: Set[UserID] = trips.keySet

    users.foreach( user => {
      val fullDayAccelerometerData =
        cassandra.getAccDataForUserAndDay(user, config.date.format(formatter))

      trips(user).zipWithIndex.foreach(
        userWithTripIdx => {
          val trip = userWithTripIdx._1._2
          val tripIdx: Int = userWithTripIdx._2
          // filter the accelerometer data to the time range of the considered trip
          val filteredAccelerometerData =
            filter(fullDayAccelerometerData, trip.start.timestamp, trip.end.timestamp)

          runModeDetection(user, trip, filteredAccelerometerData, tripIdx, config)
        }
      )
    })
  }

  private def filter(accelerometerData: Seq[AccelerometerRecord], start: Timestamp, stop: Timestamp): Seq[AccelerometerRecord] =
    accelerometerData.filter(e => e.timestamp.after(start) && e.timestamp.before(stop))

  private val today = LocalDate.now()
  case class Config(date: LocalDate = today,
                    writeDebugOut: Boolean = false)

  import scopt.Read.reads
  implicit val dateRead: Read[LocalDate] =
    reads(x => LocalDate.parse(x, DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)))

  private val parser = new scopt.OptionParser[Config]("IlogQuestionaireDataGenerator") {
    head("ModeDetection", "0.1.0")

    opt[LocalDate]('d', "date")
      //      .required()
      .valueName("<yyyyMMdd>")
      .action((x, c) =>
        c.copy(date = x)).text("Date to be processed (yyyyMMdd), e.g. 20180330 . " +
      "If no date is provided, we'll use the current date.")

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
          connect(appConfig.getString("sqlite_settings.db_file"))

          run(config)
        } catch {
          case e: IOException => logger.error("Cannot create output directories.", e)
          case t: Throwable => logger.error("Failed to process the ILog data.", t)
        } finally {
          // stop Cassandra here because when iterating we want to do it only once
          close()
          cassandra.close()
        }
      case _ =>
    }
  }
}
