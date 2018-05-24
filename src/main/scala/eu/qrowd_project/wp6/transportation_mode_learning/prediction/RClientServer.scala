package eu.qrowd_project.wp6.transportation_mode_learning.prediction
import java.io.PrintStream
import java.net.{InetAddress, Socket}
import java.sql.Timestamp
import java.util.concurrent.TimeUnit

import scala.io.BufferedSource

/**
  * @author Lorenz Buehmann
  */
class RClientServer(baseDir: String, serverScriptPath: String, clientScriptPath: String, modelPath: String)
  extends RClient(baseDir, clientScriptPath, modelPath) {

  val logger = com.typesafe.scalalogging.Logger("RClientServer")

  var serverProcess: sys.process.Process = _

  start()

  /**
    * Start the server script.
    */
  def start() = {
    logger.debug("starting R server...")
    // call external R script
    val command = s"Rscript --vanilla $serverScriptPath -m $modelPath"
    println(command)
    serverProcess = sys.process.Process(command, new java.io.File(baseDir)).run()
    Thread.sleep(5000)
    logger.debug("sucessfully started R server.")
  }

  /**
    * Stop the server script.
    */
  def stop() = {
    logger.debug("stopping R server...")
    serverProcess.destroy()
  }

  override def predict(accelerometerData: Seq[(Double, Double, Double, Timestamp)]): ModeProbabilities = {
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
