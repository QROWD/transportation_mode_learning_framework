package eu.qrowd_project.wp6.transportation_mode_learning.prediction
import java.sql.Timestamp

/**
  * @author Lorenz Buehmann
  */
class RClientServer(baseDir: String, scriptPath: String, modelPath: String)
  extends RClient(baseDir, scriptPath, modelPath) {

  val logger = com.typesafe.scalalogging.Logger("RClientServer")

  /**
    * Start the server script.
    */
  def start() = {
    logger.debug("starting R server...")

  }

  /**
    * Stop the server script.
    */
  def stop() = {
    logger.debug("stopping R server...")

  }

  override def predict(accelerometerData: Seq[(Double, Double, Double, Timestamp)]): ModeProbabilities = {
    super.predict(accelerometerData)
  }

}
