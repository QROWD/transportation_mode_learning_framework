package eu.qrowd_project.wp6.transportation_mode_learning.util

import scala.math._

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.LocationDataAnalyzer.EARTH_RADIUS
import org.apache.commons.math3.ml.distance.DistanceMeasure

/**
  * The Haversine formula determines the great-circle distance between two points on a sphere given their
  * longitudes and latitudes.
  *
  * @author Lorenz Buehmann
  */
object HaversineDistance extends DistanceMeasure {

  /**
    * The Haversine formula determines the great-circle distance between two points on a sphere given their
    * longitudes and latitudes.
    *
    * @param p1 first point with latitude and longitude
    * @param p2 second point with latitude and longitude
    * @return distance returned in km
    */
  def compute[P <: Point](p1: P, p2: P): Double = {
    val dLat = (p2.lat - p1.lat).toRadians
    val dLon = (p2.long - p1.long).toRadians

    val a = pow(sin(dLat / 2), 2) + pow(sin(dLon / 2), 2) * cos(p1.lat.toRadians) * cos(p2.lat.toRadians)
    val c = 2 * asin(sqrt(a))

    EARTH_RADIUS * c
  }

  override def compute(a: Array[Double], b: Array[Double]): Double = compute(Point(a(0), a(1)), Point(b(0), b(1)))
}
