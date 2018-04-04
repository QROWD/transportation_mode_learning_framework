package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.awt.Color

import scala.collection.JavaConverters._
import scala.util.Random

import javax.json.{Json, JsonObject, JsonValue}

/**
  * Convert a GPS trajectory to GeoJSON.
  *
  * @author Lorenz Buehmann
  */
object GeoJSONConverter {

  /**
    * Convert a GPS trajectory to GeoJSON.
    *
    * @param points the GPS trajectory
    * @param format the GeoJSON format, either 'points' or 'linestring'
    * @return the GeoJSON object
    */
  def convert(points: Seq[TrackPoint], format: String): JsonObject = {
    format match {
      case "points" => toGeoJSONPoints(points)
      case "linestring" => toGeoJSONLineString(points)
      case _ => throw new RuntimeException(s"format $format not supported")
    }
  }

  def merge(json1 : JsonObject, json2: JsonObject): JsonObject = {
    val features1 = json1.getJsonArray("features")
    val features2 = json2.getJsonArray("features")

    val features = Json.createArrayBuilder(features1)
    features2.getValuesAs[JsonValue](classOf[JsonValue]).asScala.foreach(features.add)

    Json.createObjectBuilder(json1)
      .add("features",  features)
      .build()
  }

  def toGeoJSONPoints(entries: Seq[TrackPoint], properties: Map[String, String] = Map()): JsonObject = {
    val features = Json.createArrayBuilder()

    if(entries.nonEmpty) {
      val concisePoints = entries.head :: entries.sliding(2).collect { case Seq(a,b) if a != b => b }.toList

      concisePoints.zipWithIndex.foreach{
        case(p, i) =>
          val point = Json.createObjectBuilder()
            .add("type", "Feature")
            .add("geometry", Json.createObjectBuilder()
              .add("type", "Point")
              .add("coordinates", Json.createArrayBuilder(Seq(p.long, p.lat).asJava)))

          val props = Json.createObjectBuilder()
            .add("timestamp", p.toString)
          properties.foreach(e => props.add(e._1, e._2))
          point.add("properties", props)

          features.add(i, point)
      }
    }

    Json.createObjectBuilder()
      .add("type", "FeatureCollection")
      .add("features", features)
      .build()
  }


  def toGeoJSONLineString(entries: Seq[TrackPoint]): JsonObject = {
    val concisePoints = entries.head :: entries.sliding(2).collect { case Seq(a,b) if a != b => b }.toList

    val coordinates = Json.createArrayBuilder()
    concisePoints.zipWithIndex.foreach{
      case(p, i) => coordinates.add(i, Json.createArrayBuilder(Seq(p.long, p.lat).asJava))
    }

    val feature = Json.createObjectBuilder()
      .add("type", "Feature")
      .add("geometry", Json.createObjectBuilder()
        .add("type", "LineString")
        .add("coordinates", coordinates)
      )
      .add("properties", Json.createObjectBuilder()
        .add("timestamp-start", entries.head.timestamp.toString)
        .add("timestamp-end", entries.last.timestamp.toString))

    Json.createObjectBuilder()
      .add("type", "FeatureCollection")
      .add("features", Json.createArrayBuilder().add(feature))
      .build()
  }

  private def addStyleData(json: JsonObject, title: String, color: String) = {
    Json.createObjectBuilder(json)
      .add("title", title)
      .add("marker-color", color)
      .build()
  }
// "#"+Integer.toHexString(your_color.getRGB()).substring(2)
  def colors(numColors: Int) = {
    for(i <- 0 to 360 by  360 / numColors) yield
      Color.getHSBColor(i, 90 + Random.nextFloat() * 10, 50 + Random.nextFloat() * 10)
  }

}
