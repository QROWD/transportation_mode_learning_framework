package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.File

import eu.qrowd_project.wp6.transportation_mode_learning.preprocessing.{
  STDBSCAN,
  TDBSCAN
}
import eu.qrowd_project.wp6.transportation_mode_learning.util.TrackPoint

/**
  * Determine stops given a GPS trajectory, i.e. a sequence of GPS points tagged with a timestamp.
  *
  * Right now, we perform spatio-temporal clustering to solve this task.
  *
  * @param ceps   the distance range to ensure that the points comprising a stop are of state continuity (in km)
  * @param eps    the search radius for identifying density-based neighborhood (in km)
  * @param minPts the minimum number of neighboring points to identify a core point
  * @author Lorenz Buehmann
  */
class StopDetection(val ceps: Double = 0.3,
                    val eps: Double = 0.1,
                    val minPts: Int = 80) {

  val clusterer = new TDBSCAN(ceps, eps, minPts)

  /**
    * Find stop points given a set of time-tagged GPS points P.
    * The result are sequences of GPS points that denote a stop point.
    *
    * @param  points the sequence of GPS points
    * @return sequences of GPS points that denote a stop point
    */
  def find(points: Seq[TrackPoint]): Seq[Seq[TrackPoint]] = {
    import scala.collection.JavaConverters._
    //    println("ST-DBSCAN:" + new STDBSCAN(eps, 60, minPts).cluster(points.asJava).asScala.map(c => c.getPoints).mkString("\n"))
    clusterer
      .cluster(points)
//      .filter(_.size >= minPts)
  }
}

object StopDetection {

  def apply(ceps: Double = 0.3, eps: Double = 0.1, minPts: Int = 80): StopDetection = new StopDetection(ceps, eps, minPts)


  def main(args: Array[String]): Unit = {

  }

  case class Config(in: File = new File("."),
                    out: File = new File("."),
                    windowSize: Int = 1,
                    withLabels: Boolean = true)

  private val parser = new scopt.OptionParser[Config]("StopDetection") {
    head("StopDetection", "0.1.0")

    opt[File]('i', "in")
      .required()
      .valueName("<file>")
      .action((x, c) => c.copy(in = x))
      .text("Path to input Parquet file containing the raw data of shape [x(Double), y(Double), z(Double), label(Double)]")

    opt[File]('o', "out")
      .required()
      .valueName("<file>")
      .action((x, c) => c.copy(out = x))
      .text("Path to output Parquet file containing the converted frequency data of shape [features (Vector), label(Double)]")

    opt[Int]('w', "window")
      .required()
      .action((x, c) => c.copy(windowSize = x))
      .text("Size of the window used for conversion, i.e. size of the feature vectors.")

    opt[Unit]("no-labels")
      .action((_, c) => c.copy(withLabels = false))
      .text(
        "whether the dataset contains labels or not, i.e. is just of shape [x, y, z]")
  }
}
