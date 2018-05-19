package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.time.Duration

/**
  * @author Lorenz Buehmann
  */
object TrackpointUtils {

  /**
    * Computes the avg. speed between both points in km/h
    *
    * @param p1 the start point
    * @param p2 the end point
    * @return avg. speed in km/h
    */
  def avgSpeed(p1: TrackPoint, p2: TrackPoint): Double = {
    val distanceInMeters = HaversineDistance.compute(p1, p2) * 1000
    val diffSeconds = timeDiff(p1, p2)

    val metersPerSecond = if (diffSeconds == 0) 0
    else distanceInMeters.toDouble / diffSeconds

    metersPerSecond * 3.6
  }

  /**
    * Computes the avg. speed taken to pass through all points in km/h
    *
    * @param points the GPS points of the trip
    * @return avg. speed in km/h
    */
  def avgSpeed(points: Seq[TrackPoint]): Double = {
    val distanceInMeters = haversineDistanceSum(points) * 1000
    val diffSeconds = timeDiff(points.head, points.last)

    val metersPerSecond = if (diffSeconds == 0) 0
    else distanceInMeters.toDouble / diffSeconds

    metersPerSecond * 3.6
  }

  /**
    * This formula is for the initial bearing (sometimes referred to as forward azimuth) which if followed in a
    * straight line along a great-circle arc will take you from the start point to the end point.
    *
    * Bearing would be measured from North direction i.e 0° bearing means North, 90° bearing is East,
    * 180° bearing is measured to be South, and 270° to be West.
    * @param start the start point
    * @param end   the end point
    * @return bearing in degrees
    */
  def bearing(start: Point, end: Point): Double = {
    val lat1 = start.lat.toRadians
    val lat2 = end.lat.toRadians
    val dLong = (end.long - start.long).toRadians
    val y = Math.sin(dLong) * Math.cos(lat2)
    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLong)
    val brng = Math.atan2(y, x).toDegrees

    // since atan2 returns values in the range (-180° ... +180°),
    // to normalise the result to a compass bearing (in the range 0° ... 360°)
    (brng + 360) % 360
  }

  def haversineDistanceSum[P <: Point](points: Seq[P]): Double =
    points.sliding(2).collect { case Seq(a, b) => HaversineDistance.compute(a, b) }.sum

  /**
    * The time difference in seconds between start and end point.
    *
    * @param start the start point
    * @param end   the end point
    * @return time difference ins seconds
    */
  def timeDiff(start: TrackPoint, end: TrackPoint): Long =
    Duration.between(start.timestamp.toLocalDateTime, end.timestamp.toLocalDateTime).getSeconds

  /**
    * The velocity change rate (VCR), is a concept different from acceleration. It is a
    * statistical indicator that takes into account the magnitude of the velocity, i.e. a
    * change of 20 km/h from 100 km/h is different from the same change from a speed of 30 km/h.
    * VCR is calculated by dividing the change in velocity by the velocity of the first point.
    *
    * @param p1
    * @param p2
    * @param p3
    * @return velocity change rate
    */
  def velocityChangeRate(p1: TrackPoint, p2: TrackPoint, p3: TrackPoint): Double = {
    val v1 = avgSpeed(p2, p1)
    val v2 = avgSpeed(p3, p2)

    velocityChangeRate(v1, v2)
  }

  /**
    * The velocity change rate (VCR), is a concept different from acceleration. It is a
    * statistical indicator that takes into account the magnitude of the velocity, i.e. a
    * change of 20 km/h from 100 km/h is different from the same change from a speed of 30 km/h.
    * VCR is calculated by dividing the change in velocity by the velocity of the first point.
    *
    * @param v1 velocity of first point
    * @param v2 velocity of second point
    * @return velocity change rate
    */
  def velocityChangeRate(v1: Double, v2: Double): Double = {
    if(v1 == 0) 0
    else (v2 - v1) / v1
  }

  /**
    * Computes a new point from the given point.
    *
    * @param tp the start point
    * @param bearing the bearing in rad
    * @param distanceKm the distance in km
    * @param R the earth radius
    * @return the new point
    */
  def pointFrom(tp: Point, bearing: Double, distanceKm: Double, R: Double = 6378.1): Point = {
    val lat1 = Math.toRadians(tp.lat)
    val lon1 = Math.toRadians(tp.long)

    val lat2 = Math.asin(Math.sin(lat1) * Math.cos(distanceKm / R) +
      Math.cos(lat1) * Math.sin(distanceKm / R) * Math.cos(bearing.toRadians))

    val lon2 = lon1 + Math.atan2(Math.sin(bearing.toRadians) * Math.sin(distanceKm / R) * Math.cos(lat1),
      Math.cos(distanceKm / R) - Math.sin(lat1) * Math.sin(lat2))

    Point(Math.toDegrees(lat2), Math.toDegrees(lon2))
  }

  def main(args: Array[String]): Unit = {
    println(TrackpointUtils.bearing(Point(39.099912, -94.581213), Point(38.627089, -90.200203)))
    println(TrackpointUtils.bearing(Point(46.09043, 11.10995), Point(46.09118, 11.10932)))

    println(TrackpointUtils.pointFrom(Point(46.09043, 11.10995), 329.77, 0.04))
  }
}
