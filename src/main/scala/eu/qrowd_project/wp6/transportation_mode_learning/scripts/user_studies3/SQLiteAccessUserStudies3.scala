package eu.qrowd_project.wp6.transportation_mode_learning.scripts.user_studies3

import eu.qrowd_project.wp6.transportation_mode_learning.util.{Point, SQLiteAccess, TrackPoint}

trait SQLiteAccessUserStudies3 extends SQLiteAccess {
  private val logger = com.typesafe.scalalogging.Logger("SQLite DB Writer")

  def getCitizenCollectionMode(uid: String): String = {
    val queryStr = s"SELECT collection_mode FROM citizen WHERE citizen_id='$uid';"

    logger.info(s"Running query\n$queryStr")
    val res = runQuery(queryStr)

    if (res.next()) {
      res.getString("collection_mode")
    } else {
      throw new RuntimeException(s"No collection mode set for user $uid")
    }
  }

  def writeTripInfo(trip: UserStudies3Stage): Unit = {
    val wholeTrajectory = Seq(trip.start) ++ trip.trajectory ++ Seq(trip.stop)
    val jsonPointsStr = asJSonArray(wholeTrajectory)
    val jsonMappedPointsStr = asJSonArray(trip.mappedTrajectory)
    val queryStr =
      s"""
         |INSERT INTO trip(
         |  citizen_id,
         |  start_coordinate,
         |  start_timestamp,
         |  start_address,
         |  stop_coordinate,
         |  stop_timestamp,
         |  stop_address,
         |  transportation_mode,
         |  path,
         |  path_mapped
         |)
         |VALUES (
         |  '${trip.userID}',
         |  '[${trip.start.lat},${trip.start.long}]',
         |  '${trip.start.timestamp.toLocalDateTime.format(dateTimeFormatter)}',
         |  '${trip.startAddress.replace("'", "")}',
         |  '[${trip.stop.lat},${trip.stop.long}]',
         |  '${trip.stop.timestamp.toLocalDateTime.format(dateTimeFormatter)}',
         |  '${trip.stopAddress.replace("'", "")}',
         |  '${trip.mode}',
         |  '$jsonPointsStr',
         |  '$jsonMappedPointsStr'
         |);
       """.stripMargin

    logger.info(s"Running query\n$queryStr")
    connection.createStatement().execute(queryStr)
  }

  private def asJSonArray[T <: Point](points: Seq[T]): String = {
    s"[ " + points.map(p => s"[${p.long},${p.lat}]").mkString(", ") + " ]"
  }
}
