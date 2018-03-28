package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import eu.qrowd_project.wp6.transportation_mode_learning.util.{HaversineDistance, Point}
import org.apache.commons.math3.ml.clustering.DBSCANClusterer
import org.apache.commons.math3.ml.distance.DistanceMeasure

/**
  * Perform clustering on geospatial data, i.e. the input are GPS points with timestamps attached.
  *
  * @param eps    maximum radius of the neighborhood to be considered
  * @param minPts minimum number of points needed for a cluster
  * @author Lorenz Buehmann
  */
class GeoSpatialClustering[P <: Point](val eps: Double, val minPts: Int) extends DBSCANClusterer[P](eps, minPts) {


  override def getDistanceMeasure: DistanceMeasure = HaversineDistance
}
