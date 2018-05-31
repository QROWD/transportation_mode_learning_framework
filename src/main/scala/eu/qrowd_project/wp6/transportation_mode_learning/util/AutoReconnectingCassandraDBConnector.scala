package eu.qrowd_project.wp6.transportation_mode_learning.util

import com.datastax.driver.core.{Cluster, Session, SocketOptions}

/**
  * Establishes a connection proxy which is able to reconnect to the
  * Cassandra DB even if we got timeouts or other problems which require
  * reconnecting.
  */
class AutoReconnectingCassandraDBConnector
  extends CassandraDBConnector {

  override lazy val cluster: Cluster = {
    var _cluster: Cluster = null
    // cluster wasn't set up, yet, or closed already
    if (!_clusterInitialized || cluster.isClosed) {
      logger.info("setting up Cassandra cluster...")
      var clusterSetUpSuccessfully = false

      while (!clusterSetUpSuccessfully) {
        try {
          _cluster = _initCluster
          clusterSetUpSuccessfully = true
        } catch {
          case t: Throwable =>
            logger.error("Failed to set up Cassandra cluster. Retrying....", t)
        }
      }
    }

    _cluster
  }

  override lazy val session: Session = {
    var _session: Session = null
    // session wasn't set up, yet or closed already
    if (!_sessionInitialized) {
      logger.info("setting up Cassandra session...")
      var sessionSetUpSuccessfully = false

      while (!sessionSetUpSuccessfully) {
        try {
          _session = _initSession
          sessionSetUpSuccessfully = true
        } catch {
          case t: Throwable =>
            logger.error("Failed to set up Cassandra session. Retrying....", t)
        }
      }
    }

    _session
  }

  override def readData(day: String,
                        accuracyThreshold: Int = Int.MaxValue,
                        userID: Option[String] = None): Seq[(String, Seq[LocationEventRecord])] = {
    var dataReadSuccessfully = false
    var res = Seq.empty[(String, Seq[LocationEventRecord])]

    while (!dataReadSuccessfully) {
      try {
        val keyspaces = cluster.getMetadata.getKeyspaces
        res = runQuery(keyspaces, session, day, accuracyThreshold)
//        res = readData(day, accuracyThreshold, userID)
        dataReadSuccessfully = true
      } catch {
        case t: Throwable =>
          logger.error("Failed to read data from Cassandra. Retrying....", t)
      }
    }

    res
  }

  override def close(): Unit = {
    if(session != null && !session.isClosed) {
      logger.info("stopping Cassandra session ...")
      session.close()
    }
    if(cluster != null && !cluster.isClosed) {
      logger.info("stopping Cassandra cluster ...")
      cluster.close()
    }
  }
}
