package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, LocalDateTime}
import java.util.Locale

import eu.qrowd_project.wp6.transportation_mode_learning.util._
import javax.json.Json

/**
  * The main entry class for processing the ILog data of the given day.
  *
  * @author Lorenz Buehmann
  */
object IlogQuestionaireDataGenerator extends JSONExporter with ParquetLocationEventRecordLoader {

  val logger = com.typesafe.scalalogging.Logger("ILog Questionaire Data Generator")

  val poiRetrieval = POIRetrieval("http://linkedgeodata.org/sparql")
  val tripDetector = new TripDetection()

  def main(args: Array[String]): Unit = {

    parser.parse(args, Config()) match {
      case Some(config) =>

        try {
          new File(config.out.getParent).mkdirs()
          // iterate over dates, for debugging - TODO remove
          val formatter = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ENGLISH)
          var startDate = java.time.LocalDate.parse("20180301", formatter)
          val endDate = java.time.LocalDate.parse("20180331", formatter)
          while (startDate.isBefore(endDate)) {
            run(config.copy(
              date = startDate.format(formatter),
              out = new File(config.out.getParent + startDate.format(formatter))))
            startDate = startDate.plusDays(1)
          }
        } catch {
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
  private lazy val cassandra = CassandraDBConnector(
    // restriction of users TODO remove
    Seq("ecfb0929250fb6dda66a4065441230ab27f094e5", "d429974540bfd38c3367fe9f0c8682775ff4fa18")
  )

  private def run(config: Config) = {
    //    val inputPath = args(0)
    //    val data = loadDataFromDisk(inputPath, date)

    logger.info(s"processing ILog data of ${config.date} ...")

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

        // get possible POIs at start and end of trip
        trips.map(trip => {
          val poisStart = poiRetrieval.getPOIsAt(trip._1, 0.1)
          val poisEnd = poiRetrieval.getPOIsAt(trip._2, 0.1)

          (userId, trip._1, poisStart.head, trip._2, poisEnd.head)
        })
      case other =>
        println(other)
        None
    }

    // write to JSON
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
    write(json.build(), outputPath)
  }

  private def splitByDay(entries: Seq[TrackPoint]): Seq[(LocalDateTime, Seq[TrackPoint])] = {
    var list = Seq[(LocalDateTime, Seq[TrackPoint])]()

    val firstDay = entries.head.timestamp.toLocalDateTime.truncatedTo(ChronoUnit.DAYS)
    val lastDay = entries.last.timestamp.toLocalDateTime.plusDays(1).truncatedTo(ChronoUnit.DAYS)
    println(s"First day: $firstDay")
    println(s"Last day: $lastDay")

    var currentDay = firstDay
    while (currentDay.isBefore(lastDay)) {
      val nextDay = currentDay.plusDays(1)

      val currentEntries = entries.filter(e => {
        e.timestamp.toLocalDateTime.isAfter(currentDay) && e.timestamp.toLocalDateTime.isBefore(nextDay)
      })
      println(s"$currentDay  --  $nextDay: ${currentEntries.size} entries")

      list :+= (currentDay, currentEntries)

      currentDay = nextDay
    }
    //    (list.indices zip list).toMap
    list
  }

  val today = LocalDate.now().format(formatter)

  case class Config(date: String = today,
                    out: File = new File(System.getProperty("java.io.tmpdir")
                      + File.separator + "ilog-questionaire" + File.separator + today + ".json"))

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

  }

}
