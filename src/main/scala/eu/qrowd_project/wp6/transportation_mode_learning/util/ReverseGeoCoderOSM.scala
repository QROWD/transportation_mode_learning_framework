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
        .param("zoom", 15.toString)
        .param("addressdetails", 1.toString)
        .param("extratags", 1.toString)
        .execute(is => {

          val reader = Json.createReader(is)
          val json = reader.readObject()
          json
        }).body
    )
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
