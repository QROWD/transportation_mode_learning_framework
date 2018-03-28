package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import java.time.Duration

import eu.qrowd_project.wp6.transportation_mode_learning.util.{
  HaversineDistance,
  TrackPoint
}
import util.control.Breaks._

/**
  * Resampling points in the feature based on sampling time and distance
  *
  * @param minimumDistance minimum distance in metres
  * @param minimumTime     time interval in seconds between successive coordinates
  * @param maximumPoints   maximum number of points
  * @author Lorenz Buehmann
  */
class GPSTrajectoryTidy(val minimumDistance: Int = 10,
                        val minimumTime: Int = 5,
                        val maximumPoints: Int = 100) {

  def tidy(points: Seq[TrackPoint]): Seq[TrackPoint] = {
    var tidyPoints = Seq[TrackPoint]()

    for (i <- points.indices) {
      breakable {
        // Add first and last points
        if (i == 0 || i == points.size) {
          tidyPoints :+= points(i)
          break
        }

        val p1 = points(i)
        val p2 = points(i + 1)

        // calculate distance between successive points in metres
        val dx = HaversineDistance.compute(p1, p2) * 1000

        // skip point if its too close to each other
        if (dx < minimumDistance) break

        // calculate sampling time difference between successive points in seconds
        val tx = Duration
          .between(p1.timestamp.toInstant, p2.timestamp.toInstant)
          .getSeconds

        // skip point if sampled to close to each other
        if (tx < minimumTime) break

        tidyPoints :+= p1

      }
    }

    tidyPoints
  }

}

object GPSTrajectoryTidy {
  def apply(minimumDistance: Int = 10,
            minimumTime: Int = 5,
            maximumPoints: Int = 100): GPSTrajectoryTidy =
    new GPSTrajectoryTidy(minimumDistance, minimumTime, maximumPoints)

}
