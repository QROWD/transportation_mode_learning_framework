package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.nio.file.Paths
import java.time.{LocalDate, LocalDateTime}
import java.time.temporal.ChronoUnit

import eu.qrowd_project.wp6.transportation_mode_learning.util._
import io.eels.component.parquet.ParquetSource
import javax.json.Json
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem

/**
  * @author Lorenz Buehmann
  */
object IlogQuestionaireDataGenerator extends JSONExporter {

  val poiRetrieval = POIRetrieval("http://linkedgeodata.org/sparql")
  val tripDetector = new TripDetection()

  def main(args: Array[String]): Unit = {

    val inputPath = args(0)

    val date = "20171204"
    val outputPath = "/tmp/questionaire_20180330.json"

    // connect to Trento Cassandra DB
//    val cassandra = CassandraDBConnector()

    // get the data for the given day, i.e. (user, entries)
//    val data = cassandra.readData(date)
    val data = loadDataFromDisk(inputPath, date)

    // detect trips per each user
    val result = data.flatMap {
      case (userId: String, entries: Seq[LocationEventRecord]) =>
        // extract GPS data
        val trajectory = entries.map(e => TrackPoint(e.latitude, e.longitude, e.timestamp))
        println(s"trajectory size:${trajectory.size}")

        // find trips (start, end, trace)
        val trips = tripDetector.find(trajectory)
        println(s"#trips: ${trips.size}")

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

//    cassandra.close()


  }

  import java.time.format.DateTimeFormatter
  val  formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
  private def loadDataFromDisk(path: String, dateStr: String): Seq[(String, Seq[LocationEventRecord])] = {

    val date = LocalDate.parse(dateStr, formatter)
    println(date)
    // Eels API
    implicit val hadoopConfiguration = new Configuration()
    implicit val hadoopFileSystem = FileSystem.get(hadoopConfiguration) // This is required
    val source = ParquetSource(Paths.get(path))
    println(source.schema)
    println(source.toDataStream().take(10).collect.mkString("\n"))

    val records = source
      //      .withProjection("timestamp", "point")
      .toDataStream()
      .collect
      .map(row => LocationEventRecord.from(row))
      // choose a single day
      .filter(r => r.timestamp.toLocalDateTime.toLocalDate.equals(date))

    Seq(("dummyUserId", records))

  }

  private def splitByDay(entries: Seq[TrackPoint]): Seq[(LocalDateTime, Seq[TrackPoint])] = {
    var list = Seq[(LocalDateTime, Seq[TrackPoint])]()

    val firstDay = entries.head.timestamp.toLocalDateTime.truncatedTo(ChronoUnit.DAYS)
    val lastDay = entries.last.timestamp.toLocalDateTime.plusDays(1).truncatedTo(ChronoUnit.DAYS)
    println(s"First day: $firstDay")
    println(s"Last day: $lastDay")

    var currentDay = firstDay
    while(currentDay.isBefore(lastDay)) {
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

  }
