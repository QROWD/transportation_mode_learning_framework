package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import java.io.{BufferedReader, ByteArrayInputStream, InputStreamReader}
import java.net.URL
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatterBuilder
import java.util.stream.Collectors

import scala.util.Try

import eu.qrowd_project.wp6.transportation_mode_learning.util.{TrackPoint, TryWith}
import io.jenetics.jpx.GPX
import org.apache.http.impl.client.HttpClientBuilder
import scalaj.http.Http

/**
  * Communicate with the Graphhopper server via HTTP.
  *
  * @param url the service URL
  */
class GraphhopperPublicTransitMapMatcherHttp(val url: String) extends GraphhopperMapMatchingService {
  val logger = com.typesafe.scalalogging.Logger("Graphhopper Public Transit Map Matcher")

  private lazy val httpClient = HttpClientBuilder.create().build()
  val formatter = new DateTimeFormatterBuilder()
    .appendPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    .toFormatter

  def query(trajectory: Seq[TrackPoint], vehicle: Option[String] = None): Option[GPX] = {
    var request = Http(url)
      .param(RoutingAPIParams.TYPE, "gpx")
      .param(RoutingAPIParams.POINT, s"${trajectory.head.lat},${trajectory.head.long}")
      .param(RoutingAPIParams.POINT, s"${trajectory.last.lat},${trajectory.last.long}")
      .param(RoutingAPIParams.EARLIEST_DEPARTURE_TIME, trajectory.head.timestamp.toLocalDateTime.format(formatter))

    if(vehicle.nonEmpty) request = request.param(RoutingAPIParams.VEHICLE, vehicle.get)

    logger.debug(s"routing request: ${new URL(request.urlBuilder.apply(request))}")
    Try(
      request
        .execute(is => {
          TryWith(new BufferedReader(new InputStreamReader(is))) { br =>
            br.lines.collect(Collectors.joining("\n"))
          }
        })
        .body.toOption
        .map(s => TryWith(new ByteArrayInputStream(s.getBytes)) {is =>
          GPX.read(is)
        }.toOption.get)
    ).get
  }

  def shutdown(): Unit = {
    httpClient.close()
  }

  object RoutingAPIParams {
    val POINT = "point"
    val VEHICLE = "vehicle"
    val EARLIEST_DEPARTURE_TIME = "pt.earliest_departure_time"
    val TYPE = "type"
  }

  /**
    * Takes a bunch of location points as input and returns a GPX XML object which denotes the
    * matching path in the map
    *
    *
    * Input format: `{"id":"x001","time":1410324847000,"point":"POINT (11.564388282625075 48.16350662940509)"}`
    * Output format (GPX):
    *
    * @param input the input data
    * @return the matching GPX XML object
    */
  override def query(input: String): Option[GPX] = throw new UnsupportedOperationException("not implemented")
}

object GraphhopperPublicTransitMapMatcherHttp {

  def main(args: Array[String]): Unit = {
    import java.time.format.DateTimeFormatter
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val data = Seq(
      TrackPoint(46.06175, 11.12376, Timestamp.valueOf(LocalDateTime.parse("2018-07-10 10:48:00", formatter))),
      TrackPoint(46.06481, 11.12336, Timestamp.valueOf(LocalDateTime.parse("2018-07-10 10:49:00", formatter)))
    )
    val response = new GraphhopperPublicTransitMapMatcherHttp(url = "http://localhost:8989/route").query(data, Some("train"))
    print( response)
  }
}



