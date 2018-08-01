package eu.qrowd_project.wp6.transportation_mode_learning.scripts.user_studies3

import eu.qrowd_project.wp6.transportation_mode_learning.util.TrackPoint

/**
  * Stage class corresponding to the following SQLite schema:
  *
  * trip
  *
  * 0|trip_id|INTEGER|1||1
  * 1|citizen_id|VARCHAR(255)|1||0
  * 2|start_coordinate|VARCHAR(255)|1||0
  * 3|start_address|VARCHAR(255)|1||0
  * 4|stop_coordinate|VARCHAR(255)|1||0
  * 5|stop_address|VARCHAR(255)|1||0
  * 6|start_timestamp|DATETIME|1||0
  * 7|stop_timestamp|DATETIME|1||0
  * 8|transportation_mode|VARCHAR(255)|0||0
  * 9|segment_confidence|REAL|0||0
  * 10|transportation_confidence|REAL|0||0
  * 11|path|TEXT|0||0
  * 12|multimodal_trip_id|INTEGER|0||0
  *
  * A stage is a sequence of points representing a movement done with one means
  * of transportation, like e.g. a bus trip from a start to a destination stop.
  *
  * TODO: Add support for multimodal_trip_id
  *
  * @param userID       A string containing a users UUID like, e.g. d7a1230d94f91a32cc079809748e52e8a4a6a22f
  * @param start        A TrackPoint object (i.e. something containing long, lat and a
  *                     timestamp) representing the start point of a stage
  * @param startAddress Contains the address of the start point
  * @param stop         A TrackPoint object (i.e. something containing long, lat and a
  *                     timestamp) representing the stop point of a stage
  * @param stopAddress  Contains the address of the stop point
  */
case class UserStudies3Stage(userID: String,
                             mode: String,
                             start: TrackPoint, startAddress: String,
                             stop: TrackPoint, stopAddress: String,
                             segmentConfidence: Double = Double.NaN,
                             modeConfidence: Double = Double.NaN,
                             trajectory: Seq[TrackPoint],
                             var mappedTrajectory: Seq[TrackPoint] = Seq(),
                             parentTrip: Option[Int] = None
                            ) {
  val tripID: Int = (((5 * start.lat) * (7 * start.long) * (11 * stop.lat) * (17 * stop.long)) % Int.MaxValue).toInt
}
