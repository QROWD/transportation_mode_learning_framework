package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import eu.qrowd_project.wp6.transportation_mode_learning.mapmatching.BarefootMapMatcherSocket
import eu.qrowd_project.wp6.transportation_mode_learning.util.{BarefootJSONConverter, POIRetrieval, TrackPoint}

/**
  * Basically, uses the stop point detection to perform trip detection given a GPS trajectory.
  *
  * @author Lorenz Buehmann
  */
class TripDetection(val useMapMatching: Boolean = false) {



  val logger = com.typesafe.scalalogging.Logger("TripDetection")

  lazy val mapMatcher = new BarefootMapMatcherSocket(host = "127.0.0.1", port = 1234)

  def find(trajectory: Seq[TrackPoint]): Seq[Trip] = {

    if(trajectory.isEmpty) {
      logger.warn("could not perform trip detection: empty trajectory")
      return Seq()
    }

    // sort by time
    val points = trajectory.sortWith(_ < _)

    // compute stop point clusters
    val stopClusters = StopDetection().find(points)
    logger.info(s"#stop clusters:${stopClusters.size}")

    // keep first and last point of each cluster
    val stopsStartEnd = stopClusters.map(c => (c.head, c.last, c))

    // the stop clusters are in ascending order w.r.t. time, i.e. we assume trips to be happen
    // between successive points
    // we keep the last point of the start cluster and the first point of the end cluster
    val trips: Seq[Trip] =
    stopsStartEnd
      .sliding(2)
      .filter(_.size == 2)
      .map (e =>{
//        case Seq(firstStop: (TrackPoint, TrackPoint), secondStop: (TrackPoint, TrackPoint)) =>
          // last point of first stop
          val begin = e(0)._2//firstStop._2
          // first point of second stop
          val end = e(1)._1//secondStop._1
          // entries in between
          val entries = points.filter(p => p.timestamp.after(begin.timestamp) && p.timestamp.before(end.timestamp))

          Trip(begin, end, entries, e(0)._3, e(1)._3)
//        case other =>
//          println(other)
//          Seq[(TrackPoint, TrackPoint, Seq[TrackPoint])]()
  } )
      .toSeq

//    trips.map(trip => trip.groupBy(p => p.timestamp).map(e => (e._1, e._2.size))).foreach(println(_))
//    trips.foreach(println(_))

    // get rid of outliers


    // perform map matching
    if(useMapMatching) {
      trips.map(trip => {
        val response = mapMatcher.query(BarefootJSONConverter.convert(trip.trace).toString)
        println(response)
      })
    }

    trips
  }


}

case class Trip(
                 start: TrackPoint,
                 end: TrackPoint,
                 trace: Seq[TrackPoint],
                 startCluster: Seq[TrackPoint] = Seq(),
                 endCluster: Seq[TrackPoint])
