package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import java.io.{OutputStreamWriter, _}
import java.net.Socket
import java.nio.charset.StandardCharsets

import eu.qrowd_project.wp6.transportation_mode_learning.util.TryWith
import javax.json.{Json, JsonObject}

/**
  * Communicate with the Barefoot Map server via a socket stream.
  *
  * @param host the socket URL
  * @param port the socket port
  */
class BarefootMapMatcherSocket(val host: String, val port: Int) extends BarefootMapMatchingService {
  val logger = com.typesafe.scalalogging.Logger("Barefoot Map Matcher")

  override def query(json: String): Option[JsonObject] = request(json)

  private def request(json: String): Option[JsonObject] = {

    // generate the request JSON document
    val jsonDoc = Json.createObjectBuilder()
      .add("format", "geojson")
      .add("request", Json.createReader(new ByteArrayInputStream(json.getBytes())).readArray())
      .build()

    // open socket
    TryWith(new Socket(host, port))({ socket =>
      var res: Option[JsonObject] = None

      // send request
      TryWith(new OutputStreamWriter(socket.getOutputStream, StandardCharsets.UTF_8)) { out =>
        logger.info(s"Barefoot request: ${jsonDoc.toString}")
        out.write(jsonDoc.toString)
        out.write("\n")
        out.flush()

        // get response
        TryWith(new BufferedReader(new InputStreamReader(socket.getInputStream))) { in =>
          val status = in.readLine()
          if (status == "SUCCESS") {
            res = Some(Json.createReader(in).readObject())
          } else {
            logger.error(s"Barefoot Request Error: $status")
          }
        }
      }
      res
    }).get
  }
}

object BarefootMapMatcherSocket {
  def main(args: Array[String]): Unit = {

    val json =
      """
        |[{"point":"POINT(11.1498097 46.0669548)","time":"2014-09-10 07:13:22+0200","id":"\\x0001"},
        |{"point":"POINT(11.1498704 46.0669866)","time":"2014-09-10 07:13:37+0200","id":"\\x0001"},
        |{"point":"POINT(11.1498097 46.0669548)","time":"2014-09-10 07:13:52+0200","id":"\\x0001"}]
      """.stripMargin

    val response = new BarefootMapMatcherSocket(host = "127.0.0.1", port = 1234).query(json)
    print(response)
  }
}