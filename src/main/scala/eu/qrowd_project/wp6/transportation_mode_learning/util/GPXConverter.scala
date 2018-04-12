package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.ByteArrayOutputStream
import java.time.ZoneOffset

import io.jenetics.jpx.{GPX, Track, TrackSegment, WayPoint}

import scala.collection.JavaConverters._

/**
  * A converter to GPX.
  *
  * @author Lorenz Buehmann
  */
object GPXConverter {

  /**
    * Convert GPS trajectory to GPX.
    *
    * @param trajectory
    * @return
    */
  def toGPX(trajectory: Seq[TrackPoint]): GPX = {
    GPX.builder().addTrack(
      Track.builder().addSegment(
        TrackSegment.of(
          trajectory
            .map(p => WayPoint.of(p.lat, p.long, p.timestamp.toLocalDateTime.toEpochSecond(ZoneOffset.UTC)))
            .toList.asJava
        )
      ).build()
    ).build()
  }

  /**
    * Convert GPS trajectory to GPX string.
    *
    * @param trajectory
    * @return
    */
  def toGPXString(trajectory: Seq[TrackPoint]): String = {
    val baos = new ByteArrayOutputStream()
    GPX.write(toGPX(trajectory), baos)
    new String(baos.toByteArray)
  }

}
