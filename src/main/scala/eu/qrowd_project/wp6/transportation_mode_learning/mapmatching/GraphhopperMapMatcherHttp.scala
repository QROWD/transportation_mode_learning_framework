package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import scala.util.Try

import eu.qrowd_project.wp6.transportation_mode_learning.util.{GPXConverter, TrackPoint, TryWith}
import org.apache.http.impl.client.HttpClientBuilder
import scalaj.http.Http
import java.io.{BufferedReader, ByteArrayInputStream, InputStreamReader}
import java.util.stream.Collectors

import io.jenetics.jpx.GPX

/**
  * Communicate with the Graphhopper server via HTTP.
  *
  * @param url the service URL
  */
class GraphhopperMapMatcherHttp(val url: String) extends GraphhopperMapMatchingService {
  val logger = com.typesafe.scalalogging.Logger("Graphhopper Map Matcher")

  private lazy val httpClient = HttpClientBuilder.create().build()

  override def query(data: String): Option[GPX] = request(data)

  def query(trajectory: Seq[TrackPoint]): Option[GPX] = request(GPXConverter.toGPXString(trajectory))

  private def request(data: String): Option[GPX] = {
    Try(
      Http(url)
        .param("type", "gpx")
        .postData(data)
        .header("Content-Type", "application/gpx+xml")
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
}

object GraphhopperMapMatcherHttp {
  def main(args: Array[String]): Unit = {

    val data =
      """<?xml version="1.0"?>
        |<gpx version="1.1" creator="GDAL 2.1.3" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:ogr="http://osgeo.org/gdal" xmlns="http://www.topografix.com/GPX/1/1" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
        |<metadata><bounds minlat="46.064770000000003" minlon="11.124969999999999" maxlat="46.073270000000001" maxlon="11.150449999999999"/></metadata>
        |<trk>
        |  <extensions>
        |    <ogr:timestamp-start>2018/04/06 08:14:11</ogr:timestamp-start>
        |    <ogr:timestamp-end>2018/04/06 08:38:38</ogr:timestamp-end>
        |  </extensions>
        |  <trkseg>
        |    <trkpt lat="46.07327" lon="11.12497">
        |    </trkpt>
        |    <trkpt lat="46.07222" lon="11.12525">
        |    </trkpt>
        |    <trkpt lat="46.07164" lon="11.12614">
        |    </trkpt>
        |    <trkpt lat="46.07094" lon="11.12658">
        |    </trkpt>
        |    <trkpt lat="46.06842" lon="11.12797">
        |    </trkpt>
        |    <trkpt lat="46.06996" lon="11.12709">
        |    </trkpt>
        |    <trkpt lat="46.06965" lon="11.12794">
        |    </trkpt>
        |    <trkpt lat="46.06755" lon="11.12763">
        |    </trkpt>
        |    <trkpt lat="46.0666" lon="11.13161">
        |    </trkpt>
        |    <trkpt lat="46.06749" lon="11.13621">
        |    </trkpt>
        |    <trkpt lat="46.07015" lon="11.13679">
        |    </trkpt>
        |    <trkpt lat="46.06969" lon="11.13645">
        |    </trkpt>
        |    <trkpt lat="46.06876" lon="11.14052">
        |    </trkpt>
        |    <trkpt lat="46.06714" lon="11.13961">
        |    </trkpt>
        |    <trkpt lat="46.06608" lon="11.14055">
        |    </trkpt>
        |    <trkpt lat="46.06477" lon="11.14262">
        |    </trkpt>
        |    <trkpt lat="46.06525" lon="11.14851">
        |    </trkpt>
        |    <trkpt lat="46.06535" lon="11.15038">
        |    </trkpt>
        |    <trkpt lat="46.06701" lon="11.15045">
        |    </trkpt>
        |    <trkpt lat="46.06689" lon="11.15">
        |    </trkpt>
        |    <trkpt lat="46.06707" lon="11.15012">
        |    </trkpt>
        |    <trkpt lat="46.06712" lon="11.14998">
        |    </trkpt>
        |    <trkpt lat="46.06722" lon="11.14963">
        |    </trkpt>
        |    <trkpt lat="46.06722" lon="11.14963">
        |    </trkpt>
        |    <trkpt lat="46.06723" lon="11.14963">
        |    </trkpt>
        |    <trkpt lat="46.07103" lon="11.14747">
        |    </trkpt>
        |    <trkpt lat="46.06721" lon="11.1496">
        |    </trkpt>
        |    <trkpt lat="46.07103" lon="11.14747">
        |    </trkpt>
        |    <trkpt lat="46.06721" lon="11.14959">
        |    </trkpt>
        |  </trkseg>
        |</trk>
        |</gpx>
      """.stripMargin

    val response = new GraphhopperMapMatcherHttp(url = "http://localhost:8989/match").query(data)
    print(response)
  }
}

