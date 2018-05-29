package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.sql.{Connection, DriverManager, ResultSet}
import java.time.format.DateTimeFormatter

import eu.qrowd_project.wp6.transportation_mode_learning.Pilot2Stage

import scala.collection.JavaConversions._

trait SQLiteAccess2ndPilot {
  private val logger = com.typesafe.scalalogging.Logger("SQLite DB Writer")

  Class.forName("org.sqlite.JDBC")
  var connection: Connection = null

  def connect(dbFilePath: String): Unit = {
    connection = DriverManager.getConnection(s"jdbc:sqlite:$dbFilePath")
  }

  def close(): Unit = {
    if (connection != null && !connection.isClosed) {
      connection.close()
    }
  }

  def runQuery(queryStr: String): ResultSet = {
    connection.createStatement().executeQuery(queryStr)
  }

  def getCitizenCollectionMode(uid: String): String = {
    val queryStr = s"SELECT collection_mode FROM citizen WHERE citizen_id='$uid';"

    logger.info("Running query\n" + queryStr)
    val res = runQuery(queryStr)

    if (res.next()) {
      res.getString("collection_mode")
    } else {
      throw new RuntimeException(s"No collection mode set for user $uid")
    }
  }

  val dateTimeFormatter = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss")

  def writeTripInfo(trip: Pilot2Stage): Unit = {
    // long, lat
    val queryStr =
      s"""
         |INSERT INTO trip(
         |  trip_id,
         |  citizen_id,
         |  start_coordinate,
         |  start_timestamp,
         |  start_address,
         |  stop_coordinate,
         |  stop_timestamp,
         |  stop_address
         |)
         |VALUES (
         |  ${trip.tripID},
         |  '${trip.userID}',
         |  '[${trip.start.lat},${trip.start.long}]',
         |  '${trip.start.timestamp.toLocalDateTime.format(dateTimeFormatter)}',
         |  '${trip.startAddress.label.replace("'", "")}',
         |  '${trip.stop.long}, ${trip.stop.lat}',
         |  '${trip.stop.timestamp.toLocalDateTime..format(dateTimeFormatter)}',
         |  '${trip.stopAddress.label.replace("'", "")}'
         |);
       """.stripMargin

    logger.info("Running query\n" + queryStr)
    connection.createStatement().execute(queryStr)
  }
}
