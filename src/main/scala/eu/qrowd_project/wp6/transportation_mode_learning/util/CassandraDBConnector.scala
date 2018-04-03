package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.File

import scala.collection.JavaConversions._

import com.datastax.driver.core.exceptions.{InvalidQueryException, UnauthorizedException}
import com.datastax.driver.core.{Cluster, Session, SocketOptions}
import com.typesafe.config.ConfigFactory

/**
  * Connect to a Cassandra DB.
  * Credential have to be provided in the file `cassandra.conf`
  *
  * @author Lorenz Buehmann
  */
class CassandraDBConnector {

  val logger = com.typesafe.scalalogging.Logger("Cassandra DB connector")

  lazy val config = ConfigFactory.parseFile(new File(getClass.getClassLoader.getResource("cassandra.conf").toURI))

  lazy val cluster: Cluster = {
    logger.info("setting up Cassandra cluster...")

    val builder = Cluster.builder
    builder
      .addContactPoints(config.getString("connection.url"))
      .withPort(config.getInt("connection.port"))
      .withCredentials(
        config.getString("connection.credentials.user"),
        config.getString("connection.credentials.password"))
      .withMaxSchemaAgreementWaitSeconds(60)
      .withSocketOptions(new SocketOptions()
        .setConnectTimeoutMillis(120000)
        .setReadTimeoutMillis(120000))
      .build
    builder.build
  }

  lazy val session: Session = {
    logger.info("setting up Cassandra session...")
    cluster.connect
  }


  /**
    *
    * Returns all location event record for the given date per each user that belongs to QROWD project.
    *
    * @param day the date you want to query for. format: `yyyymmdd`
    * @return users with their location event records
    */
  def readData(day: String): Seq[(String, Seq[LocationEventRecord])] = {
    var data: Seq[(String, Seq[LocationEventRecord])] = Seq()

    // get all the keyspaces
    val keyspaces = cluster.getMetadata.getKeyspaces

    // loop over each keyspace
    for (keyspace <- keyspaces) { //Get the keyspace name that is what we need to perform queries. Since 1 keyspace = 1 user, the keyspace name is the user uniqueidentifier (salt)
      val usersalt = keyspace.getName

      try {
        // execute the select query for all the positions collected from the user (usersalt) for a specific day (daystring)
        val resultSet = session.execute("SELECT * FROM " + usersalt + ".locationeventpertime WHERE day='" + day + "'")

        // if at this point there is no error means that you have select permissions and then the user belongs to QROWD
        logger.info(s"User $usersalt belonging to QROWD :)")
        if (resultSet != null) {
          val entries = resultSet.map(row => LocationEventRecord.from(row)).toSeq
          data :+= (usersalt, entries)
        }
      } catch {
        case e: UnauthorizedException =>
          logger.warn(s"User $usersalt not belonging to QROWD :(")
        case e: InvalidQueryException =>
          logger.warn(s"User $usersalt is an old one, we don't care about him and he does not belong to QROWD :(")
      }
    }

    data
  }

  /**
    * Close the connection.
    */
  def close(): Unit = {
    logger.info("stopping Cassandra session and cluster...")
    session.close()
    cluster.close()
  }
}

object CassandraDBConnector {

  def apply(): CassandraDBConnector = new CassandraDBConnector()


  def main(args: Array[String]): Unit = {
    val cassandra = CassandraDBConnector()
    val data = cassandra.readData("20180330")
    println(data.size)
    cassandra.close()
  }
}

