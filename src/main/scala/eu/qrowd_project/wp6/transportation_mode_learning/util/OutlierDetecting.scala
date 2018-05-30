package eu.qrowd_project.wp6.transportation_mode_learning.util

/**
  * Created by patrick on 5/29/18.
  */
trait OutlierDetecting {
  def detectOutliers(trajectory: Seq[TrackPoint]): Seq[TrackPoint] = {
    if (trajectory.size >= 3) {
      val outliers = trajectory.sliding(3).flatMap {
        case Seq(a, b, c) =>
          val v1 = TrackpointUtils.avgSpeed(a, b)
          val v2 = TrackpointUtils.avgSpeed(b, c)

          val d12 = HaversineDistance.compute(a, b)
          val d23 = HaversineDistance.compute(b, c)
          val d13 = HaversineDistance.compute(a, c)

          val b1 = TrackpointUtils.bearing(a, b)
          val b2 = TrackpointUtils.bearing(b, c)

          val t13 = TrackpointUtils.timeDiff(a, c)

          val tv = 30
          val td = 0.1
          val eps_b = 5.0
          val eps_t = 90L // time diff between a and c

          if((Math.abs(180 - Math.abs(b1 - b2)) <= eps_b && t13 <= eps_t && d12 >= 0.1)
            || d13 <= td && v1 >= tv && v2 >= tv) {
            Some(b)
          } else {
            None
          }
        case _ => None
      }.toSeq

      outliers
    } else {
      Seq.empty[TrackPoint]
    }
  }
}
