package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import com.typesafe.config.{Config, ConfigFactory}
import eu.qrowd_project.wp6.transportation_mode_learning.mapmatching.BarefootMapMatcherSocket
import eu.qrowd_project.wp6.transportation_mode_learning.util.{BarefootJSONConverter, HaversineDistance, POIRetrieval, TrackPoint}

import collection.JavaConverters._

/**
  * Basically, uses the stop point detection to perform trip detection given a GPS trajectory.
  *
  * @author Lorenz Buehmann
  */
class ClusterBasedTripDetection(val userIDindex: Int = -1,
                     val useMapMatching: Boolean = false,
                     val config: Config = ConfigFactory.defaultApplication()) extends TripDetection {

  val logger = com.typesafe.scalalogging.Logger("TripDetection")

  // FIXME: Move to config file (map matching not used, yet)
  lazy val mapMatcher = new BarefootMapMatcherSocket(host = "127.0.0.1", port = 1234)

  override def find(trajectory: Seq[TrackPoint]): Seq[ClusterTrip] = {

    if (trajectory.isEmpty) {
      logger.warn(s"${userIDindex} : could not perform trip detection: empty trajectory")
      return Seq()
    }

    // sort by time
    val points = trajectory.sortWith(_ < _)

    // compute stop point clusters
    val stopClusters = StopDetection(config.withOnlyPath("stop_detection")).find(points)
    logger.info(s"${userIDindex} : #stop clusters:${stopClusters.size}")

    // keep first and last point of each cluster
    val stopsStartEnd = stopClusters.map(c => (c.head, c.last, c))

    if (stopClusters.nonEmpty) {
      // the stop clusters are in ascending order w.r.t. time, i.e. we assume trips to be happen
      // between successive points
      // we keep the last point of the start cluster and the first point of the end cluster
      val trips: Seq[ClusterTrip] = stopsStartEnd
        .sliding(2)
        .filter(_.size == 2)
        .map(e => {
          if (Thread.currentThread().isInterrupted) {
            throw new InterruptedException
          }
          // last point of first stop
          val begin = e(0)._2 // firstStop._2

          // first point of second stop
          val end = e(1)._1 // secondStop._1
          // entries in between
          val entries = points.filter(p => p.timestamp.after(begin.timestamp) && p.timestamp.before(end.timestamp))
          val trace = Seq(begin) ++ entries ++ Seq(end)
          val beginCluster: Seq[TrackPoint] = e(0)._3
          val endCluster: Seq[TrackPoint] = e(1)._3

          ClusterTrip(begin, end, trace, beginCluster, endCluster)
        }).toSeq

//      // perform map matching
//      if (useMapMatching) {
//        trips.map(trip => {
//          val response = mapMatcher.query(BarefootJSONConverter.convert(trip.trace).toString)
//
//          // TODO: finish
//          println(response)
//        })
//      }

      trips.filter(_.averageSpeed > config.getDouble("stop_detection.clustering.min_avg_speed"))

    } else {
      Seq(ClusterTrip(
        trajectory.head,
        trajectory.last,
        trajectory,
        Seq.empty[TrackPoint], Seq.empty[TrackPoint])).filter(
          _.averageSpeed > config.getDouble("stop_detection.clustering.min_avg_speed"))
    }
  }

  override def getSettings: Map[String, String] = {
    Map[String, String](
      "tripDetectionType" -> "ClusterBasedTripDetection",
      "useMapMatching" -> useMapMatching.toString,
      "clusterer" -> "tdbscan",
      "cEps" -> config.getDouble("stop_detection.clustering.tdbscan.cEps").toString,
      "eps" -> config.getDouble("stop_detection.clustering.tdbscan.eps").toString,
      "minPts" -> config.getInt("stop_detection.clustering.tdbscan.minPts").toString
    )
  }
}

sealed abstract class Trip(
                            val start: TrackPoint,
                            val end: TrackPoint,
                            val trace: Seq[TrackPoint]) {
  def averageSpeed: Double = {
    if (trace.nonEmpty) {
      trace.sliding(2)
        .map(pair => HaversineDistance.compute(pair.head, pair.last) /
          ((pair.last.timestamp.getTime - pair.head.timestamp.getTime) / 1000.0 / 60 / 60))
        .foldLeft((0.0, 1))((acc, i) => ((acc._1 + (i - acc._1) / acc._2), acc._2 + 1))._1
    } else {
      0
    }
  }
}

case class NonClusterTrip(
                           override val start: TrackPoint,
                           override val end: TrackPoint,
                           override val trace: Seq[TrackPoint]) extends Trip(start, end, trace)

case class ClusterTrip(
                        override val start: TrackPoint,
                        override val end: TrackPoint,
                        override val trace: Seq[TrackPoint],
                        startCluster: Seq[TrackPoint] = Seq(),
                        endCluster: Seq[TrackPoint]) extends Trip(start, end, trace)
