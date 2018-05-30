package eu.qrowd_project.wp6.transportation_mode_learning.prediction
import java.io.PrintStream
import java.net.{InetAddress, Socket}
import java.sql.Timestamp
import java.util.concurrent.TimeUnit

import scala.io.BufferedSource

/**
  * @author Lorenz Buehmann
  */
class RClientServer(
                     baseDir: String,
                     serverScriptPath: String,
                     modelPath: String,
                     serverHost: String = "localhost",
                     serverPort: Int = 6011)
  extends RClient(baseDir, serverScriptPath, modelPath) {

  val logger = com.typesafe.scalalogging.Logger("RClientServer")

  var serverProcess: sys.process.Process = _

  start()


  /**
    * Start the server script.
    */
  def start() = {
    logger.info(s"starting R server at $serverHost:$serverPort...")
    // call external R script
    val command = s"Rscript --vanilla $serverScriptPath  -m $modelPath"
    println(command)
    serverProcess = sys.process.Process(command, new java.io.File(baseDir)).run()
    Thread.sleep(5000)
    logger.info("successfully started R server.")
  }

  /**
    * Stop the server script.
    */
  override def stop() = {
    logger.info("stopping R server...")
    serverProcess.destroy()
    logger.info("stopped R server.")
  }

  override def predict(accelerometerData: Seq[(Double, Double, Double, Timestamp)]): ModeProbabilities = {
    logger.info("prediction call...")
    // write data as CSV to disk
    val inputFile = serializeInput(accelerometerData)

    // "talk" to R script
    val s = new Socket(InetAddress.getByName("localhost"), 6011)
    lazy val in = new BufferedSource(s.getInputStream).getLines()
    val out = new PrintStream(s.getOutputStream)
    out.println(inputFile.toFile.getAbsolutePath)
    out.flush()
    // read response from server to block until computation was finished
    in.next()
    s.close()
    out.close()

    // read output from CSV to internal list
    val (header, probabilities) = readOutput()

    // keep track of timestamp from input
    val probsWithTime = (accelerometerData zip probabilities).map(pair => {
      (pair._1._4, pair._2._1, pair._2._2, pair._2._3,pair._2._4, pair._2._5, pair._2._6)
    })

    val predictedProbabilities = ModeProbabilities(header, probsWithTime)

    assert(accelerometerData.size == probabilities.size,
      s"size of input (${accelerometerData.size}) and output (${probabilities.size} not the same.")

    predictedProbabilities
  }

}
