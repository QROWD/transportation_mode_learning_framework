package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import java.time.Duration

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Clusterer
import eu.qrowd_project.wp6.transportation_mode_learning.util.{HaversineDistance, TrackPoint}
import org.apache.commons.math3.ml.clustering.{Clusterable, DBSCANClusterer}
import org.apache.commons.math3.ml.distance.DistanceMeasure

import collection.JavaConverters._

/**
  * ST-DBSCAN implementation which extends DBSCAN by means of temporal data awareness.
  *
  * The rough idea is to do the neighborhood-lookup with an additional filter on the timestamps of the
  *
  * analyzed points.
  *
  * @param eps    maximum radius of the neighborhood to be considered (in km)
  * @param tEps    maximum time distance of the neighborhood to be considered (in seconds)
  * @param minPts minimum number of points needed for a cluster
  * @author Lorenz Buehmann
  */
class STDBSCAN(val eps: Double, val tEps:Double, val minPts: Int) extends DBSCANClusterer[TrackPoint](eps, minPts, HaversineDistance) with Clusterer {
  private val logger = com.typesafe.scalalogging.Logger("STDBSCAN")

  //override def getDistanceMeasure: DistanceMeasure = HaversineDistance
  def this(config: com.typesafe.config.Config) {
    this(
      config.getDouble("eps"),
      config.getDouble("tEps"),
      config.getInt("minPts"))

    logger.debug(s"Clusterer params: eps=$eps\ttEeps=$tEps\tminPts=$minPts")
  }
  override def distance(p1: Clusterable, p2: Clusterable): Double = {
    // we do the time distance check first and return a high value if necessary, otherwise we delegate to the default
    // metric, i.e. Haversine distance
    if(Duration.between(
          p1.asInstanceOf[TrackPoint].timestamp.toInstant,
          p2.asInstanceOf[TrackPoint].timestamp.toInstant).getSeconds > tEps) {
      Double.MaxValue
    }
    else {
      super.distance(p1, p2)
    }
  }

  override def cluster(points: Seq[TrackPoint]): Seq[Seq[TrackPoint]] = {
    val c = cluster(points.asJava)
    c.asScala.map(l => l.getPoints.asScala)
  }
}
