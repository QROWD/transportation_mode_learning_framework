package eu.qrowd_project.wp6.transportation_mode_learning.util

import scala.util.Try

import javax.json.Json
import scalaj.http.Http

/**
  * @author Lorenz Buehmann
  */
object ReverseGeoCoderOSM {

  val BASE_URL = "https://nominatim.openstreetmap.org/reverse"


  def find(long: Double, lat: Double) = {
    Try(
      Http(BASE_URL)
        .param("format", "jsonv2")
        .param("lat", lat.toString)
        .param("lon", long.toString)
        .param("zoom", 17.toString)
        .param("addressdetails", 1.toString)
        .param("extratags", 1.toString)
        .execute(is => {

          val reader = Json.createReader(is)
          val json = reader.readObject()
          json
        }).body
    )
  }

  def main(args: Array[String]): Unit = {
    println(ReverseGeoCoderOSM.find(11.13813, 46.04137))
  }
}
