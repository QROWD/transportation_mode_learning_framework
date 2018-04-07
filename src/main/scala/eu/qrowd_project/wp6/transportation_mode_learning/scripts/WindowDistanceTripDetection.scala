package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.sql.Timestamp
import java.time.LocalDateTime

import eu.qrowd_project.wp6.transportation_mode_learning.util.{HaversineDistance, TrackPoint}

import scala.collection.mutable

/**
  * Created by patrick on 4/7/18.
  */
class WindowDistanceTripDetection(windowSize: Int, stepSize: Int,
                                  distanceThresholdInKm: Double,
                                  min_nr_of_segments: Int = 5,
                                  noiseSegments: Int = 4) {
  val logger = com.typesafe.scalalogging.Logger("WindowDistanceTripDetection")
  val secsPerDay: Int = 60 * 60 * 24

  def find(trajectory: Seq[TrackPoint]): Seq[Trip] = {
    if(trajectory.isEmpty) {
      logger.warn("could not perform trip detection: empty trajectory")
      return Seq()
    }

    // sort by time
    val points: Seq[TrackPoint] = trajectory.sortWith(_ < _)

    val bins = mutable.Map[Int, (TrackPoint, TrackPoint)]()
    for (i <- 0 until secsPerDay - windowSize by stepSize) {
      bins.put(i, null)
    }

    var candidateKeys = mutable.ArraySeq[Int]()
    candidateKeys ++= bins.keys

    for (point <- points) {
      val secsOfDay = getSecondsOfDay(point.timestamp)

      /* Since points are sorted by date we can throw away all key candidates
       * that are smaller than the current point's timestamp because they won't
       * be used in the future */
      while (candidateKeys.nonEmpty && candidateKeys.head < secsOfDay) {
        candidateKeys = candidateKeys.tail
      }

      for (k <- candidateKeys.filter(k => k <= secsOfDay && secsOfDay <= (k + windowSize))) {
        val bin = bins(k)
        if (bin == null) {
          bins(k) = (point, point)
        } else {
          if (point < bin._1) {
            // timestamp of the point is earlier than the existing one
            bins(k) = (point, bin._2)
          } else if (point > bin._2) {
            // timestamp of the point is later than the existing one
            bins(k) = (bin._1, point)
          }
        }
      }
    }

    var pointsWithCategories: List[(TrackPoint, Boolean)] = bins.values
      .toList.distinct.filter(_ != null)
      .map(pair => (pair._1, HaversineDistance.compute(pair._1, pair._2) >= distanceThresholdInKm))

    val availableCategoryPoints = pointsWithCategories.map(_._1)

    val tmp: Seq[(TrackPoint, Int)] = points.map(p => {
      if (availableCategoryPoints.contains(p)) {
        // if this point is one of the beginning points of a window...

        val idx = availableCategoryPoints.indexOf(p)
        // ...get the category (i.e. distance in window > distance threshold)
        // from the respective window:
        //    1: distance in window > distance threshold --> a trip segment
        //    0: distance in window <= distance threshold --> not a trip segment
        val isTrip: Int = if (pointsWithCategories(idx)._2) 1 else 0

        (p, isTrip)
      } else {
        // this point is not a beginning point of a window (which can happen
        // since the step size might be greater 1).
        // Here the trip segment/not trip segment flag is set to -1 which means
        // 'don't know'.
        (p, -1)
      }
    })

    var beenWithinTrip: Int = tmp.head._2
    var lastTrip = Seq.empty[TrackPoint]
    var trips = Seq.empty[Trip]
    var noiseSegmentsCounter = 0

    for (pointWithCat <- tmp) {
      val currPoint = pointWithCat._1
      // -1: don't know, i.e. same as previous point, 0: not part of trip,
      // 1: part of trip
      val currPointPartOfTrip = pointWithCat._2

      (beenWithinTrip, currPointPartOfTrip) match {
        case (1, -1) =>
          // still within a trip sequence
          lastTrip = lastTrip ++ Seq(currPoint)
        case (1, 0) =>
          // found the end of a trip or a noise segment
          if (noiseSegmentsCounter <= noiseSegments) {
            // non-trip segment considered as noise
            noiseSegmentsCounter += 1
            lastTrip = lastTrip ++ Seq(currPoint)
          } else {
            // found the end ot a trip
            trips = trips ++ Seq(NonClusterTrip(lastTrip.head, lastTrip.last, lastTrip))
            lastTrip = Seq.empty[TrackPoint]
            beenWithinTrip = 0
            noiseSegmentsCounter = 0
          }
        case (1, 1) =>
          // still within a trip sequence
          lastTrip = lastTrip ++ Seq(currPoint)
        case (0, -1) =>
          // still within a non-trip sequence --> nothing to do
        case (0, 0) =>
          // still within a non-trip sequence --> nothing to do
        case (0, 1) =>
          // found the beginning of a new trip
          lastTrip = Seq(currPoint)
          beenWithinTrip = 1
      }
    }

//    var isTrip = pointsWithCategories.head._2
//    var lastTrip = Seq.empty[TrackPoint]
//    var trips = Seq.empty[Trip]
//
//    for (pointWCat <- pointsWithCategories) {
//      val currPoint = pointWCat._1
//      val currPointPartOfTrip = pointWCat._2
//      (isTrip, currPointPartOfTrip) match {
//        case (true, false) =>
//          // found the end of a trip
//          trips = trips ++ Seq(NonClusterTrip(lastTrip.head, lastTrip.last, lastTrip))
//          lastTrip = Seq.empty[TrackPoint]
//          isTrip = false
//        case (true, true) =>
//          // still within a trip sequence
//          lastTrip = lastTrip ++ Seq(currPoint)
//        case (false, true) =>
//          // found the beginning of a new trip
//          lastTrip = Seq(currPoint)
//          isTrip = true
//        case (false, false) =>
//          // within a non-trip sequence --> nothing to do
//      }
//    }

    trips.filter(_.trace.size >= min_nr_of_segments)
  }

  private def getSecondsOfDay(timestamp: Timestamp): Double = {
    val dateTime = timestamp.toLocalDateTime

    val nanoSecs = dateTime.getNano
    val secs = dateTime.getSecond
    val mins = dateTime.getMinute
    val hours = dateTime.getHour

    (hours * 60 * 60) + (mins * 60) + secs + (nanoSecs / 1000000000.0)
  }

}

object WindowDistanceTripDetection {
  def main(args: Array[String]): Unit = {
    val detector = new WindowDistanceTripDetection(300, 60, 0.17)
    detector.getSecondsOfDay(Timestamp.valueOf(LocalDateTime.now()))
  }
}