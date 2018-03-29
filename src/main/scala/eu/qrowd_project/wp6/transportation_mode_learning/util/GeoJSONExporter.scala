package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.{BufferedWriter, FileWriter}

import scala.collection.JavaConverters._

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.LocationDataAnalyzer.jsonDir
import javax.json.stream.JsonGenerator
import javax.json.{Json, JsonObject}

/**
  * Export a GPS trajectory to GeoJSON file.
  *
  * @author Lorenz Buehmann
  */
object GeoJSONExporter {

  /**
    * Export a GPS trajectory to GeoJSON file.
    *
    * @param points the GPS trajectory
    * @param format the GeoJSON format, either 'points' or 'linestring'
    * @param path path to export file
    * @return the GeoJSON object
    */
  def export(points: Seq[TrackPoint], format: String, path: String): Unit = {
    write(GeoJSONConverter.convert(points, format), path)
  }

  def write(json: JsonObject, path: String) = {
    val config = Map(JsonGenerator.PRETTY_PRINTING -> true)
    val factory = Json.createWriterFactory(config.asJava)

    val jsonWriter = factory.createWriter(new BufferedWriter(new FileWriter(jsonDir.resolve(path).toString)))
    jsonWriter.writeObject(json)
    jsonWriter.close()
  }

}
