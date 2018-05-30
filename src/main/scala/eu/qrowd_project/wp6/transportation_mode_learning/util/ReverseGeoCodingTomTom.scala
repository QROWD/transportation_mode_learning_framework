package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.{BufferedReader, File, InputStreamReader}
import javax.json.{Json, JsonObject}

import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Success, Try}
import scalaj.http.Http

trait ReverseGeoCodingTomTom {
  private val logger = com.typesafe.scalalogging.Logger("ReverseGeoCodingTomTom")

  lazy private val tomtomDevConfig = ConfigFactory.parseFile(
    new File(getClass.getClassLoader.getResource("tomtomdev.conf").toURI))
  lazy private val appConfig = ConfigFactory.load()

  private val baseURL = "api.tomtom.com"
  private val versionNumber = 2
  private val ext = "JSON"
  private val apiKey = tomtomDevConfig.getString("api_key")
  private val radius = appConfig.getInt("stop_detection.gps_accuracy")

  val uri = s"https://$baseURL/search/$versionNumber/reverseGeocode/%s.$ext?key=$apiKey&radius=$radius"

  def find(long: Double, lat: Double): Try[JsonObject] = {
    // This is specified as a comma separated string composed by lat., lon.
    // (e.g.: 37.337,-121.89).
    val geoCoordStr = s"$lat,$long"

    val queryURI = String.format(uri, geoCoordStr)
    Try(
      Http(queryURI).execute(is => {
        val reader = Json.createReader(is)
        val json = reader.readObject()

        json
      }).body
    )
  }

  def getStreet(long: Double, lat: Double): String = {
    val json = find(long, lat)

    json match {
      case Success(j) => {
//        println(j.toString)
        val addresses = j.getJsonArray("addresses")
        val firstAddress = addresses.getJsonObject(0)
        try {
          firstAddress.getJsonObject("address").getString("streetName")
        } catch {
          case _: NullPointerException => ""
        }
      }
      case Failure(e) =>
        e.printStackTrace()
        ""
    }
  }
}

//object Tester {
//  class Tester extends ReverseGeoCodingTomTom
//
//  def main(args: Array[String]): Unit = {
//    val revCoder = new Tester
//
//    println(revCoder.getStreet(13.730276, 51.039973))
//  }
//}
