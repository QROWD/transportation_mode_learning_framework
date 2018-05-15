package eu.qrowd_project.wp6.transportation_mode_learning

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Paths}

import scala.collection.JavaConverters._


/**
  *
  * We're using an external R-script by
  *
  *  - writing data as CSV to disk
  *  - running the script
  *  - reading the generated output CSV from disk
  *
  * @param baseDir base directory of the R project
  * @param scriptPath path to the R script
  * @param modelPath path to the R model
  *
  * @author Lorenz Buehmann
  */
class Predict(baseDir: String, scriptPath: String, modelPath: String) {


  /**
    * Calls the prediction.
    *
    * @param data x,y,z values of the accelerometer
    */
  def predict(data: Seq[(Double, Double, Double)]) = {

    // write data as CSV to disk
    val inputFile = File.createTempFile("qrowddata", ".tmp").toPath
    val content = "x,y,z\n" + data.map(entry => entry.productIterator.mkString(",")).mkString("\n")
    Files.write(inputFile, content.getBytes("UTF-8"))

    // call external R script
    val command = s"Rscript --vanilla $scriptPath prediction $modelPath ${inputFile.toFile.getAbsolutePath} "
    println(command)
    sys.process.Process(command, new java.io.File(baseDir)).!

    // read output from CSV to internal list
    val outputCSV = Paths.get(baseDir).resolve("out.csv")
    val result = Files.readAllLines(outputCSV).asScala // read all lines
      .drop(1) // skip header
      .map(line => line.split(",").map(_.toDouble)) // split by comma
      .map { case Array(a, b, c, d, e, f) => (a, b, c, d, e, f)} // convert to result object

    println(result.size == data.size)

    result
  }

}

object Predict {

  def main(args: Array[String]): Unit = {
    val data = Files.readAllLines(Paths.get("/home/user/work/r/TR/datasets/car.csv")).asScala
      .drop(1)
      .map(lines => lines.split(","))
      .map(cols => (cols(1).toDouble, cols(2).toDouble, cols(3).toDouble))

    val res = new Predict("/home/user/work/r/TR",
      "/home/user/work/r/TR/run.r",
      "/home/user/work/r/TR/model.rds")
      .predict(data)

    println(res.mkString("\n"))
  }

}
