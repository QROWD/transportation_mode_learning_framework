package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.ByteArrayOutputStream
import java.time.ZoneOffset

import io.jenetics.jpx.{GPX, Track, TrackSegment, WayPoint}

import scala.collection.JavaConverters._

/**
  * A converter for GPX.
  *
  * @author Lorenz Buehmann
  */
object GPXConverter {

  /**
    * Convert GPS trajectory to GPX.
    *
    * @param trajectory the trajectory
    * @return the GPX object
    */
  def toGPX(trajectory: Seq[TrackPoint]): GPX = {
    GPX
      .builder()
      .addTrack(
        Track
          .builder()
          .addSegment(
            TrackSegment.of(
              trajectory
                .map(
                  p =>
                    WayPoint.of(p.lat,
                                p.long,
                                p.timestamp.toLocalDateTime.toEpochSecond(
                                  ZoneOffset.UTC)))
                .toList
                .asJava
            )
          )
          .build()
      )
      .build()
  }

  /**
    * Convert GPS trajectory to GPX string.
    *
    * @param trajectory the trajectory
    * @return a GPX string
    */
  def toGPXString(trajectory: Seq[TrackPoint]): String = {
    val baos = new ByteArrayOutputStream()
    GPX.write(toGPX(trajectory), baos)
    new String(baos.toByteArray)
  }

  /**
    * Convert GPX to GPS trajectory.
    *
    * Note, we just use the first route in the GPX. Timestamps might not exist and will set to
    * null then.
    *
    * @param gpx the GPX
    * @return the trajectory
    */
  def fromGPX(gpx: GPX): Seq[TrackPoint] = {
    if (gpx.getRoutes.isEmpty) {
      throw new RuntimeException("no route in GPX data")
    }

    // take first route
    val route = gpx.getRoutes.get(0)

    // convert the points
    route.getPoints.asScala.map(wp =>
      new TrackPoint(wp.getLatitude.toDegrees, wp.getLongitude.toDegrees, null))
  }

}
