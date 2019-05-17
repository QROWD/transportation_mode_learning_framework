package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

import javax.json.{Json, JsonObject}
import scalaj.http.Http
import java.util.concurrent.Executors

import scala.util.Try

import org.aksw.jena_sparql_api.delay.extra.DelayerDefault

/**
  * A Nominatim based reverse Geo coder.
  *
  * @param baseURL base URL of Nominatim service
  * @param delayDuration duration of delay between requests
  * @param delayTimeUnit the time unit of the delay
  */
class ReverseGeoCoderOSM(
                          val baseURL: String = "https://nominatim.openstreetmap.org/reverse",
                          delayDuration: Long = 1,
                          delayTimeUnit: TimeUnit = TimeUnit.SECONDS)
  extends DelayerDefault(delayTimeUnit.toMillis(delayDuration)) {

  val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor


  def find(long: Double, lat: Double): Try[JsonObject] = {
    doDelay()

    Try(
      Http(baseURL)
        .param("format", "jsonv2")
        .param("lat", lat.toString)
        .param("lon", long.toString)
        .param("zoom", 16.toString)
        .param("addressdetails", 1.toString)
        .param("extratags", 1.toString)
        .execute(is => {

          val reader = Json.createReader(is)
          val json = reader.readObject()
          json
        }).body
    )
  }

  def addressLookup(long: Double, lat: Double): String = {
    // address retrieval (reverse geo-coding
    val json = find(long, lat)
    var label: String = ""

    /* e.g.:
     * {
     *   "place_id":84294074,
     *   "licence":"Data © OpenStreetMap contributors, ODbL 1.0. https://osm.org/copyright",
     *   "osm_type":"way",
     *   "osm_id":34991542,
     *   "lat":"46.1008776000705",
     *   "lon":"11.1009310570506",
     *   "place_rank":26,
     *   "category":"highway",
     *   "type":"residential",
     *   "importance":"0.1",
     *   "addresstype":"road",
     *   "name":"Via Luigi Caneppele",
     *   "display_name":"Via Luigi Caneppele, Canova, Trento, Territorio Val d'Adige, TN, TAA, 38121, Italia",
     *   "address":{
     *       "road":"Via Luigi Caneppele",
     *       "suburb":"Canova",
     *       "city":"Trento",
     *       "county":"Territorio Val d'Adige",
     *       "state":"TAA",
     *       "postcode":"38121",
     *       "country":"Italia",
     *       "country_code":"it"},
     *   "extratags":{},
     *   "boundingbox":["46.1008156","46.1010727","11.0995794","11.1017272"]
     * }
     */
    try {
      label = json.get.getString("name")
    } catch {
      case e: ClassCastException =>
        label = json.get.getString("display_name")
    }

    if(json.isSuccess) {
      label
    } else {
      "UNKNOWN_ADDRESS"
    }
  }
}

/**
  * @author Lorenz Buehmann
  */
object ReverseGeoCoderOSM {

  def apply(
             baseURL: String = "https://nominatim.openstreetmap.org/reverse",
             delayDuration: Long = 1,
             delayTimeUnit: TimeUnit = TimeUnit.SECONDS)
  : ReverseGeoCoderOSM = new ReverseGeoCoderOSM(baseURL)


  def main(args: Array[String]): Unit = {
    println(ReverseGeoCoderOSM().find(11.13813, 46.04137))
  }
}
