package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.sql.{Connection, DriverManager, ResultSet, Timestamp}
import java.time.format.DateTimeFormatter

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.ModePredictionPilot2.UserID
import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Trip
import eu.qrowd_project.wp6.transportation_mode_learning.user.ILogUsageMode.ILogUsageMode
import eu.qrowd_project.wp6.transportation_mode_learning.user.{ILogUsageMode, ILogUsageModeUnknownException}
import eu.qrowd_project.wp6.transportation_mode_learning.util.SQLiteDB.SQLiteDB
import eu.qrowd_project.wp6.transportation_mode_learning.{Pilot2Stage, Pilot4Stage}


class SQLiteAcces {
  private val logger = com.typesafe.scalalogging.Logger("SQLite DB Writer")

  Class.forName("org.sqlite.JDBC")
  private var stageConnection: Connection = null
  private var tripConnection: Connection = null

  private val citizenInfoDB = SQLiteDB.Stages
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss")

  def connect(tripSQLiteFilePath: String, stageSQLiteFilePath: String): Unit = {
    stageConnection = DriverManager.getConnection(s"jdbc:sqlite:$stageSQLiteFilePath")
    tripConnection = DriverManager.getConnection(s"jdbc:sqlite:$tripSQLiteFilePath")
  }

  def installSchema(): Unit = {
    val schema =
      """
        |CREATE TABLE IF NOT EXISTS "citizen" ("citizen_id" VARCHAR(255) NOT NULL PRIMARY KEY);
        |CREATE TABLE IF NOT EXISTS "trip" ("trip_id" INTEGER NOT NULL PRIMARY KEY, "citizen_id" VARCHAR(255) NOT NULL, "start_coordinate" VARCHAR(255) NOT NULL, "start_address" VARCHAR(255) NOT NULL, "stop_coordinate" VARCHAR(255) NOT NULL, "stop_address" VARCHAR(255) NOT NULL, "start_timestamp" DATETIME NOT NULL, "stop_timestamp" DATETIME NOT NULL, "transportation_mode" VARCHAR(255), "segment_confidence" REAL, "transportation_confidence" REAL, "path" TEXT, "json_file_path" TEXT, FOREIGN KEY ("citizen_id") REFERENCES "citizen" ("citizen_id"));
        |CREATE INDEX IF NOT EXISTS "trip_citizen_id" ON "trip" ("citizen_id");
        |CREATE TABLE IF NOT EXISTS "question" ("question_id" INTEGER NOT NULL PRIMARY KEY, "citizen_id" VARCHAR(255) NOT NULL, "task_id" VARCHAR(255) NOT NULL, "trip_id" INTEGER NOT NULL, "question_json" TEXT NOT NULL, "update_url" VARCHAR(255) NOT NULL, "answer_file_path" TEXT, "diff_file_path" TEXT, "answer_json" TEXT, "answer_code" VARCHAR(255), FOREIGN KEY ("citizen_id") REFERENCES "citizen" ("citizen_id"), FOREIGN KEY ("trip_id") REFERENCES "trip" ("trip_id"));
        |CREATE INDEX IF NOT EXISTS "question_citizen_id" ON "question" ("citizen_id");
        |CREATE INDEX IF NOT EXISTS "question_trip_id" ON "question" ("trip_id");
        |CREATE TABLE IF NOT EXISTS "questionfailsafe" ("question_id" INTEGER NOT NULL PRIMARY KEY, "citizen_id" VARCHAR(255) NOT NULL, "task_id" VARCHAR(255) NOT NULL, "date" DATETIME NOT NULL, "file_paths" TEXT, "answer_json" TEXT, FOREIGN KEY ("citizen_id") REFERENCES "citizen" ("citizen_id"));
        |CREATE INDEX IF NOT EXISTS "questionfailsafe_citizen_id" ON "questionfailsafe" ("citizen_id");
      """.stripMargin.trim.split(";(\n|$)")

    schema.map(z => stageConnection.createStatement().execute(z))
  }

  def close(): Unit = {
    for (connection <- Seq(stageConnection, tripConnection)) {
      if (connection != null && !connection.isClosed) {
        connection.close()
      }
    }
  }

  def getAllUsers(): Set[String] = {
    val queryStr = "SELECT citizen_id FROM citizen"

    var users = Set.empty[String]
    val res = executeQuery(SQLiteDB.Stages, queryStr)
    while (res.next()) {
      users += res.getString("citizen_id")
    }

    users
  }

  def getUsersWithDataForDate(dateString: String): Set[String] = {
    val queryStr =
      s"SELECT DISTINCT citizen_id " +
      s"FROM trip " +
      s"WHERE strftime('%Y%m%d', start_timestamp)='$dateString'"

    var users = Set.empty[String]
    val res = executeQuery(SQLiteDB.Stages, queryStr)
    while (res.next()) {
      users += res.getString("citizen_id")
    }

    users
  }

  def executeQuery(db: SQLiteDB, queryStr: String): ResultSet = {
    db match {
      case SQLiteDB.Stages => stageConnection.createStatement().executeQuery(queryStr)
      case SQLiteDB.Trips => tripConnection.createStatement().executeQuery(queryStr)
    }
  }

  def getCitizenCollectionMode(uid: String): ILogUsageMode = {
    val queryStr = s"SELECT collection_mode FROM citizen WHERE citizen_id='$uid';"

    logger.debug("Running query\n" + queryStr)
    val res = executeQuery(citizenInfoDB, queryStr)

    if (res.next()) {
      val modeString = res.getString("collection_mode")
      modeString match {
        case "CONTINUOUS" => ILogUsageMode.Continuous
        case "ON-OFF" => ILogUsageMode.OnOff
        case _ => throw new ILogUsageModeUnknownException
      }
    } else {
      throw new RuntimeException(s"No collection mode set for user $uid")
    }
  }

  def getLastIndex(db: SQLiteDB): Long = {
    val queryStr = "SELECT last_insert_rowid();"
    val statement = tripConnection.createStatement()
    val res = statement.execute(queryStr)
    val keys = statement.getGeneratedKeys

    keys.getLong(1)
  }

  /**
    * trip table
    *
    *    0|trip_id|INTEGER|1||1
    *    1|citizen_id|VARCHAR(255)|1||0
    *    2|start_coordinate|VARCHAR(255)|1||0
    *    3|start_address|VARCHAR(255)|1||0
    *    4|stop_coordinate|VARCHAR(255)|1||0
    *    5|stop_address|VARCHAR(255)|1||0
    *    6|start_timestamp|DATETIME|1||0
    *    7|stop_timestamp|DATETIME|1||0
    *    8|transportation_mode|VARCHAR(255)|0||0
    *    9|segment_confidence|REAL|0||0
    *   10|transportation_confidence|REAL|0||0
    *   11|path|TEXT|0||0
    *   12|multimodal_trip_id|INTEGER|0||0
    */
  def writeTripInfo(userID: UserID, startAddress: String, stopAddress: String,
                    trip: Trip): Unit = {
    val citizenID = userID
    val startCoordinate = s"[${trip.start.lat},${trip.start.long}]"
    val stopCoordinate = s"[${trip.end.lat},${trip.end.long}]"
    val startTimestamp = trip.start.timestamp.toLocalDateTime.format(dateTimeFormatter)
    val stopTimestamp = trip.end.timestamp.toLocalDateTime.format(dateTimeFormatter)
    val path = "[" + trip.trace.map(p => s"[${p.long},${p.lat}]").mkString(", ") + "]"

    val queryStr =
      s"""
         |INSERT INTO trip (
         |  citizen_id,
         |  start_coordinate,
         |  start_timestamp,
         |  start_address,
         |  stop_coordinate,
         |  stop_timestamp,
         |  stop_address,
         |  path
         |) VALUES (
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?
         |)
       """.stripMargin

    val stmt = stageConnection.prepareStatement(queryStr)
    stmt.setString(1, citizenID)
    stmt.setString(2, startCoordinate)
    stmt.setString(3, startTimestamp)
    stmt.setString(4, startAddress)
    stmt.setString(5, stopCoordinate)
    stmt.setString(6, stopTimestamp)
    stmt.setString(7, stopAddress)
    stmt.setString(8, path)
    stmt.executeUpdate()
  }

  /*
   * Schema:
   *
   * CREATE TABLE IF NOT EXISTS "trip" (
   *    "trip_id" INTEGER NOT NULL PRIMARY KEY,
   *    "citizen_id" VARCHAR(255) NOT NULL,
   *    "start_coordinate" VARCHAR(255) NOT NULL,
   *    "start_address" VARCHAR(255) NOT NULL,
   *    "stop_coordinate" VARCHAR(255) NOT NULL,
   *    "stop_address" VARCHAR(255) NOT NULL,
   *    "start_timestamp" DATETIME NOT NULL,
   *    "stop_timestamp" DATETIME NOT NULL,
   *    "transportation_mode" VARCHAR(255),
   *    "segment_confidence" REAL,
   *    "transportation_confidence" REAL,
   *    "path" TEXT,
   *    "json_file_path" TEXT, FOREIGN KEY ("citizen_id") REFERENCES "citizen" ("citizen_id")
   * );
   */
  def writeStageInfoWithJSONFilePath(stage: Pilot4Stage): Unit = {
    val citizenID = stage.userID
    val startCoordinate = s"[${stage.start.lat},${stage.start.long}]"
    val stopCoordinate = s"[${stage.stop.lat},${stage.stop.long}]"
    val startTimestamp = stage.start.timestamp.toLocalDateTime.format(dateTimeFormatter)
    val stopTimestamp = stage.stop.timestamp.toLocalDateTime.format(dateTimeFormatter)
    val path = "[" + stage.trajectory.map(p => s"[${p.long},${p.lat}]").mkString(", ") + "]"

    val queryStr =
      s"""
         |INSERT INTO trip (
         |  citizen_id,
         |  start_coordinate,
         |  start_timestamp,
         |  start_address,
         |  stop_coordinate,
         |  stop_timestamp,
         |  stop_address,
         |  transportation_mode,
         |  transportation_confidence,
         |  path,
         |  json_file_path
         |) VALUES (
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?,
         |  ?
         |)
       """.stripMargin

    val stmt = stageConnection.prepareStatement(queryStr)
    stmt.setString(1, citizenID)
    stmt.setString(2, startCoordinate)
    stmt.setString(3, startTimestamp)
    stmt.setString(4, stage.startAddress)
    stmt.setString(5, stopCoordinate)
    stmt.setString(6, stopTimestamp)
    stmt.setString(7, stage.stopAddress)
    stmt.setString(8, stage.mode)
    stmt.setDouble(9, stage.modeConfidence)
    stmt.setString(10, path)
    stmt.setString(11, stage.jsonFilePath)
    stmt.executeUpdate()
  }

  /**
    * trip table
    *
    *    0|trip_id|INTEGER|1||1
    *    1|citizen_id|VARCHAR(255)|1||0
    *    2|start_coordinate|VARCHAR(255)|1||0
    *    3|start_address|VARCHAR(255)|1||0
    *    4|stop_coordinate|VARCHAR(255)|1||0
    *    5|stop_address|VARCHAR(255)|1||0
    *    6|start_timestamp|DATETIME|1||0
    *    7|stop_timestamp|DATETIME|1||0
    *    8|transportation_mode|VARCHAR(255)|0||0
    *    9|segment_confidence|REAL|0||0
    *   10|transportation_confidence|REAL|0||0
    *   11|path|TEXT|0||0
    *   12|multimodal_trip_id|INTEGER|0||0
    */
  def writeStageInfo(stage: Pilot2Stage, tripID: Long): Unit = {
    val citizenID: String = stage.userID
    val startCoordinate: String = s"[${stage.start.lat},${stage.start.long}]"
    val startAddress: String = stage.startAddress
    val stopCoordinate: String = s"[${stage.stop.lat},${stage.stop.long}]"
    val stopAddress: String = stage.startAddress
    val startTimestamp: Timestamp = stage.start.timestamp
    val stopTimestamp: Timestamp = stage.stop.timestamp
    val transportationMode: String = stage.mode
//    val segmentConfidence: Double = ???
    val transportationConfidence: Double = stage.modeConfidence
    val path: String = "[" + stage.trajectory.map(p => s"[${p.long},${p.lat}]").mkString(", ") + "]"
    val multimodalTripId: Long = tripID

    val queryStr =
      s"""
         |INSERT INTO trip (
         |  citizen_id,
         |  start_coordinate,
         |  start_timestamp,
         |  start_address,
         |  stop_coordinate,
         |  stop_timestamp,
         |  stop_address,
         |  transportation_mode,
         |  transportation_confidence,
         |  path,
         |  multimodal_trip_id
         |) VALUES (
         |  '$citizenID',
         |  '$startCoordinate',
         |  '$startTimestamp',
         |  ?,
         |  '$stopCoordinate',
         |  '$stopTimestamp',
         |  ?,
         |  '$transportationMode',
         |  $transportationConfidence,
         |  '$path',
         |  $multimodalTripId
         |)
       """.stripMargin
    val stmt = stageConnection.prepareStatement(queryStr)
    stmt.setString(1, startAddress)
    stmt.setString(2, stopAddress)
    stmt.executeUpdate()
  }
}
