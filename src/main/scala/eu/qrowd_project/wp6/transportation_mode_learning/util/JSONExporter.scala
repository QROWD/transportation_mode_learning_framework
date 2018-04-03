package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.{BufferedWriter, FileWriter}

import scala.collection.JavaConverters._

import javax.json.stream.JsonGenerator
import javax.json.{Json, JsonStructure}

/**
  * @author Lorenz Buehmann
  */
trait JSONExporter {

  def write(json: JsonStructure, path: String) = {
    val config = Map(JsonGenerator.PRETTY_PRINTING -> true)
    val factory = Json.createWriterFactory(config.asJava)

    val jsonWriter = factory.createWriter(new BufferedWriter(new FileWriter(path)))
    jsonWriter.write(json)
    jsonWriter.close()
  }

}
