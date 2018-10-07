package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.sql.Timestamp
import java.time.LocalDateTime

import eu.qrowd_project.wp6.transportation_mode_learning.util.{HaversineDistance, TrackPoint}

import scala.collection.mutable

/**
  * Created by patrick on 4/7/18.
  */
class WindowDistanceBasedTripDetection(windowSize: Int, stepSize: Int,
                                       distanceThresholdInKm: Double,
                                       minNrOfSegments: Int,
                                       noiseSegments: Int) extends TripDetection {
  val logger = com.typesafe.scalalogging.Logger("WindowDistanceTripDetection")
  val secsPerDay: Int = 60 * 60 * 24

  def secsOfDayToDate(secs: Int): LocalDateTime = {
    var secsOfDay = secs
    val year = 2018
    val month = 6
    val day = 3
    val hour = Math.abs(secsOfDay / 60 / 60)
    secsOfDay -= hour * 60 * 60
    val min = Math.abs(secsOfDay / 60)
    secsOfDay -= min * 60

    LocalDateTime.of(year, month, day, hour, min, secsOfDay)
  }

  override def find(trajectory: Seq[TrackPoint]): Seq[Trip] = {
    if(trajectory.isEmpty) {
      logger.warn("could not perform trip detection: empty trajectory")
      return Seq()
    }

    // sort by time
    val points: Seq[TrackPoint] = trajectory.sortWith(_ < _)

    //                      timestamp, (start point, stop point)
    val windows = mutable.Map[Int, (TrackPoint, TrackPoint)]()
    for (i <- 0 until secsPerDay - windowSize by stepSize) {
      windows.put(i, null)
    }

    var candidateKeys = mutable.ArraySeq[Int]()
    candidateKeys ++= windows.keys

    for (point <- points) {
      val secsOfDay = getSecondsOfDay(point.timestamp)

      /* Since points are sorted by date we can throw away all key candidates
       * that are smaller than the current point's timestamp because they won't
       * be used in the future */
      while (candidateKeys.nonEmpty && candidateKeys.head < secsOfDay) {
        candidateKeys = candidateKeys.tail
      }

      for (k <- candidateKeys.filter(k => k <= secsOfDay && secsOfDay <= (k + windowSize))) {
        val window = windows(k)
        if (window == null) {
          windows(k) = (point, point)
        } else {
          if (point < window._1) {
            // timestamp of the point is earlier than the existing one
            windows(k) = (point, window._2)
          } else if (point > window._2) {
            // timestamp of the point is later than the existing one
            windows(k) = (window._1, point)
          }
        }
      }
    }

    val keys: List[Int] = windows.keys.toList.sorted

//    keys.foreach(k => {
////    windows.foreach(win => {
//      val window = windows(k)
//      val startDate: LocalDateTime = secsOfDayToDate(k)
//      val stopDate: LocalDateTime = secsOfDayToDate(k + windowSize)
//      println("------------------------------------------")
//      println(s"Considering time window from ${startDate.toString} to ${stopDate.toString}")
//
//      if (window != null) {
//        val startPoint = window._1
//        val stopPoint = window._2
//        println(s"Start point of this window is ${startPoint.toString}")
//        println(s"Stop point of this window is ${stopPoint.toString}")
//
//        println(s"The distance between both points is ${HaversineDistance.compute(startPoint, stopPoint)}")
//        println(s"This is greater than the distance threshold of $distanceThresholdInKm: " +
//          s"${HaversineDistance.compute(startPoint, stopPoint) >= distanceThresholdInKm}")
//      } else {
//        println(s"No points in this window")
//      }
//    })

    var windowStartPointsWithCategories: List[(TrackPoint, Boolean)] = windows.values
      .toList.distinct.filter(_ != null)
      .map(startEndPair => (
          startEndPair._1,
          HaversineDistance.compute(startEndPair._1, startEndPair._2) >= distanceThresholdInKm
        )
      )

    val availableCategoryPoints = windowStartPointsWithCategories.map(_._1)

    val pointsWithCategories: Seq[(TrackPoint, Int)] = points.map(p => {
      if (availableCategoryPoints.contains(p)) {
        // if this point is one of the start points of a window...

        val idx = availableCategoryPoints.indexOf(p)
        // ...get the category (i.e. distance in window > distance threshold)
        // from the respective window:
        //    1: distance in window > distance threshold --> a trip segment
        //    0: distance in window <= distance threshold --> not a trip segment
        val isTrip: Int = if (windowStartPointsWithCategories(idx)._2) 1 else 0

        (p, isTrip)
      } else {
        // this point is not a start point of a window (which can happen
        // since the step size might be greater 1 and points have a nanosecond
        // accuracy).
        // Here the trip segment/not trip segment flag is set to -1 which means
        // 'don't know'.
        (p, -1)
      }
    })

    var beenWithinTrip: Int = pointsWithCategories.head._2
    var lastTrip = Seq.empty[TrackPoint]
    var trips = Seq.empty[Trip]
    var noiseSegmentsCounter = 0

    for (pointWithCat <- pointsWithCategories) {
      val currPoint = pointWithCat._1

      // -1: don't know, i.e. same as previous point, 0: not part of trip,
      // 1: part of trip
      val currPointIsPartOfTrip = pointWithCat._2

      (beenWithinTrip, currPointIsPartOfTrip) match {
        case (1, -1) =>
          // still within a trip sequence
          lastTrip = lastTrip ++ Seq(currPoint)
        case (1, 0) =>
          // found the end of a trip or a noise segment
          if (noiseSegmentsCounter <= noiseSegments) {
            // non-trip segment considered as noise --> still within trip
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

    trips.filter(_.trace.size > minNrOfSegments)
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

object WindowDistanceBasedTripDetection {
  def main(args: Array[String]): Unit = {
    val detector = new WindowDistanceBasedTripDetection(300, 60, 0.17, 8, 5)
    detector.getSecondsOfDay(Timestamp.valueOf(LocalDateTime.now()))
  }
}