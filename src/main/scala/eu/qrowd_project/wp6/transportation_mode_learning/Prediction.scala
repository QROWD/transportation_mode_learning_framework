package eu.qrowd_project.wp6.transportation_mode_learning

import java.awt.Color
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDateTime}

import eu.qrowd_project.wp6.transportation_mode_learning.prediction._
import eu.qrowd_project.wp6.transportation_mode_learning.scripts._
import eu.qrowd_project.wp6.transportation_mode_learning.util._


/**
  * 1. split raw data into trips
  * 2. predict mode probabilities per trip
  *
  * We're using an external Python script for mode prediction, i.e. we're
  *
  *  - writing data as CSV to disk
  *  - running the script
  *  - reading the generated output CSV from disk
  *
  * 3. determine mode transitions
  *
  * @param baseDir base directory of the Python project
  * @param scriptPath path to the Python script
  * @param modelPath path to the directory containing the trained Python models
  *
  * @author Lorenz Buehmann
  * @author Patrick Westphal
  */
class Prediction(baseDir: String, scriptPath: String, modelPath: String) {
  type Mode = String
  val logger = com.typesafe.scalalogging.Logger("Predict")

  private lazy val pythonClient = {
      new PythonClient(baseDir, scriptPath, modelPath)
  }

  val colors = Seq("red", "green", "blue", "yellow", "olive", "purple", "pink")

  val colorMapping = Map(
    "bike" -> "red",
    "bus" -> "green",
    "car" -> "blue",
    "still" -> "yellow",
    "train" -> "olive",
    "walk" -> "purple",
    "e-bike" -> "pink"
  )

  var outputDir: Path = Paths.get(System.getProperty("java.io.tmpdir"))
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  def asTimestamp(timestamp: String): Timestamp =
    Timestamp.valueOf(
      LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

  def predict(trip: Trip,
              accRecords: Seq[AccelerometerRecord],
              user: String,
              tripIdx: Integer,
              provenanceRecorder: Option[ProvenanceRecorder] = None): (Seq[(String, Double, Timestamp)], String) = {

    var predictedStages: Seq[PredictedStage] = null

    if (accRecords.isEmpty) {
      // Fallback in case there was no or not enough accelerometer data
      predictedStages = Seq(PredictedStage(
        "car",
        trip.start.timestamp,
        trip.end.timestamp,
        0.1,
        "guessed"))
    } else {
      predictedStages =
        pythonClient.predict(accRecords.map(r => (r.x, r.y, r.z, r.timestamp)),
          id = s"${user}_trip$tripIdx")

      // Fallback in case there was no or not enough accelerometer data
      if (predictedStages.isEmpty) {
        predictedStages = Seq(PredictedStage(
          "car",
          trip.start.timestamp,
          trip.end.timestamp,
          0.1,
          "guessed"))
      }
    }

    // Store the raw GeoJSON points and lines
    GeoJSONExporter.write(
      GeoJSONConverter.merge(
        GeoJSONConverter.toGeoJSONPoints(trip.trace),
        GeoJSONConverter.toGeoJSONLineString(trip.trace)),
      outputDir.resolve(s"trip${tripIdx}_lines_with_points.json").toString)

    Files.write(
      outputDir.resolve(s"trip${tripIdx}_predicted_stages.out"),
      predictedStages.mkString("\n").getBytes(Charset.forName("UTF-8")))

    // GeoJson with modes highlighted
    val outFilePath: String = visualizeModes(
      trip, predictedStages, tripIdx, fileSuffix = "_cleaned")

    // Provenance logging
    provenanceRecorder match {
      case Some(provRec) => provRec.addPredictions(predictedStages)
      case None =>
    }

    (
      predictedStages
        .map(stage => (stage.label, stage.prob, stage.startTimestamp)),
      outFilePath
    )
  }

  private def visualizeModes(
                              trip: Trip,
                              stages: Seq[PredictedStage],
                              idx: Int,
                              fileSuffix: String = ""): String = {

    logger.debug(s"visualizing trip$idx ...")
    val f =
      (e1: (String, Double, Timestamp), e2: (String, Double, Timestamp)) =>
        e1._1 == e2._1

    val bestModes: List[(String, Double, Timestamp)] =
      stages
        .map(stage => (stage.label, stage.prob, stage.startTimestamp)).toList

    // compress the data,
    // i.e. (mode1, mode1, mode1, mode2, mode2, mode1, mode3) -> (mode1, mode2, mode1, mode3)
    val compressedModes = compress(bestModes, f)
    Files.write(
      outputDir.resolve(s"trip${idx}_compressed_modes$fileSuffix.out"),
      compressedModes.mkString("\n").getBytes)

    // we generate pairs of GPS points, i.e. from (p1, p2, p3, ..., pn) we get
    // ((p1, p2), (p2, p3), ..., (p(n-1), pn)
    val gpsPairs = trip.trace zip trip.trace.tail

    val lineStringsJson = gpsPairs.flatMap {
      case (tp1, tp2) =>
        val begin = tp1.timestamp
        val end = tp2.timestamp

        // bearing
        val bearing = TrackpointUtils.bearing(tp1, tp2)

        // total time between t2 and t1 in ms
        val timeTotalMs = Duration.between(
          tp2.timestamp.toLocalDateTime, tp1.timestamp.toLocalDateTime).toMillis

        // total distance
        val distanceTotalKm = HaversineDistance.compute(tp1, tp2)

        // get all modes in time range between both GPS points
        val modesBetween =
          compressedModes.filter(e => e._3.after(begin) && e._3.before(end))

        if(modesBetween.isEmpty) { // handle no mode change between both points
          // this happens due to compression, just take the last known mode
          // TODO we might not have seen any mode before because
          // i) it might be the first point at all and
          // ii) the first in the trip split
          val modes = compressedModes.filter(e => e._3.before(tp1.timestamp))

          var mode: (String, Double, Timestamp) = null

          if (modes.nonEmpty) {
            mode = modes.last

          } else {
            // In case the there are no predicted modes in the range between
            // both GPS points (which may happen e.g. if we did't have
            // accelerometer data for this period) we just fall back to a
            // arbitrary, but safe mode (in the sense that we can assume it
            // exists) and pick the very first predictded mode of the overall
            // stage. This choice is definitely not meaningful in terms of the
            // mode prediction but better than crashing.
            mode = compressedModes.head
          }

          Seq(
            GeoJSONConverter.toGeoJSONLineString(
              Seq(tp1, tp2), propertiesFor(mode._1)))

        } else if(modesBetween.size == 1) { // handle single mode change between both points
          // compute the split point
          val modeChange = modesBetween.head

          val timeMs = Duration.between(
            modeChange._3.toLocalDateTime, begin.toLocalDateTime).toMillis

          val ratio = timeMs.toDouble / timeTotalMs
          val distanceKm = ratio * distanceTotalKm
          val splitPoint = TrackpointUtils.pointFrom(tp1, bearing, distanceKm)
          val newTP = TrackPoint(splitPoint.lat, splitPoint.long, modeChange._3)

          // get the last mode before the starting point if exists, otherwise
          // use mode change inside
          val lastMode = compressedModes
            .filter(e => e._3.before(tp1.timestamp))
            .lastOption.getOrElse(modeChange)

          // return the 2 JSON lines
          Seq(
            GeoJSONConverter.toGeoJSONLineString(
              Seq(tp1, newTP), propertiesFor(lastMode._1)),
            GeoJSONConverter.toGeoJSONLineString(
              Seq(newTP, tp2),propertiesFor(modeChange._1)))

        } else {
          // for each mode we compute the distance taken based on time ratio
          // it contains a point and the mode used to this point
          val intermediatePointsWithMode = (modesBetween zip modesBetween.tail).map {
            case ((mode1, maxValue1, t1),(mode2, maxValue2, t2)) =>
              val timeMs = Duration.between(
                t2.toLocalDateTime, begin.toLocalDateTime).toMillis

              val ratio = timeMs.toDouble / timeTotalMs

              val distanceKm = ratio * distanceTotalKm

              val newPoint = TrackpointUtils.pointFrom(tp1, bearing, distanceKm)
              (TrackPoint(newPoint.lat, newPoint.long, t2), mode1)
          }

          // Generate pairs of points with the mode used in between
          // TODO: actually, the first mode should come from before the GPS point instead of the next mode change, but just for rendering it's ok
          val first = Seq((
            tp1,
            intermediatePointsWithMode.head._1,
            intermediatePointsWithMode.head._2))

          val mid = (intermediatePointsWithMode zip intermediatePointsWithMode.tail).map {
            case ((p1, mode1), (p2, mode2)) =>
              (p1, p2, mode2)
          }

          val last = Seq((
            intermediatePointsWithMode.last._1,
            tp2,
            intermediatePointsWithMode.last._2))

          // generate line strings between all points
          (first ++ mid ++ last).map {
            case (p1, p2, mode) =>
              GeoJSONConverter.toGeoJSONLineString(
                Seq(p1, p2),
                propertiesFor(mode))
          }
        }
    }

    // build the points JSON object
    val pointsJson = GeoJSONConverter.toGeoJSONPoints(trip.trace)

    // merge the JSON features
    val json = GeoJSONConverter.merge(lineStringsJson :+ pointsJson)

    val outFilePath = outputDir.resolve(
      s"trip${idx}_lines_with_points_modes_colored$fileSuffix.json").toString

    // write to disk
    GeoJSONExporter.write(json, outFilePath)

    outFilePath
  }

  private def propertiesFor(mode: String) =
    Map(
      "stroke" -> colorMapping(mode),
      "stroke-width" -> "2",
      "mode" -> mode)

  def toHexString(color: Color):String = "#" + Integer.toHexString(color.getRGB).substring(2)

  private def compress[A](l: List[A], fn: (A, A) => Boolean): List[A] =
    l.foldLeft(List[A]()) {
      case (List(), e) => List(e)
      case (ls, e) if fn(ls.last, e) => ls
      case (ls, e) => ls:::List(e)
    }

  private def getBestModes(modeProbabilities: ModeProbabilities):
      Seq[((String, Double, Timestamp), (Timestamp, (Double, Double, Double, Double, Double, Double)))] = {

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
}
