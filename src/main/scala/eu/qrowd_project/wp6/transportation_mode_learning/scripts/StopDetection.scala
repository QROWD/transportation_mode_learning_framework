package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.{File, PrintWriter}
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util
import java.util.UUID

import eu.qrowd_project.wp6.transportation_mode_learning.preprocessing.{STDBSCAN, TDBSCAN, TDBSCAN2}
import eu.qrowd_project.wp6.transportation_mode_learning.util.TrackPoint
import org.apache.commons.math3.ml.clustering.Cluster
import org.joda.time.format.DateTimeParser

import scala.io.Source
import scala.util.matching.Regex
import collection.JavaConverters._

/**
  * Determine stops given a GPS trajectory, i.e. a sequence of GPS points tagged with a timestamp.
  *
  * Right now, we perform spatio-temporal clustering to solve this task.
  *
  * @param clusterer the cluster computing algorithm
  * @author Lorenz Buehmann
  */
class StopDetection(val clusterer: Clusterer) {

  /**
    * Determine stops given a GPS trajectory, i.e. a sequence of GPS points tagged with a timestamp.
    *
    * @param ceps   the distance range to ensure that the points comprising a stop are of state continuity (in km)
    * @param eps    the search radius for identifying density-based neighborhood (in km)
    * @param minPts the minimum number of neighboring points to identify a core point
    */
  def this(ceps: Double = 0.3,
           teps: Double = 300,
           eps: Double = 0.1,
           minPts: Int = 80,
           mode: String = "stdbscan") {
    this(if (mode == "stdbscan") {
      new STDBSCAN(eps, teps, minPts)
    } else {
      new TDBSCAN2(ceps, eps, minPts)
    })
  }

  /**
    * Determine stops given a GPS trajectory, i.e. a sequence of GPS points tagged with a timestamp.
    *
    * @param config parent element must contain path to cluster implementation, i.e.
    *               `stop_detection.clustering.tdbscan`
    */
  def this(config: com.typesafe.config.Config) {
    this(if (config.getString("stop_detection.clustering.mode") == "stdbscan") {
      new STDBSCAN(config.getConfig("stop_detection.clustering.stdbscan"))
    } else {
      new TDBSCAN2(config.getConfig("stop_detection.clustering.tdbscan"))
    })
  }

  /**
    * Find stop points given a set of time-tagged GPS points P.
    * The result are sequences of GPS points that denote a stop point.
    *
    * @param  points the sequence of GPS points
    * @return sequences of GPS points that denote a stop point
    */
  def find(points: Seq[TrackPoint]): Seq[Seq[TrackPoint]] = {
    //    println("ST-DBSCAN:" + new STDBSCAN(eps, 60, minPts).cluster(points.asJava).asScala.map(c => c.getPoints).mkString("\n"))
    clusterer
      .cluster(points)
    //      .filter(_.size >= minPts)
  }
}

object StopDetection {


  def apply(ceps: Double = 0.3,            teps: Double = 300,
            eps: Double = 0.1, minPts: Int = 80, mode: String = "stdbscan"): StopDetection = new StopDetection(ceps, teps, eps, minPts, mode)

  def apply(config: com.typesafe.config.Config): StopDetection = new StopDetection(config)

  def main(args: Array[String]): Unit = {
    val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    val points : Seq[TrackPoint] = Source.fromFile(args(0)).getLines().map(line => {
      //         2019-09-28 00:00:06.549, (46.12807, 11.11958)
      val ret: Option[Regex.Match] = "(\\S+ \\S+), \\((\\S+), (\\S+)\\)".r.findFirstMatchIn(line)

      if (ret.isEmpty) {
        null
      } else {
        val v = ret.get
        new TrackPoint(v.group(2).toFloat, v.group(3).toFloat, Timestamp.valueOf(LocalDateTime.parse(v.group(1).padTo(23, '0'), dtf)) )
      }
    }).filterNot(_ == null).toSeq
    val seq = StopDetection().find(points)
    println(seq)
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
