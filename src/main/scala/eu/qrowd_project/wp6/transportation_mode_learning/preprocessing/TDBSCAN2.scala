package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import java.sql.Timestamp
import java.util

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Clusterer

import scala.collection.mutable
import eu.qrowd_project.wp6.transportation_mode_learning.util.{HaversineDistance, TrackPoint}
import org.apache.commons.math3.ml.clustering.{Cluster, Clusterable}

import collection.JavaConverters._

/**
  * An implementation of T-DBSCAN for clustering spatio-temporal data.
  * The goal is identify stops in a GPS trajectory.
  *
  * @param ceps   the distance range to ensure that the points comprising a stop are of state continuity (in km)
  * @param eps    the search radius for identifying density-based neighborhood (in km)
  * @param minPts the minimum number of neighboring points to identify a core point
  * @author Lorenz Buehmann
  */
class TDBSCAN2(
               val ceps: Double,
               val eps: Double,
               val minPts: Int,
               val performDistanceMerge: Boolean = false) extends Clusterer {

  private var _distanceLookupCounter: Int = 0

  def this(config: com.typesafe.config.Config) {
    this(
      config.getDouble("cEps"),
      config.getDouble("eps"),
      config.getInt("minPts"))

    logger.debug(s"TDBSCAN params: ceps=$ceps\teps=$eps\tminPts=$minPts")
  }

  private val logger = com.typesafe.scalalogging.Logger("TDBSCAN")

  private var visited = mutable.Set[TrackPoint]()

  private def reset(): Unit = {
    visited.clear()
    _distanceLookupCounter = 0
  }

  object PointStatus extends Enumeration {
    /** The point has is considered to be noise. */
    type PointStatus = Value
    val NOISE, PART_OF_CLUSTER = Value
  }

  import PointStatus._

  def cluster(points: Seq[TrackPoint]): Seq[Seq[TrackPoint]] = {
    reset()

    var clusters = Seq[Seq[TrackPoint]]()
    val visited = mutable.Map[Clusterable, PointStatus]()

    var c = 0
    var maxId: Timestamp = Timestamp.valueOf(points.head.timestamp.toLocalDateTime.minusDays(1))

    val end = points.last.timestamp

    while(maxId.before(end)) {
      val p = points
        .filter(_.timestamp.after(maxId))
        .head

      // get neighbors
      val neighbors = getNeighbors(p, points)
      println(s"#neighbors:${neighbors.size}")

      // update max ID
      maxId = p.timestamp

      // increase cluster ID
      if(neighbors.size >= minPts) { // core point
        c += 1

        logger.debug("new cluster")
        println("new cluster")

        // create new cluster
        val cluster = mutable.ListBuffer[TrackPoint]()

        // expand the cluster
        val (expandedCluster, maxClusterId) = expandCluster(cluster, p, points, neighbors, maxId, visited)

        maxId = maxClusterId

        clusters :+= expandedCluster
      } else{ // noise
        visited += p -> PointStatus.NOISE
      }
      if (Thread.currentThread().isInterrupted) {
        throw new InterruptedException
      }
    }

    // merge clusters if timestamps overlap
    // if max point id of cluster_i >= min point id of cluster_i+1
//    var mergedClusters = Seq[Seq[TrackPoint]]()
//    while(mergedClusters.size < clusters.size) {
//      clusters.sliding(2).foreach {
//        case Seq(c1, c2) =>
//          if(c1.last.timestamp.after(c2.head.timestamp)) {
//            mergedClusters :+= c1 ++ c2
//          } else {
//            mergedClusters :
//          }
//      }
//    }

    logger.debug(s"got ${clusters.size} clusters")
    logger.debug("merging clusters...")
    var mergedClusters =
      clusters
        .filter(_.size >= minPts)
        .foldRight(Seq[Seq[TrackPoint]]()){
          (left, rightClusters) => {
            if (Thread.currentThread().isInterrupted) {
              throw new InterruptedException
            }
            if(rightClusters.isEmpty) {
              left +: rightClusters
            } else {
              val right = rightClusters.head
              // merge if there is time overlap
              if(left.last.timestamp.after(right.head.timestamp)) {
                val mergedCluster = left ++ right
                mergedCluster +: rightClusters.drop(1)
              } else { // otherwise, prepend
                left +: rightClusters
              }
            }
          }
        }
    logger.info(s"got ${mergedClusters.size} clusters after time overlap merging.")


    // we perform another merge step if clusters are too close by distance

    mergedClusters =
      mergedClusters.foldRight(Seq[Seq[TrackPoint]]()){
        (left, rightClusters) => {
          if(rightClusters.isEmpty) {
            left +: rightClusters
          } else {
            val right = rightClusters.head
            // merge if there is not that much distance
//            println(left.last + ":::" + right.head)
//            println(distance(left.last, right.head))
            if(distance(left.last, right.head) < ceps) {
//              println("merge")
              val mergedCluster = left ++ right
              mergedCluster +: rightClusters.drop(1)
            } else { // otherwise, prepend
//              println("prepended: " + (left +: rightClusters).map(_.head))
              left +: rightClusters
            }
          }
        }
      }
    logger.info(s"got ${mergedClusters.size} clusters after distance merging.")


//    mergedClusters
//      .filter(_.size >= minPts)
//      .foldRight(Seq[Seq[TrackPoint]]()){
//      (left, rightClusters) => {
//        if(rightClusters.isEmpty) {
//          left +: rightClusters
//        } else {
//          val right = rightClusters.head
//          // merge if there is not that much distance
//          //            println(left.last + ":::" + right.head)
//                      println(distance(left.last, right.head))
//          if(distance(left.last, right.head) < ceps) {
//            //              println("merge")
//            val mergedCluster = left ++ right
//            mergedCluster +: rightClusters.drop(1)
//          } else { // otherwise, prepend
//            //              println("prepended: " + (left +: rightClusters).map(_.head))
//            left +: rightClusters
//          }
//        }
//      }
//    }

    logger.info(s"#distance lookups=${_distanceLookupCounter}")
    mergedClusters
  }

  /**
    * get the epsilon-neighborhood of the given point.
    * @param p the point
    * @param points all points
    * @return the neighborhood
    */
  private def  getNeighbors(p: TrackPoint, points: Seq[TrackPoint]): Seq[TrackPoint] = {
    //println(s"computing neighbors for $p ...")
    var neighbors = Seq[TrackPoint]()

//    points.take(10).foreach(p => println(s"Point: $p"))

    points
      .slice(points.indexOf(p), points.size - 1) // should be more efficient, but works only if list is ordered
//      .filter(_.timestamp.after(p.timestamp))
      .takeWhile(distance(_, p) <= ceps)
      .foreach(p_other => {
        val d = distance(p_other, p)

        if(d <= ceps && d <= eps) {
          neighbors :+= p_other
        }
        if (Thread.currentThread().isInterrupted) {
          throw new InterruptedException
        }
      })

    neighbors
  }

  private def expandCluster(cluster: mutable.ListBuffer[TrackPoint], p: TrackPoint, points: Seq[TrackPoint], neighbors: Seq[TrackPoint],
                            maxId: Timestamp, visited: mutable.Map[Clusterable, PointStatus])
  : (Seq[TrackPoint], Timestamp) = {
    logger.debug(s"expanding cluster $maxId ...")
    println(s"expanding cluster $maxId ...")

    cluster += p
    visited += p -> PointStatus.PART_OF_CLUSTER

    var currentMaxId = maxId

    var seeds = Seq[TrackPoint](neighbors: _*)

    var index = 0
    while(index < seeds.size) {
      val current = seeds(index)

      // update max ID
      if (current.timestamp.after(currentMaxId)) currentMaxId = current.timestamp

      // only check non-visited points
      val pStatus = visited.get(current)
      if(pStatus.isEmpty) {
        // find the neighbors of neighbors of core point p
        val currentNeighbors = getNeighbors(current, points)

        if(currentNeighbors.size >= minPts) seeds = merge(seeds, currentNeighbors)
      }

      // add p to cluster if it isn't already member of a cluster
      if(pStatus.isEmpty || pStatus.get != PointStatus.PART_OF_CLUSTER) {
        cluster += current
        visited += current -> PointStatus.PART_OF_CLUSTER
      }

      index += 1
      if (Thread.currentThread().isInterrupted) {
        throw new InterruptedException
      }
    }
//    neighbors.foreach(n => {
//      // mark as visited
//      visited += n
//
//      // update max ID
//      if (n.timestamp.after(currentMaxId)) {
//        currentMaxId = n.timestamp
//      }
//
//      if(!visited.contains(n)) {
//        // find the neighbors of neighbors of core point p
//        val neighborsNeighbors = getNeighbors(n, points)
//
//        if(neighborsNeighbors.size >= minPts) {
//          seeds = merge(seeds, neighborsNeighbors)
//        }
//
//        // add p to cluster if it isn't already member of a cluster
//        if(!clusters.exists(_.contains(n))) {
//          cluster :+= n
//        }
//      }
//    })

    (cluster, currentMaxId)

  }

  private def merge(first: Seq[TrackPoint], second: Seq[TrackPoint]): Seq[TrackPoint] = {
    (mutable.LinkedHashSet(first: _*) ++ second).toSeq
  }

  private def distance(p1: TrackPoint, p2: TrackPoint): Double = {
    _distanceLookupCounter += 1
    HaversineDistance.compute(p1, p2)
  }

}
