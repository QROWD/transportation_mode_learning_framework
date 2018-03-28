package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.{Files, Path, Paths}
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Duration, LocalDateTime}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import com.google.common.base.Splitter
import eu.qrowd_project.wp6.transportation_mode_learning.preprocessing.TDBSCAN
import eu.qrowd_project.wp6.transportation_mode_learning.util.{HaversineDistance, Point, TrackPoint}
import io.eels.Row
import io.eels.component.parquet.ParquetSource
import javax.json.stream.JsonGenerator
import javax.json.{Json, JsonArray, JsonObject}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.functions._
//import org.apache.spark.sql.{Row, SparkSession}

/**
  *
  * Show movement per minute based on location data provided by ILog App.
  *
  * @author Lorenz Buehmann
  */
object LocationDataAnalyzer {

  var outputDir: Path = _

  var windowDir: Path = _
  var jsonDir: Path = _

  def main(args: Array[String]): Unit = {
//    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)
//    Logger.getLogger("parquet.hadoop").setLevel(Level.ERROR)
//    Logger.getLogger("org.eclipse.jetty").setLevel(Level.ERROR)
//    Logger.getLogger("org.apache.parquet.hadoop").setLevel(Level.ERROR)


    val input = args(0)
    outputDir = Paths.get(args(1))
    if(Files.notExists(outputDir)) {
      Files.createDirectories(outputDir)
    }

    // time in second
    val windowSizes: Seq[Int] = Splitter.on(",").omitEmptyStrings().trimResults().split(args(2)).asScala.map(_.toInt).toSeq
    val timestampFormat: Boolean = if (args.length == 4) args(3).toBoolean else true // weird timestamp format hack

    jsonDir = outputDir.resolve("geojson")
    if(Files.notExists(jsonDir)) {
      Files.createDirectories(jsonDir)
    }

    // load the data
    val entries = loadData(input, timestampFormat)

    // split by day
    val entriesPerDay = splitByDay(entries)
      .filter(_._2.nonEmpty)

//    // windows as TSV
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
//    windowSizes.foreach(windowSize => {
//      windowDir = outputDir.resolve(s"windows/${windowSize}")
//      if(Files.notExists(windowDir)) {
//        Files.createDirectories(windowDir)
//      }
//      visualizeWindowDistance(entries, windowSize, s"location_windows_${windowSize}s.tsv")
//      entriesPerDay.foreach(e => visualizeWindowDistance(e._2, windowSize, s"location_windows_${windowSize}s_${e._1.format(dateFormatter)}.tsv"))
//
//    })
//
//    // write to GeoJSON file
//    visualizeGeoData(entries, "location_points.json")
//    entriesPerDay.foreach(e => visualizeGeoData(e._2, s"location_points_${e._1.format(dateFormatter)}.json"))
//    entriesPerDay.foreach(e => visualizeGeoData(e._2, s"locations_line_${e._1.format(dateFormatter)}.json", linestring = true))

    // clustered GPS data per day
    val stopDetector = new StopDetection(0.5, 0.1, 40)
    entriesPerDay.foreach(e => {
      val points = e._2
//      val clusters = new GeoSpatialClustering(0.3, 5).cluster(points.asJava)
////      Files.newBufferedWriter(Paths.get("/tmp/clusters/"))
//      println(s"day ${e._1}:\n ${clusters.asScala.map(_.getPoints.asScala.mkString("||")).mkString("\n")}")

      val stops = stopDetector.find(points)
      visualizeGeoData(stops.flatten, s"stop_cluster_points_${e._1.format(dateFormatter)}.json")
      println(stops.zipWithIndex.mkString("\n"))

    })

    //    //
//    val entitiesbefore12 = entriesPerDay(15).filter(e => e._1.toLocalDateTime.getHour < 12)
//    println(toGeoJSONLineString(entitiesbefore12))
//    val json = toBarefootJSON(entitiesbefore12)
//    print(json)
//
//
//    val barefootService = new BarefootMapMatcherSocket(host = "127.0.0.1", port = 1234)
//    val response = barefootService.query(json.toString)
//    print(response)

  }

  private def loadData(path: String, timestampFormat: Boolean = true) = {
    // Eels API
    implicit val hadoopConfiguration = new Configuration()
    implicit val hadoopFileSystem = FileSystem.get(hadoopConfiguration) // This is required
    val source = ParquetSource(Paths.get(path)).withProjection("timestamp", "point")
    val data = source.toDataStream().collect

    // Spark API
//    val session = SparkSession.builder()
//      .master("local[4]")
//      .getOrCreate()
//
//    // read data
//    val df = session.read.parquet(path)
//      .select("timestamp", "point")
//      .sort(asc("timestamp"))
//    df.printSchema()
//    df.show(false)
//    //    df.sort(desc("timestamp")).show(false)
//    val data = df.collect()
//
//    session.stop()

    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    // map to (Timestamp, Point)
    val entries: Seq[TrackPoint] =
      data
        .map(row => {

          val timestamp = if (timestampFormat) row.getAs[Timestamp]("timestamp")
          else Timestamp.valueOf(
            LocalDateTime.parse(row.getAs[String]("timestamp").substring(0, 14),
              dateTimeFormatter))
//          val point = row.getAs[Row]("point")
//          val (lat, long) = (point.getAs[Double]("latitude"), point.getAs[Double]("longitude"))
          val point = row.getAs[mutable.WrappedArray[Double]]("point")
          val (lat, long) = (point(0), point(1))
          TrackPoint(lat, long, timestamp)
        })
        .toSet // de-duplicate
        .toSeq
        .sortWith((p1, p2) => p1.timestamp.before(p2.timestamp)) // sort by timestamp asc

    entries
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

  private def windows(entries: Seq[TrackPoint], windowSizeSeconds: Int): Seq[(Timestamp, Seq[TrackPoint])] = {
    var windows = ListBuffer[(Timestamp, Seq[TrackPoint])]()

    // reset to get every full minute
    var begin = entries.head.timestamp.toLocalDateTime.truncatedTo(ChronoUnit.MINUTES)
    var end = entries.last.timestamp.toLocalDateTime.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)

//    var begin: Timestamp = entries.head._1
//    val end: Timestamp = entries.last._1
//
//    println(s"Begin:$begin\nEnd:$end\nDuration:${begin.toLocalDateTime.until(end.toLocalDateTime, MINUTES)}min")
//    val nrOfWindows: Int = ((end.getTime - begin.getTime) / windowSize).toInt
//    println(s"#windows:$nrOfWindows")

    var currentBegin = begin

    while(currentBegin.isBefore(end)) {
      val nextEnd = currentBegin.plusSeconds(windowSizeSeconds)

      val currentEntries = entries.filter(e =>
        e.timestamp.toLocalDateTime.isAfter(currentBegin) &&
        e.timestamp.toLocalDateTime.isBefore(nextEnd))

      windows :+= (Timestamp.valueOf(currentBegin), currentEntries)

      currentBegin = nextEnd
    }
    windows
  }


  val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  def visualizeWindowDistance(entries: Seq[TrackPoint], windowSize: Int, path: String): Unit = {
    val points = windows(entries, windowSize)
      .filter(_._2.nonEmpty)
      .map(window => {

        val start = window._1
        val values = window._2


        val begin = values.head
        val end = values.last

        val distanceLat = end.lat - begin.lat
        val distanceLong = end.long - begin.long
        val norm = Vectors.norm(Vectors.dense(distanceLat, distanceLong), 2)

        val distance = HaversineDistance.compute(begin, end)

        val distanceSum = haversineDistance(values)

        val speed = avgSpeed(values)

        (start.toLocalDateTime.format(timeFormatter), values.size, begin.toString(), end.toString(), norm, distance, distanceSum, speed)
      })

    val writer = new BufferedWriter(new FileWriter(windowDir.resolve(path).toFile))
    val separator = "\t"
    val header = Seq("window_start", "#entries", "first_entry", "last_entry", "l2-norm", "distance_start_end(km)", "distance_sum(km)", "avg. km/h").mkString(separator) + "\n"
    writer.write(header)
    points.map(tuple => tuple.productIterator.mkString(separator) + "\n").foreach(writer.write)
    writer.close()
  }

  def visualizeGeoData(entries: Seq[TrackPoint], path: String, linestring: Boolean = false): Unit = {
    val config = Map(JsonGenerator.PRETTY_PRINTING -> true)
    val factory = Json.createWriterFactory(config.asJava)

    val json = if(linestring) {
      toGeoJSONLineString(entries)
    } else {
      toGeoJSON(entries)
    }

    val jsonWriter = factory.createWriter(new BufferedWriter(new FileWriter(jsonDir.resolve(path).toString)))
    jsonWriter.writeObject(json)
    jsonWriter.close()
  }

  private def toBarefootJSON(entries: Seq[TrackPoint]): JsonArray = {
    val features = Json.createArrayBuilder()

    val concisePoints = entries.head :: entries.sliding(2).collect { case Seq(a,b) if a != b => b }.toList

    concisePoints.zipWithIndex.foreach{
      case(p, i) =>
        features.add(i, Json.createObjectBuilder()
          .add("id", i)
          .add("time", p.timestamp.getTime)
          .add("point", s"POINT (${p.long} ${p.lat})"))
    }

    features.build()
  }

  private def toGeoJSON(entries: Seq[TrackPoint]): JsonObject = {
    val features = Json.createArrayBuilder()

    val concisePoints = entries.head :: entries.sliding(2).collect { case Seq(a,b) if a != b => b }.toList

    concisePoints.zipWithIndex.foreach{
      case(p, i) =>
        features.add(i, Json.createObjectBuilder()
          .add("type", "Feature")
          .add("geometry", Json.createObjectBuilder()
            .add("type", "Point")
            .add("coordinates", Json.createArrayBuilder(Seq(p.long, p.lat).asJava)))
          .add("properties", Json.createObjectBuilder().add("timestamp", p.toString))
        )
    }

    Json.createObjectBuilder()
      .add("type", "FeatureCollection")
      .add("features", features)
      .build()
  }

  private def toGeoJSONLineString(entries: Seq[TrackPoint]): JsonObject = {
    val concisePoints = entries.head :: entries.sliding(2).collect { case Seq(a,b) if a != b => b }.toList

    val coordinates = Json.createArrayBuilder()
    concisePoints.zipWithIndex.foreach{
      case(p, i) => coordinates.add(i, Json.createArrayBuilder(Seq(p.long, p.lat).asJava))
    }

    Json.createObjectBuilder()
        .add("type", "Feature")
        .add("geometry", Json.createObjectBuilder()
          .add("type", "LineString")
          .add("coordinates", coordinates)
        )
        .add("properties", Json.createObjectBuilder().add("timestamp", entries.head.timestamp.toString))
      .build()
  }
  val EARTH_RADIUS = 6372.8  //radius in km


  private def haversineDistance[P <: Point](points: Seq[P]): Double =
    points.sliding(2).collect { case Seq(a,b) => HaversineDistance.compute(a, b)}.sum

  /**
    * Time difference in seconds
    */
  private def timeDiff(begin: TrackPoint, end: TrackPoint): Double =
    Duration.between(begin.timestamp.toLocalDateTime, end.timestamp.toLocalDateTime).getSeconds

  private def avgSpeed(entries: Seq[TrackPoint]) = {
    val distanceInMeters = haversineDistance(entries) * 1000
    val diffSeconds = timeDiff(entries.head, entries.last)

    val metersPerSecond = if (diffSeconds == 0) 0
                          else distanceInMeters.toDouble / diffSeconds

    metersPerSecond * 3.6
  }



}
