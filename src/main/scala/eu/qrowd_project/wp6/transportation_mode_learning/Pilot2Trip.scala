package eu.qrowd_project.wp6.transportation_mode_learning

import eu.qrowd_project.wp6.transportation_mode_learning.util.{Address, TrackPoint}

case class Pilot2Trip(userID: String, start: TrackPoint, startAddress: Address,
                      stop: TrackPoint, stopAddress: Address) {
  val tripID: Int = (((5 * start.lat) * (7 * start.long) * (11 * stop.lat) * (17 * stop.long)) % Int.MaxValue).toInt
}
