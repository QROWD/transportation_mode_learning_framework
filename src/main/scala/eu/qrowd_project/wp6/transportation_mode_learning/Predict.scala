package eu.qrowd_project.wp6.transportation_mode_learning

import java.awt.Color
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}
import java.util.concurrent.TimeUnit

import scala.collection.JavaConverters._
import eu.qrowd_project.wp6.transportation_mode_learning.prediction._
import eu.qrowd_project.wp6.transportation_mode_learning.scripts._
import eu.qrowd_project.wp6.transportation_mode_learning.util._
import eu.qrowd_project.wp6.transportation_mode_learning.util.window.{TimeWindow, Window}


/**
  * 1. split raw data into trips
  * 2. predict mode probabilities per trip
  *
  * We're using an external R-script for mode prediction, i.e. we're
  *
  *  - writing data as CSV to disk
  *  - running the script
  *  - reading the generated output CSV from disk
  *
  * 3. determine mode transitions
  *
  * @param baseDir base directory of the R project
  * @param scriptPath path to the R script
  * @param modelPath path to the R model
  *
  * @author Lorenz Buehmann
  */
class Predict(baseDir: String, scriptPath: String, modelPath: String) {

  type Mode = String
  val logger = com.typesafe.scalalogging.Logger("Predict")

  lazy val rClient = {
    if(serverMode)
      new RClientServer(baseDir, scriptPath, modelPath)
    else
      new RClient(baseDir, scriptPath, modelPath)
  }

  val colors = Seq("red", "green", "blue", "yellow", "olive", "purple")

  val colorMapping = Map(
    "bike" -> "red",
    "bus" -> "green",
    "car" -> "blue",
    "still" -> "yellow",
    "train" -> "olive",
    "walk" -> "purple"
  )

  var debug: Boolean = true
  var debugOutputDir: Path = Paths.get(System.getProperty("java.io.tmpdir"))

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  def asTimestamp(timestamp: String): Timestamp =
    Timestamp.valueOf(LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

  def predict(trip: Trip,
              accRecords: Seq[AccelerometerRecord],
              user: String,
              tripIdx: Integer): Seq[(String, Double, Timestamp)] = {
    /**
      * Probability matrix containing, for each accelerometer value, the
      * probabilities for each transportation mode, e.g.
      *
      *   Bus: 0.10
      *   Car: 0.23
      *   Bike: 0.42
      *   ...
      */
    val probMatrix: ModeProbabilities =
      rClient.predict(accRecords.map(r => (r.x, r.y, r.z, r.timestamp)))

    // reduce the matrix to a vector of triples containing
    // - the mode that had the highest probability for the given sensor value
    // - the modes probability
    // - the timestamp of the sensor value
    val bestModes: Seq[((String, Double, Timestamp), (Timestamp, (Double, Double, Double, Double, Double, Double)))] =
    getBestModes(probMatrix)

    // cleaned best modes
    val cleanedBestModes: Seq[(String, Double, Timestamp)] =
      MajorityVoteTripCleaning(TimeWindow.of(60, TimeUnit.SECONDS), iterations = 3).clean(trip, bestModes.map(_._1), probMatrix)._2

    if(debug) {
      // print hte raw GeoJSON points and lines
      GeoJSONExporter.write(
        GeoJSONConverter.merge(
          GeoJSONConverter.toGeoJSONPoints(trip.trace),
          GeoJSONConverter.toGeoJSONLineString(trip.trace)),
        debugOutputDir.resolve(
          s"${user}_trip${tripIdx}_lines_with_points.json").toString)

        // raw best modes
        Files.write(
          debugOutputDir.resolve(s"${user}_trip${tripIdx}_best_modes.out"),
          bestModes.mkString("\n").getBytes(Charset.forName("UTF-8")))


        Files.write(
          debugOutputDir.resolve(s"${user}_trip${tripIdx}_best_modes_cleaned.out"),
          cleanedBestModes.mkString("\n").getBytes(Charset.forName("UTF-8")))

        // GeoJson with modes highlighted
//        visualizeModes(trip, bestModes, tripIdx)
//        visualizeModes(trip, cleanedBestModes.toList, tripIdx, fileSuffix = "_cleaned")
      }

//    rClient.stop()

    cleanedBestModes
  }

  /**
    * Should be the main method which returns ... TODO
    * given the data of a single user for
    * a single day
    *
    * @param gpsTrajectory the GPS trajectory
    * @param accelerometerData the accelerometer data
    */
  def predict(gpsTrajectory: Seq[TrackPoint],
              accelerometerData: Seq[(Double, Double, Double, Timestamp)]): Seq[(Trip, ModeProbabilities)] = {

    // 1. we split the data into trips
    val splittedData = splitTrips(gpsTrajectory, accelerometerData)

    // 2. for each trip, we try to predict the different modes of transportation
    val tripWithModeProbs = splittedData.map(data => (data._1, rClient.predict(data._2)))


    // keep only "best" modes
    val tripWithBestModes = tripWithModeProbs.map { case (trip, modes) => (trip, getBestModes(modes).distinct.toList, modes) }
    if(debug) {

      for (((trip, bestModes, modes), idx) <- tripWithBestModes.zipWithIndex) {

        // print hte raw GeoJSON points and lines
        GeoJSONExporter.write(
          GeoJSONConverter.merge(
            GeoJSONConverter.toGeoJSONPoints(trip.trace),
            GeoJSONConverter.toGeoJSONLineString(trip.trace)),
          debugOutputDir.resolve(s"trip${idx}_lines_with_points.json").toString)

        // raw best modes
        Files.write(
          debugOutputDir.resolve(s"trip${idx}_best_modes.out"),
          bestModes.mkString("\n").getBytes(Charset.forName("UTF-8")))

        // cleaned best modes
        val cleanedBestModes = MajorityVoteTripCleaning(Window(100), iterations = 10).clean(trip, bestModes.map(_._1), modes)._2
        Files.write(
          debugOutputDir.resolve(s"trip${idx}_best_modes_cleaned.out"),
          cleanedBestModes.mkString("\n").getBytes(Charset.forName("UTF-8")))

        // GeoJson with modes highlighted
        visualizeModes(trip, bestModes.map(_._1), idx)
        visualizeModes(trip, cleanedBestModes.toList, idx, fileSuffix = "_cleaned")
      }
    }

    // clean best modes


//    //"20180409134345769","46.09043","11.10995","301.0"
//    //"20180409161831401","46.09118","11.10932","258.0"
//    val testGPS = Seq(
//      TrackPoint(46.09043, 11.10995, asTimestamp("20180409134345769")),
//      TrackPoint(46.09118, 11.10932, asTimestamp("20180409161831401"))
//    )
//    val testAccData = accelerometerData.filter(p => p._4.after(testGPS.head.timestamp) && p._4.before(testGPS.last.timestamp))
//    val testTrip = NonClusterTrip(testGPS.head, testGPS.last, testGPS)
//    val testModeProbs = tripDataWithModeProbs(1)._2.probabilities
//      .filter(e => e._1.after(testTrip.start.timestamp) && e._1.before(testTrip.end.timestamp))
//    visualizeModes(testTrip, ModeProbabilities(tripDataWithModeProbs(1)._2.schema, testModeProbs), 999)


    rClient.stop()

    tripWithModeProbs

  }

  private def visualizeModes(trip: Trip, bestModes: List[(String, Double, Timestamp)], idx: Int, fileSuffix: String = "") = {
    logger.debug(s"visualizing trip$idx ...")
    val f = (e1: (String, Double, Timestamp), e2: (String, Double, Timestamp)) => e1._1 == e2._1

    // compress the data,
    // i.e. (mode1, mode1, mode1, mode2, mode2, mode1, mode3) -> (mode1, mode2, mode1, mode3)
    val compressedModes = compress(bestModes, f)
    Files.write(debugOutputDir.resolve(s"trip${idx}_compressed_modes$fileSuffix.out"), compressedModes.mkString("\n").getBytes)

    // pick some colors for each mode
//    val colors = GeoJSONConverter.colors(6)

    // we generate pairs of GPS points, i.e. from (p1, p2, p3, ..., pn) we get ((p1, p2), (p2, p3), ..., (p(n-1), pn)
    val gpsPairs = trip.trace zip trip.trace.tail

    val lineStringsJson = gpsPairs.flatMap {
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
          // TODO we might not have seen any mode before because i) it might be the first point at all and ii) the first in the trip split
          val mode = compressedModes.filter(e => e._3.before(tp1.timestamp)).last
          Seq(GeoJSONConverter.toGeoJSONLineString(Seq(tp1, tp2), propertiesFor(mode._1)))
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

          // return the 2 JSON lines
          Seq(
            GeoJSONConverter.toGeoJSONLineString(Seq(tp1, newTP), propertiesFor(lastMode._1)),
            GeoJSONConverter.toGeoJSONLineString(Seq(newTP, tp2),propertiesFor(modeChange._1))
          )
//          Seq(
//            GeoJSONConverter.toGeoJSONLineString(
//              Seq(tp1, tp2),
//              Map("stroke" -> colors(modeProbabilities.schema.indexOf(modesBetween.head._1)),
//                "mode" -> modesBetween.head._1))
//          )
        } else {
          // for each mode we compute the distance taken based on time ratio
          // it contains a point and the mode used to this point
          val intermediatePointsWithMode = (modesBetween zip modesBetween.tail).map {
            case ((mode1, maxValue1, t1),(mode2, maxValue2, t2)) =>
              val timeMs = Duration.between(t2.toLocalDateTime, begin.toLocalDateTime).toMillis

              val ratio = timeMs.toDouble / timeTotalMs

              val distanceKm = ratio * distanceTotalKm

              val newPoint = TrackpointUtils.pointFrom(tp1, bearing, distanceKm)
//              println(s"time from start:$timeMs")
//              println(s"distance from start:$distanceKm")
//              println(s"new point:$newPoint")

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

          // generate line strings between all points
          (first ++ mid ++ last).map {
            case (p1, p2, mode) =>
              GeoJSONConverter.toGeoJSONLineString(Seq(p1, p2), propertiesFor(mode))
          }
        }

    }

//    // build lines JSON object between each mode change
//    val lineStringsJson = (compressedModes zip compressedModes.tail).map {
//      case ((mode1, maxValue1, t1),(mode2, maxValue2, t2)) =>
//        val points = trip.trace.filter(p => p.timestamp.after(t1) && p.timestamp.before(t2))
//        GeoJSONConverter.toGeoJSONLineString(points, Map("color" -> colors(modeProbabilities.schema.indexOf(mode1)).toString))
//    }

    // build the points JSON object
    val pointsJson = GeoJSONConverter.toGeoJSONPoints(trip.trace)

    // merge the JSON features
    val json = GeoJSONConverter.merge(lineStringsJson :+ pointsJson)

    // write to disk
    GeoJSONExporter.write(
      json,
      debugOutputDir.resolve(
        s"trip${idx}_lines_with_points_modes_colored$fileSuffix.json").toString)
  }

  private def propertiesFor(mode: String) =
    Map(
      "stroke" -> colorMapping(mode),
      "stroke-width" -> "2",
      "mode" -> mode)

  def toHexString(color: Color):String = "#" + Integer.toHexString(color.getRGB).substring(2)

  private def compress[A](l: List[A], fn: (A, A) => Boolean):List[A] = l.foldLeft(List[A]()) {
    case (List(), e) => List(e)
    case (ls, e) if fn(ls.last, e) => ls
    case (ls, e) => ls:::List(e)
  }

  private def getBestModes(modeProbabilities: ModeProbabilities)
  : Seq[((String, Double, Timestamp), (Timestamp, (Double, Double, Double, Double, Double, Double)))] = {
    modeProbabilities.probabilities.map(values => {
      val timestamp = values._1

      val valuesList = values._2.productIterator.map(_.asInstanceOf[Double]).toList

      // highest value
      val maxValue = valuesList.max

      // index of highest value
      val maxIdx = valuesList.indexOf(maxValue)

      // get mode type
      val mode = modeProbabilities.schema(maxIdx)

      ((mode, maxValue, timestamp), values)
    })
  }

  private def splitTrips(gpsTrajectory: Seq[TrackPoint],
                                    accelerometerData: Seq[(Double, Double, Double, Timestamp)])
  : Seq[(Trip, Seq[(Double, Double, Double, Timestamp)])] = {
    logger.debug("splitting data into trips ...")

    // detect trips based on GPS data
    val tripDetector = new WindowDistanceTripDetection(300, 60, 0.25, 5, 7)
    val trips = tripDetector.find(gpsTrajectory)
    logger.debug(s"got ${trips.size} trips")

    // split the accelerometer data based on the GPS trips
    val splittedData = trips.map(trip => {
      (trip, accelerometerData.filter(p => p._4.after(trip.start.timestamp) && p._4.before(trip.end.timestamp)))
    })

    splittedData
  }

  var serverMode = false

  def withServerMode(): Predict = {
    serverMode = true
    this
  }
}

object Predict {

  def main(args: Array[String]): Unit = {
    val rScriptPath = args(0)
    val gpsPath = args(1)
    val accPath = args(2)

    val gpsData = Files.readAllLines(Paths.get(gpsPath)).asScala
      .drop(1)
      .map(line => line.replace("\"", "").split(","))
      .map{case Array(t, lat, long, alt) => TrackPoint(lat.toDouble, long.toDouble, asTimestamp(t))}

    val accelerometerData = Files.readAllLines(Paths.get(accPath)).asScala
      .drop(1)
      .map(line => line.replace("\"", "").split(","))
      .map{case Array(t, x, y, z) => (x.toDouble, y.toDouble, z.toDouble, asTimestamp(t))}

    val res = new Predict(rScriptPath,
      s"$rScriptPath/prediction_server.r",
      s"$rScriptPath/model.rds")
      .withServerMode()
      .predict(gpsData, accelerometerData)

    println(res.mkString("\n"))
  }

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  def asTimestamp(timestamp: String): Timestamp =
    Timestamp.valueOf(LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

}
