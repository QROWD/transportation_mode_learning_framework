package eu.qrowd_project.wp6.transportation_mode_learning

import java.awt.Color
import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}
import java.sql.Timestamp
import java.time.{Duration, LocalDateTime}
import java.time.format.DateTimeFormatter

import scala.collection.JavaConverters._

import eu.qrowd_project.wp6.transportation_mode_learning.scripts._
import eu.qrowd_project.wp6.transportation_mode_learning.util.LocationEventRecord.dateTimeFormatter
import eu.qrowd_project.wp6.transportation_mode_learning.util._


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

  val colors = Seq("red", "green", "blue", "yellow", "olive", "purple")
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  def asTimestamp(timestamp: String): Timestamp =
    Timestamp.valueOf(LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

  /**
    * Should be the main method which returns ... TODO
    * given the data of a single user for
    * a single day
    *
    * @param gpsTrajectory the GPS trajectory
    * @param accelerometerData the accelerometer data
    */
  def predict(gpsTrajectory: Seq[TrackPoint],
              accelerometerData: Seq[(Double, Double, Double, Timestamp)]) = {

    // 1. we split the data into trips
    val splittedData = splitTrips(gpsTrajectory, accelerometerData)


    // 2. for each trip, we try to predict the different modes of transportation
    val tripDataWithModeProbs = splittedData.map(data => (data._1, predictModes(data._2)))

    //"20180409134345769","46.09043","11.10995","301.0"
    //"20180409161831401","46.09118","11.10932","258.0"
    val testGPS = Seq(
      TrackPoint(46.09043, 11.10995, asTimestamp("20180409134345769")),
      TrackPoint(46.09118, 11.10932, asTimestamp("20180409161831401"))
    )
    val testAccData = accelerometerData.filter(p => p._4.after(testGPS.head.timestamp) && p._4.before(testGPS.last.timestamp))
    val testTrip = NonClusterTrip(testGPS.head, testGPS.last, testGPS)
    val testModeProbs = tripDataWithModeProbs(1)._2.probabilities
      .filter(e => e._1.after(testTrip.start.timestamp) && e._1.before(testTrip.end.timestamp))
    visualizeModes(testTrip, ModeProbabilities(tripDataWithModeProbs(1)._2.schema, testModeProbs), 999)

    for (((trip, probs), idx) <- tripDataWithModeProbs.zipWithIndex) {
      Files.write(
        Paths.get(s"/tmp/trip${idx}_best_modes.out"),
        getBestModes(probs).mkString("\n").getBytes(Charset.forName("UTF-8")))
      GeoJSONExporter.write(
        GeoJSONConverter.merge(
          GeoJSONConverter.toGeoJSONPoints(trip.trace),
          GeoJSONConverter.toGeoJSONLineString(trip.trace)),
        s"/tmp/trip${idx}_lines_with_points.json")
      visualizeModes(trip, probs, idx)
    }

    tripDataWithModeProbs

  }

  private def visualizeModes(trip: Trip, modeProbabilities: ModeProbabilities, idx: Int) = {
    println(s"visualizing trip$idx ...")
    val bestModes = getBestModes(modeProbabilities).distinct.toList
    val f = (e1: (String, Double, Timestamp), e2: (String, Double, Timestamp)) => e1._1 == e2._1

    // compress the data,
    // i.e. (mode1, mode1, mode1, mode2, mode2, mode1, mode3) -> (mode1, mode2, mode1, mode3)
    val compressedModes = compress(bestModes, f)
    Files.write(Paths.get(s"/tmp/trip${idx}_compressed_modes.out"), compressedModes.mkString("\n").getBytes)

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
          Seq(GeoJSONConverter.toGeoJSONLineString(
            Seq(tp1, tp2),
            Map("stroke" -> colors(modeProbabilities.schema.indexOf(mode._1)),
                "mode" -> mode._1)
          ))
        } else if(modesBetween.size == 1) { // handle single mode change between both points
          // compute the split point
//          val modeChange = modesBetween.head
//          val timeMs = Duration.between(modeChange._3.toLocalDateTime, begin.toLocalDateTime).toMillis
//          val ratio = timeMs.toDouble / timeTotalMs
//          val distanceKm = ratio * distanceTotalKm
//          val splitPoint = TrackpointUtils.pointFrom(tp1, bearing, distanceKm)
//          val newTP = TrackPoint(splitPoint.lat, splitPoint.long, modeChange._3)
//
//          // get the last mode before the starting point
//          val lastMode = compressedModes.filter(e => e._3.before(tp1.timestamp)).last

          // return the 2 JSON lines
//          Seq(
//            GeoJSONConverter.toGeoJSONLineString(
//              Seq(tp1, newTP),
//              Map("stroke" -> colors(modeProbabilities.schema.indexOf(lastMode._1)),
//                "mode" -> lastMode._1)),
//            GeoJSONConverter.toGeoJSONLineString(
//            Seq(newTP, tp2),
//            Map("stroke" -> colors(modeProbabilities.schema.indexOf(modesBetween.head._1)),
//                "mode" -> modesBetween.head._1))
//          )
          Seq(
            GeoJSONConverter.toGeoJSONLineString(
              Seq(tp1, tp2),
              Map("stroke" -> colors(modeProbabilities.schema.indexOf(modesBetween.head._1)),
                "mode" -> modesBetween.head._1))
          )
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
          val first = Seq((tp1, intermediatePointsWithMode.head._1, intermediatePointsWithMode.head._2))
          val mid =  (intermediatePointsWithMode zip intermediatePointsWithMode.tail).map{
            case ((p1, mode1), (p2, mode2)) =>
              (p1, p2, mode2)
          }
          val last = Seq((intermediatePointsWithMode.last._1, tp2, intermediatePointsWithMode.last._2))

          // generate line strings between all points
          (first ++ mid ++ last).map {
            case (p1, p2, mode) =>
              GeoJSONConverter.toGeoJSONLineString(
                Seq(p1, p2),
                Map("stroke" -> colors(modeProbabilities.schema.indexOf(mode)),
                    "mode" -> mode))
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
    GeoJSONExporter.write(json, s"/tmp/trip${idx}_lines_with_points_modes_colored.json")
  }

  def toHexString(color: Color):String = "#" + Integer.toHexString(color.getRGB).substring(2)

  private def compress[A](l: List[A], fn: (A, A) => Boolean):List[A] = l.foldLeft(List[A]()) {
    case (List(), e) => List(e)
    case (ls, e) if fn(ls.last, e) => ls
    case (ls, e) => ls:::List(e)
  }

  private def getBestModes(modeProbabilities: ModeProbabilities): Seq[(String, Double, Timestamp)] = {
    modeProbabilities.probabilities.map(values => {
      val timestamp = values._1

      val valuesList = values.productIterator.toSeq.drop(1).map(_.asInstanceOf[Double])
      // highest value
      val maxValue = valuesList.max
      // index of highest value
      val maxIdx = valuesList.indexOf(maxValue)
      // get mode type
      val mode = modeProbabilities.schema(maxIdx)

      (mode, maxValue, timestamp)
    })
  }

  private def splitTrips(gpsTrajectory: Seq[TrackPoint],
                                    accelerometerData: Seq[(Double, Double, Double, Timestamp)])
  : Seq[(Trip, Seq[(Double, Double, Double, Timestamp)])] = {


    // detect trips based on GPS data
    val tripDetector = new WindowDistanceTripDetection(300, 60, 0.25, 5, 7)
    val trips = tripDetector.find(gpsTrajectory)

    // split the accelerometer data based on the GPS trips
    val splittedData = trips.map(trip => {
      (trip, accelerometerData.filter(p => p._4.after(trip.start.timestamp) && p._4.before(trip.end.timestamp)))
    })

    splittedData
  }


  /**
    * Calls the mode prediction given the accelerometer data.
    *
    * @param accelerometerData x,y,z values of the raw accelerometer data
    */
  def predictModes(accelerometerData: Seq[(Double, Double, Double, Timestamp)]) = {

    // write data as CSV to disk
    val inputFile = File.createTempFile("qrowddata", ".tmp").toPath
    val content = "x,y,z\n" + accelerometerData.map(entry => (entry._1, entry._2, entry._3).productIterator.mkString(",")).mkString("\n")
    Files.write(inputFile, content.getBytes("UTF-8"))

    // call external R script
    val command = s"Rscript --vanilla $scriptPath prediction $modelPath ${inputFile.toFile.getAbsolutePath} "
    sys.process.Process(command, new java.io.File(baseDir)).!

    // read output from CSV to internal list
    val outputCSV = Paths.get(baseDir).resolve("out.csv")
    val lines = Files.readAllLines(outputCSV).asScala // read all lines
    val header = lines.head.split(",").toSeq
    val probabilities = lines
      .drop(1) // skip header
      .map(line => line.split(",").map(_.toDouble)) // split by comma
      .map { case Array(a, b, c, d, e, f) => (a, b, c, d, e, f)} // convert probabilities to tuple

    // keep track of timestamp from input
    val probsWithTime = (accelerometerData zip probabilities).map(pair => {
      (pair._1._4, pair._2._1, pair._2._2, pair._2._3,pair._2._4, pair._2._5, pair._2._6)
    })

    val predictedProbabilities = ModeProbabilities(header, probsWithTime)

    assert(accelerometerData.size == probabilities.size)


    predictedProbabilities
  }

}

case class ModeProbabilities(schema: Seq[String], probabilities: Seq[(Timestamp, Double, Double, Double, Double, Double, Double)]) {
  override def toString: String = s"$schema\n${probabilities.take(20)} (${Math.max(0, probabilities.size - 20)} remaining)"
}

object Predict {

  def main(args: Array[String]): Unit = {
    val gpsPath = args(0)
    val accPath = args(1)
    val rScriptPath = args(2)

    val gpsData = Files.readAllLines(Paths.get(gpsPath)).asScala
      .drop(1)
      .map(line => line.replace("\"", "").split(","))
      .map{case Array(t, lat, long, alt) => TrackPoint(lat.toDouble, long.toDouble, asTimestamp(t))}

    val accelerometerData = Files.readAllLines(Paths.get(accPath)).asScala
      .drop(1)
      .map(line => line.replace("\"", "").split(","))
      .map{case Array(t, x, y, z) => (x.toDouble, y.toDouble, z.toDouble, asTimestamp(t))}

    val res = new Predict(rScriptPath,
      s"$rScriptPath/run.r",
      s"$rScriptPath/model.rds")
      .predict(gpsData, accelerometerData)

    println(res.mkString("\n"))
  }

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  def asTimestamp(timestamp: String): Timestamp =
    Timestamp.valueOf(LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

}
