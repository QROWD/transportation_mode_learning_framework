package eu.qrowd_project.wp6.transportation_mode_learning.util

import com.datastax.driver.core.{Cluster, Session}

/**
  * Establishes a connection proxy which is able to reconnect to the
  * Cassandra DB even if we got timeouts or other problems which require
  * reconnecting.
  */
class AutoReconnectingCassandraDBConnector extends CassandraDBConnector {
  private var _cluster: Cluster = null
  private var _session: Session = null

  def cluster: Cluster = {
    // cluster wasn't set up, yet, or closed already
    if (_cluster == null || _cluster.isClosed) {
      logger.info("setting up Cassandra cluster...")
      var clusterSetUpSuccessfully = false

      while (!clusterSetUpSuccessfully) {
        try {
          val builder = Cluster.builder
          builder
            .addContactPoints(config.getString("connection.url"))
            .withPort(config.getInt("connection.port"))
            .withCredentials(
              config.getString("connection.credentials.user"),
              config.getString("connection.credentials.password"))
            .withMaxSchemaAgreementWaitSeconds(60)
            //      .withSocketOptions(new SocketOptions()
            //        .setConnectTimeoutMillis(120000)
            //        .setReadTimeoutMillis(120000))
            .build
          _cluster = builder.build
          clusterSetUpSuccessfully = true
        } catch {
          case t: Throwable =>
            logger.error("Failed to set up cassandra cluster. Retrying....", t)
        }
      }
    }

    _cluster
  }

  def session: Session = {
    // session wasn't set up, yet or closed already
    if (_session == null || _session.isClosed) {
      logger.info("setting up Cassandra session...")
      var sessionSetUpSuccessfully = false

      while (!sessionSetUpSuccessfully) {
        try {
          _session = cluster.connect
          sessionSetUpSuccessfully = true
        } catch {
          case t: Throwable =>
            logger.error("Failed to set up cassandra session. Retrying....", t)
        }
      }
    }

    _session
  }

  override def readData(day: String): Seq[(String, Seq[LocationEventRecord])] = {
    var dataReadSuccessfully = false
    var res = Seq.empty[(String, Seq[LocationEventRecord])]

    while (!dataReadSuccessfully) {
      try {
        res = runQuery(cluster.getMetadata.getKeyspaces, session, day)
        dataReadSuccessfully = true
      } catch {
        case t: Throwable =>
          logger.error("Failed to read data from cassandra. Retrying....", t)
      }
    }

    res
  }

  override def close(): Unit = {
    if(_session != null || !_session.isClosed) {
      logger.info("stopping Cassandra session ...")
      session.close()
    }
    if(_cluster != null || !_cluster.isClosed) {
      logger.info("stopping Cassandra cluster ...")
      cluster.close()
    }
  }
}
