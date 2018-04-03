package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.time.Duration
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.TimeUnit

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

  def haversineDistanceSum[P <: Point](points: Seq[P]): Double =
    points.sliding(2).collect { case Seq(a,b) => HaversineDistance.compute(a, b)}.sum

  /**
    * The time difference in seconds.
    * @param begin
    * @param end
    * @return
    */
  def timeDiff(begin: TrackPoint, end: TrackPoint, unit: TimeUnit = TimeUnit.SECONDS): Long =
    Duration.between(begin.timestamp.toLocalDateTime, end.timestamp.toLocalDateTime).getSeconds
}
