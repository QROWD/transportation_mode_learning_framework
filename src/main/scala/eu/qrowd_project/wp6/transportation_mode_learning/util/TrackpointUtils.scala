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
    * @param start the start point
    * @param end   the end point
    * @return bearing in degrees
    */
  def bearing(start: TrackPoint, end: TrackPoint): Double = {
    val y = Math.sin(end.long - start.long) * Math.cos(end.lat)
    val x = Math.cos(start.lat) * Math.sin(end.lat) -
      Math.sin(start.lat) * Math.cos(end.lat) * Math.cos(end.long - start.long)
    Math.atan2(y, x).toDegrees
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
}
