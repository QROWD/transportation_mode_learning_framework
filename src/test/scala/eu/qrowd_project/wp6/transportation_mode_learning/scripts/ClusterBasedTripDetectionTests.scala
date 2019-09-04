package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.sql.Timestamp

import eu.qrowd_project.wp6.transportation_mode_learning.util.{HaversineDistance, TrackPoint}
import org.scalatest.FunSuite

class ClusterBasedTripDetectionTests extends FunSuite {
  test("A trips average speed should be computed correctly (no movement)") {
    val start = TrackPoint(12.34, 23.45, new Timestamp(1550000000000L))
    val stop = TrackPoint(12.34, 23.45, new Timestamp(1550000100000L))
    val trip = ClusterTrip(
      start,
      stop,
      Seq(start, stop),
      null,
      null
    )
    Seq(start, stop).sliding(2)
      .map(pair => HaversineDistance.compute(pair.head, pair.last) /
        ((pair.last.timestamp.getTime - pair.head.timestamp.getTime) / 10000.0 / 60 / 60))
        .foreach(println)
//    println(trip)
    assert(trip.averageSpeed == 0)
  }

  test("A trips average speed should be computed correctly") {
    val oneHour = 3600000L
    val startTimestamp = 1550000000000L
    val start = TrackPoint(12.34, 23.45, new Timestamp(startTimestamp))
    // ~11 km moved
    val point2 = TrackPoint(12.34, 23.55, new Timestamp(startTimestamp + oneHour))
    // ~22 km moved
    val point3 = TrackPoint(12.34, 23.75, new Timestamp(startTimestamp + (2*oneHour)))
    // ~5 km moved
    val point4 = TrackPoint(12.34, 23.8, new Timestamp(startTimestamp + (3*oneHour)))
    // ~11 km moved
    val stop = TrackPoint(12.34, 23.9, new Timestamp(startTimestamp + (4*oneHour)))
    // total: ~50 km moved in 4 hours --> ~12.5 km/h

    val trip = ClusterTrip(
      start,
      stop,
      Seq(start, point2, point3, point4, stop),
      null,
      null
    )
    val avgSpeed = trip.averageSpeed
    assert(12.0 < avgSpeed && avgSpeed < 12.5)
  }
}
