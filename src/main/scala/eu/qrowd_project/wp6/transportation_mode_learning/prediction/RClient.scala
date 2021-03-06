package eu.qrowd_project.wp6.transportation_mode_learning.prediction

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.sql.Timestamp

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * Wraps an external R-script for mode prediction. In particular, we're
  *
  *  - writing data as CSV to tmp dir
  *  - running the script
  *  - reading the generated output CSV from disk
  *
  * @param baseDir base directory of the R project
  * @param scriptPath path to the R script for prediction
  * @param modelPath path to the R model
  *
  * @author Lorenz Buehmann
  */
class RClient(baseDir: String, scriptPath: String, modelPath: String) {

  /**
    * Calls the mode prediction given the accelerometer data.
    *
    * @param accelerometerData x,y,z values of the raw accelerometer data
    * @param id some optional identifier to keep track of input data for R code written to disk in temp folder
    */
  def predict(accelerometerData: Seq[(Double, Double, Double, Timestamp)],
              id: String = ""): ModeProbabilities = {
    // write data as CSV to disk
    val inputFile = serializeInput(accelerometerData, id)

    // call external R script
    val command = s"Rscript --vanilla $scriptPath prediction $modelPath ${inputFile.toFile.getAbsolutePath} "
    sys.process.Process(command, new java.io.File(baseDir)).!

    // read output from CSV to internal list
    val (header, probabilities) = readOutput()

    // keep track of timestamp from input
    val probsWithTime = (accelerometerData zip probabilities).map(pair => {
      (pair._1._4, (pair._2._1, pair._2._2, pair._2._3,pair._2._4, pair._2._5, pair._2._6))
    })

    val predictedProbabilities = ModeProbabilities(header, probsWithTime)

    assert(accelerometerData.size == probabilities.size)

    predictedProbabilities
  }

  def serializeInput(accelerometerData: Seq[(Double, Double, Double, Timestamp)],
                     id: String = ""): Path = {
    // write data as CSV to disk
    val inputFile = File.createTempFile(s"qrowd_acc_data-$id-", ".tmp").toPath
    val content = "x,y,z,timestamp\n" + accelerometerData.map(entry => (entry._1, entry._2, entry._3, entry._4).productIterator.mkString(",")).mkString("\n")
    Files.write(inputFile, content.getBytes("UTF-8"))
  }

  def readOutput(): (Seq[String], mutable.Buffer[(Double, Double, Double, Double, Double, Double)]) = {
    val outputCSV = Paths.get(baseDir).resolve("out.csv")
    val lines = Files.readAllLines(outputCSV).asScala // read all lines
    val header = lines.head.replace("\"", "").split(",").toSeq
    val probabilities = lines
      .drop(1) // skip header
      .map(line => line.split(",").map(_.toDouble)) // split by comma
      .map { case Array(a, b, c, d, e, f) => (a, b, c, d, e, f)} // convert probabilities to tuple
    (header, probabilities)
  }

  def stop(): Unit = {}

}
