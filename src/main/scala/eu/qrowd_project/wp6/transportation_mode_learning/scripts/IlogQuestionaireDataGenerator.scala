package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

import com.typesafe.config.ConfigFactory
import eu.qrowd_project.wp6.transportation_mode_learning.util._
import javax.json.Json

/**
  * The main entry class for processing the ILog data of the given day.
  *
  * @author Lorenz Buehmann
  */
object IlogQuestionaireDataGenerator extends JSONExporter with ParquetLocationEventRecordLoader {

  val logger = com.typesafe.scalalogging.Logger("ILog Questionaire Data Generator")

  private val tripDetector = new TripDetection()

  private val appConfig = ConfigFactory.load()
  private val poiRetrieval = POIRetrieval(appConfig.getString("poi_retrieval.endpoint_url"))

  def main(args: Array[String]): Unit = {

    parser.parse(args, Config()) match {
      case Some(config) =>

        try {
          // create the output dirs
          Files.createDirectories(outputDir)

          if (config.testMode) {

            users = trentoTestUsers
            // iterate over dates, for debugging - TODO remove
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
            var startDate = java.time.LocalDate.parse("20180301", formatter)
            val endDate = java.time.LocalDate.parse("20180331", formatter)
            logger.warn(s"Test mode enabled. Processing all data between ${startDate.format(formatter)} and ${endDate.format(formatter)}!")
            while (startDate.isBefore(endDate)) {
              run(config.copy(
                date = startDate.format(formatter),
                out = new File(config.out.getParent + File.separator + startDate.format(formatter) + ".json")))
              startDate = startDate.plusDays(1)
            }
          } else {
            run(config)
          }
        } catch {
          case e: IOException => logger.error("Cannot create output directories.", e)
          case t: Throwable => logger.error("Failed to process the ILog data.", t)
        } finally {
          // stop Cassandra here because when iterating we want to do it only once
          cassandra.close()
        }
      case None =>
      // arguments are bad, error message will have been displayed
    }

  }


  private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  // connect to Trento Cassandra DB
  private val trentoTestUsers = Seq("ecfb0929250fb6dda66a4065441230ab27f094e5", "d429974540bfd38c3367fe9f0c8682775ff4fa18")
  private var users: Seq[String] = Seq()
  private lazy val cassandra = CassandraDBConnector(users)

  private def run(config: Config): Unit = {
    //    val inputPath = args(0)
    //    val data = loadDataFromDisk(inputPath, date)

    logger.info(s"processing ILog data of date ${config.date} ...")

    val date = config.date
    val outputPath = config.out.getAbsolutePath

    // get the data for the given day, i.e. (user, entries)
    val data = cassandra.readData(date)

    // detect trips per each user
    val result = data.flatMap {
      case (userId: String, entries: Seq[LocationEventRecord]) =>
        logger.info(s"processing user $userId ...")

        // extract GPS data
        val trajectory = entries.map(e => TrackPoint(e.latitude, e.longitude, e.timestamp))
        logger.info(s"trajectory size:${trajectory.size}")

        // find trips (start, end, trace)
        val trips = tripDetector.find(trajectory)
        logger.info(s"#trips: ${trips.size}")

        // debug output if enabled
        if (config.writeDebugOut) {
          val dir = outputDir.resolve(s"debug/$date/$userId/")
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

              // with cluster points
              write(
                GeoJSONConverter.merge(
                  GeoJSONConverter.merge(
                    GeoJSONConverter.toGeoJSONPoints(trip.startCluster),
                    GeoJSONConverter.toGeoJSONPoints(trip.endCluster)
                  ),
                  GeoJSONConverter.toGeoJSONLineString(Seq(trip.start) ++ trip.trace ++ Seq(trip.end))
                ),
                dir.resolve(s"trip_${index}_with_clusters.json").toString)

            }
        }

        // get possible POIs at start and end of trip
        trips.map(trip => {
          val radius = appConfig.getDouble("poi_retrieval.distance_radius")
          val allPOIsStart = poiRetrieval.getPOIsAt(trip.start, radius)
          val allPOIsEnd = poiRetrieval.getPOIsAt(trip.end, radius)

          val poiStart = allPOIsStart.headOption.getOrElse(UNKNOWN_POI)
          val poiEnd = allPOIsEnd.headOption.getOrElse(UNKNOWN_POI)

          (userId, trip.start, poiStart, trip.end, poiEnd)
        })
      case other =>
        println(other)
        None
    }

    // write as JSON to disk
    write(toJson(result), outputPath)
  }

  private def toGeoJson(trip: Trip) = {
    val pointsJson = GeoJSONConverter.merge(
      GeoJSONConverter.toGeoJSONPoints(Seq(trip.start), Map("marker-symbol" -> "s")),
      GeoJSONConverter.toGeoJSONPoints(Seq(trip.end), Map("marker-symbol" -> "e")))
    val tripJson = GeoJSONConverter.toGeoJSONLineString(Seq(trip.start) ++ trip.trace ++ Seq(trip.end))
    GeoJSONConverter.merge(pointsJson, tripJson)
  }

  private val UNKNOWN_POI = POI("", "UNKNOWN", "", "")

  private def toJson(result: Seq[(String, TrackPoint, POI, TrackPoint, POI)]) = {
    val json = Json.createArrayBuilder()
    result.foreach {
      case (userId: String, start: TrackPoint, startPOI: POI, end: TrackPoint, endPOI: POI) =>
        val points = Json.createArrayBuilder()
          .add(Json.createObjectBuilder()
            .add("point", Json.createArrayBuilder().add(start.long).add(start.lat))
            .add("address", startPOI.label)
            .add("datetime", start.timestamp.toString)
          )
          .add(Json.createObjectBuilder()
            .add("point", Json.createArrayBuilder().add(end.long).add(end.lat))
            .add("address", endPOI.label)
            .add("datetime", end.timestamp.toString)
          )
        json.add(Json.createObjectBuilder()
          .add("uuid", userId)
          .add("points", points))

    }
    json.build()
  }


  private val today = LocalDate.now().format(formatter)
  private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))
  private val outputDir = tmpDir.resolve("ilog-questionaire")

  case class Config(date: String = today,
                    out: File = outputDir.resolve(today + ".json").toFile,
                    writeDebugOut: Boolean = false,
                    testMode: Boolean = false)

  private val parser = new scopt.OptionParser[Config]("IlogQuestionaireDataGenerator") {
    head("IlogQuestionaireDataGenerator", "0.1.0")

    opt[File]('o', "out")
      //      .required()
      .valueName("<file>").
      action((x, c) => c.copy(out = x)).
      text("Path to output JSON file containing the start and end point of each trip." +
        "If no path is provided, data will be written to /SYSTEM_TEMP_FOLDER/ilog-questionaire/{$date}.json")

    opt[String]('d', "date")
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

    opt[Unit]("test")
      .optional()
      //      .hidden()
      .action((x, c) =>
      c.copy(testMode = true)).text("test mode which iterates over all days of March 2018 with two fixed users.")

    help("help").text("prints this usage text")

  }

}
