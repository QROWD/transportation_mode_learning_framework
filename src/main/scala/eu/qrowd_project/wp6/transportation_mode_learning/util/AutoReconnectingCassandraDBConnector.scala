package eu.qrowd_project.wp6.transportation_mode_learning.util

import com.datastax.driver.core.{Cluster, Session, SocketOptions}

/**
  * Establishes a connection proxy which is able to reconnect to the
  * Cassandra DB even if we got timeouts or other problems which require
  * reconnecting.
  */
class AutoReconnectingCassandraDBConnector
  extends CassandraDBConnector {

  private var _cluster: Cluster = _
  def arCluster: Cluster = {
    // cluster wasn't set up, yet, or closed already
    if (!_clusterInitialized || _cluster.isClosed) {
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

  private var _session: Session = _

  override def _initSession: Session = {
    val session = _cluster.connect
    _sessionInitialized = true
    session
  }

  def arSession: Session = {
    // session wasn't set up, yet or closed already
    if (!_sessionInitialized || _session.isClosed) {
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
        val keyspaces = arCluster.getMetadata.getKeyspaces
        res = runQuery(keyspaces, arSession, day, accuracyThreshold)
//        res = readData(day, accuracyThreshold, userID)
        dataReadSuccessfully = true
      } catch {
        case t: Throwable =>
          logger.error("Failed to read data from Cassandra. Retrying....", t)
      }
    }

    res
  }

  def getAccDataForUserAndDay(userID: String, day: String): Seq[AccelerometerRecord] = {
    var dataReadSuccessfully = false
    var res = Seq.empty[AccelerometerRecord]
    while (!dataReadSuccessfully) {
      try {
        res = super.getAccDataForUserAndDay(userID, day, _session)
        dataReadSuccessfully = true
      } catch {
        case t: Throwable =>
          logger.error("Failed to read data from Cassandra. Retrying....", t)
      }
    }

    res
  }


  override def close(): Unit = {
    if(arSession != null && !arSession.isClosed) {
      logger.info("stopping Cassandra session ...")
      arSession.close()
    }
    if(arCluster != null && !arCluster.isClosed) {
      logger.info("stopping Cassandra cluster ...")
      arCluster.close()
    }
  }
}
