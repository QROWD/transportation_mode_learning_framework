package eu.qrowd_project.wp6.transportation_mode_learning

import java.io.File
import java.nio.file.{Files, Paths}
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scala.collection.JavaConverters._
import eu.qrowd_project.wp6.transportation_mode_learning.scripts.{ClusterTrip, Trip, TripDetection}
import eu.qrowd_project.wp6.transportation_mode_learning.util.LocationEventRecord.dateTimeFormatter
import eu.qrowd_project.wp6.transportation_mode_learning.util.TrackPoint


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

    tripDataWithModeProbs

  }

  private def splitTrips(gpsTrajectory: Seq[TrackPoint],
                                    accelerometerData: Seq[(Double, Double, Double, Timestamp)])
  : Seq[(Trip, Seq[(Double, Double, Double, Timestamp)])] = {


    // detect trips based on GPS data
    val tripDetector = new TripDetection()
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
    val predictedProbabilities = ModeProbabilities(header, probabilities)

    assert(accelerometerData.size == probabilities.size)

    predictedProbabilities
  }

}

case class ModeProbabilities(schema: Seq[String], probabilities: Seq[(Double, Double, Double, Double, Double, Double)])

object Predict {

  def main(args: Array[String]): Unit = {
    val gpsPath = args(0)
    val accPath = args(1)
    val rScriptPath = args(2)

    val gpsData = Files.readAllLines(Paths.get(gpsPath)).asScala
      .drop(1)
      .map(line => line.replace("\"", "").split(","))
      .map{case Array(t, lat, long, alt) => TrackPoint(lat.toDouble, long.toDouble, asTimestamp(t))}

    val data = Files.readAllLines(Paths.get(accPath)).asScala
      .drop(1)
      .map(line => line.replace("\"", "").split(","))
      .map{case Array(t, x, y, z) => (x.toDouble, y.toDouble, z.toDouble, asTimestamp(t))}

    val res = new Predict(rScriptPath,
      s"$rScriptPath/run.r",
      s"$rScriptPath/model.rds")
      .predict(gpsData, data)

    println(res)
  }

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
  private def asTimestamp(timestamp :String) =
    Timestamp.valueOf(LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

}
