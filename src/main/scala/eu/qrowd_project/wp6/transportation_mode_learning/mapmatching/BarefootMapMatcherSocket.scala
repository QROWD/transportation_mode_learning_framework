package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import java.io._
import java.net.Socket

import org.apache.http.NameValuePair
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicNameValuePair
import org.apache.http.entity.StringEntity
import org.apache.http.client.methods.HttpPost
import scala.collection.JavaConversions._

import com.google.gson.JsonParser
import eu.qrowd_project.wp6.transportation_mode_learning.util.Loan
import javax.json.{Json, JsonObject}
import jdk.nashorn.internal.parser.JSONParser

/**
  * Communicate with the Barefoot Map server via a socket stream.
  *
  * @param host the socket URL
  * @param port the socket port
  */
class BarefootMapMatcherSocket(val host: String, val port: Int) extends BarefootMapMatchingService {

  private val httpClient = HttpClientBuilder.create().build()

  def query(json: String): Option[JsonObject] = getRestContent(json)


  private def getRestContent(json: String): Option[JsonObject] = {

    import java.io.OutputStreamWriter
    import java.nio.charset.StandardCharsets

    val jsonDoc = Json.createObjectBuilder()
      .add("format", "geojson")
      .add("request", Json.createReader(new ByteArrayInputStream(json.getBytes())).readArray())
      .build()
    println(jsonDoc)
//
//    val jsonDoc = new JSONObject()
//    jsonDoc.put("format", "geojson")
//    val jsonData = new JsonParser().parse(new StringReader(json)).getAsJsonArray
//    println(jsonData)
//    jsonDoc.put("request", jsonData)
//
//    println(jsonDoc)

    val s = new Socket(host, port)

    val outStr: StringBuilder = new StringBuilder()

    var res: Option[JsonObject] = null
    try {
      val out = new OutputStreamWriter(s.getOutputStream, StandardCharsets.UTF_8)

      try {
        out.write(jsonDoc.toString)
        out.write("\n")
        out.flush()


        val in = new BufferedReader(new InputStreamReader(s.getInputStream))
        var line: String = null
        val status = in.readLine()
        if(status == "SUCCESS") {
          res = Some(Json.createReader(in).readObject())
//          while ({line = in.readLine; line != null}) {
//            outStr.append(line)
//          }
        } else {
          println("Error")
          res = None
        }
        in.close()

      } finally {
        if (out != null) {
          out.close()
//          in.close()
          s.close()
        }
      }
    }

    res
  }

  def shutdown(): Unit = {
    httpClient.getConnectionManager().shutdown()
    httpClient.close()
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