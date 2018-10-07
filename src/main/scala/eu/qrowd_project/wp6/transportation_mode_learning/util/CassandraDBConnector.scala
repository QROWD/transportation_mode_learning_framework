package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.File
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util
import java.util.Date

import breeze.io.CSVWriter
import com.datastax.driver.core.exceptions.{InvalidQueryException, UnauthorizedException}
import com.datastax.driver.core.{Cluster, KeyspaceMetadata, ResultSet, Session, SocketOptions}
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConversions._

/**
  * Connect to a Cassandra DB of Uni Trento
  * Credential have to be provided in the file `cassandra.conf`
  *
  * As an optional argument you can set the user IdDs used during data retrieval
  *
  * @author Lorenz Buehmann
  */
class CassandraDBConnector(var userIds: Seq[String] = Seq()) {

  val logger = com.typesafe.scalalogging.Logger("Cassandra DB connector")

  lazy val config = ConfigFactory.parseFile(new File(getClass.getClassLoader.getResource("cassandra.conf").toURI))

  // FIXME: copied over from LocationEventRecord
  //                                                           20180530073346961
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssnnnnnnnnn")
  def asTimestamp(timestamp :String): Timestamp =
      Timestamp.valueOf(LocalDateTime.parse(timestamp + "000000", dateTimeFormatter))
//    Timestamp.valueOf(LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

  lazy val cluster: Cluster = {
    logger.info("setting up Cassandra cluster...")
    _initCluster
  }

  def _initCluster: Cluster = {
    val builder = Cluster.builder
    builder
      .addContactPoints(config.getString("connection.url"))
      .withPort(config.getInt("connection.port"))
      .withCredentials(
        config.getString("connection.credentials.user"),
        config.getString("connection.credentials.password"))
      .withMaxSchemaAgreementWaitSeconds(60)
      .withSocketOptions(new SocketOptions()
        .setConnectTimeoutMillis(60000)
        .setReadTimeoutMillis(60000))
      .build
    val cluster = builder.build
    _clusterInitialized = true
    cluster
  }

  lazy val session: Session = {
    logger.info("setting up Cassandra session...")
    _initSession
  }

  def _initSession: Session = {
    val session = cluster.connect
    _sessionInitialized = true
    session
  }

  var _clusterInitialized = false
  var _sessionInitialized = false

  /**
    *
    * Returns all location event record for the given date per each user that belongs to QROWD project.
    *
    * @param day the date you want to query for. format: `yyyymmdd`
    * @return users with their location event records
    */
  def readData(day: String,
               accuracyThreshold: Int = Int.MaxValue,
               userID: Option[String] = None): Seq[(String, Seq[LocationEventRecord])] = {
    // get all the keyspaces
    val keyspaces = cluster.getMetadata.getKeyspaces
    runQuery(keyspaces, session, day, accuracyThreshold)
  }

  def getAccDataForUserAndDay(userID: String, day: String, session: Session = session): Seq[AccelerometerRecord] = {
    logger.info(s"getting accelerometer data for user $userID on $day...")
    val resultSet: ResultSet = session.execute(
      s"SELECT * FROM $userID.accelerometerevent WHERE day='$day'")

    time {
      if (resultSet != null) {
        resultSet.map(row => {
          val x = row.getFloat("x").toDouble
          val y = row.getFloat("y").toDouble
          val z = row.getFloat("z").toDouble
          val timestamp = asTimestamp(row.getString("timestamp"))

          AccelerometerRecord(x, y, z, timestamp)
        }).toSeq
      } else {
        Seq.empty[AccelerometerRecord]
      }
    }
  }

  def time[R](block: => R): R = {
    val t0 = System.currentTimeMillis()
    val result = block    // call-by-name
    val t1 = System.currentTimeMillis()
    logger.info("Elapsed time: " + (t1 - t0) + "ms")
    result
  }



  def runQuery(keyspaces: util.List[KeyspaceMetadata], session: Session, day: String, accuracyThreshold: Int): Seq[(String, Seq[LocationEventRecord])] = {
    var data: Seq[(String, Seq[LocationEventRecord])] = Seq()

    // loop over each keyspace
    for (keyspace <- keyspaces if userIds.isEmpty || userIds.contains(keyspace.getName)) { //Get the keyspace name that is what we need to perform queries. Since 1 keyspace = 1 user, the keyspace name is the user uniqueidentifier (salt)
      val usersalt = keyspace.getName

      try {
        // execute the select query for all the positions collected from the user (usersalt) for a specific day (daystring)
        val resultSet: ResultSet = session.execute("SELECT * FROM " + usersalt + ".locationeventpertime WHERE day='" + day + "'")

        // if at this point there is no error means that you have select permissions and then the user belongs to QROWD
        logger.debug(s"User $usersalt belonging to QROWD :)")
        if (resultSet != null) {
          var entries = resultSet.map(row => LocationEventRecord.from(row)).toSeq
          logger.debug(s"--------------------- Num records before filtering: ${entries.size}")
          entries = entries.filter(_.accuracy < accuracyThreshold)
          logger.debug(s"--------------------- Num records after filtering (<$accuracyThreshold): ${entries.size}")
          data :+= (usersalt, entries)
        }
      } catch {
        case e: UnauthorizedException =>
          logger.warn(s"User $usersalt not belonging to QROWD :(")
        case e: InvalidQueryException =>
          logger.warn(s"User $usersalt is an old one, we don't care about him and he does not belong to QROWD :(")
      }
    }

    // deduplicate //TODO avoid
    data = data.distinct

    data
  }

  /**
    *
    * @param user The hash representing a user, e.g. c43070046a941029397691609f583f327b2d1fa9
    * @param dateString A string of the pattern %Y%m%d, e.g. 20180528
    * @param csvOutputFile A file object for the output CSV file
    */
  def writeAccelerometerDataToCSV(user: String, dateString: String, csvOutputFile: File): Unit = {
    /*
     * > DESC accelerometerevent;
     *
     * CREATE TABLE abcdefg.accelerometerevent (
     *     day text,
     *     timestamp text,
     *     x float,
     *     y float,
     *     z float,
     *     PRIMARY KEY (day, timestamp)
     * ) WITH CLUSTERING ORDER BY (timestamp ASC)
     *     AND ...
     */
    val resultColumns = Seq("timestamp", "x", "y", "z")
    val table = "accelerometerevent"

    val queryString =
      "SELECT " + resultColumns.mkString(",") + " " +
      "FROM " + user + "." + table + " " +
      "WHERE day='" + dateString + "'"
    val resultSet = session.execute(queryString)

    val preparedData = resultSet.map(row => Seq(
      row.getString("timestamp"),
      row.getFloat("x").toString,
      row.getFloat("y").toString,
      row.getFloat("z").toString
    ).toIndexedSeq)

    var data: Seq[IndexedSeq[String]] = Seq(Seq("timestamp", "x", "y", "z").toIndexedSeq)

    data ++= preparedData
    CSVWriter.writeFile(csvOutputFile, data.toIndexedSeq)
  }

  /**
    *
    * @param user The hash representing a user, e.g. c43070046a941029397691609f583f327b2d1fa9
    * @param dateString A string of the pattern %Y%m%d, e.g. 20180528
    * @param csvOutputFile A file object for the output CSV file
    */
  def writeGPSDataToCSV(user: String, dateString: String, csvOutputFile: File): Unit = {
    /*
     * > DESC locationeventpertime;
     *
     * CREATE TABLE abcdefg.locationeventpertime (
     *     day text,
     *     timestamp text,
     *     accuracy float,
     *     bearing double,
     *     lucene text,
     *     point frozen<point>,
     *     provider text,
     *     speed float,
     *     PRIMARY KEY (day, timestamp)
     * ) WITH CLUSTERING ORDER BY (timestamp ASC)
     *     AND ...
     */
    val table = "locationeventpertime"

    val queryString =
      "SELECT * " +
        "FROM " + user + "." + table + " " +
        "WHERE day='" + dateString + "'"
    val resultSet = session.execute(queryString)

    val preparedData = resultSet.map(row => (row.getString("timestamp"), LocationEventRecord.from(row)))
      .map(r => Seq(r._1,
        r._2.latitude.toString,
        r._2.longitude.toString,
        r._2.altitude.toString).toIndexedSeq)

    var data: Seq[IndexedSeq[String]] =
      Seq(Seq("timestamp", "latitude", "longitude", "altitude").toIndexedSeq)

    data ++= preparedData
    CSVWriter.writeFile(csvOutputFile, data.toIndexedSeq)
  }

  /**
    * Close the connection.
    */
  def close(): Unit = {
    if(_sessionInitialized) {
      logger.info("stopping Cassandra session ...")
      session.close()
    }
    if(_clusterInitialized) {
      logger.info("stopping Cassandra cluster ...")
      cluster.close()
    }
  }
}

object CassandraDBConnector {

  def apply(): CassandraDBConnector = new CassandraDBConnector()

  def apply(userIDs: Seq[String]): CassandraDBConnector = new CassandraDBConnector(userIDs)


  def main(args: Array[String]): Unit = {
    val cassandra = CassandraDBConnector()
    val data = cassandra.readData("20180330")
    println(data.size)
    cassandra.close()
  }
}

