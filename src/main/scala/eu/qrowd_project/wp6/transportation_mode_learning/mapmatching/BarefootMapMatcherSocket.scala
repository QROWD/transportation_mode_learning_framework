package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import java.io.{BufferedReader, InputStreamReader, PrintWriter, StringReader}
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
import javax.json.Json
import jdk.nashorn.internal.parser.JSONParser
import org.json.JSONObject

/**
  * Communicate with the Barefoot Map server via a socket stream.
  *
  * @param host the socket URL
  * @param port the socket port
  */
class BarefootMapMatcherSocket(val host: String, val port: Int) extends BarefootMapMatchingService {

  private val httpClient = HttpClientBuilder.create().build()

  def query(json: String): JSONObject = getRestContent(json)


  private def getRestContent(json: String): JSONObject = {

    import java.io.OutputStreamWriter
    import java.nio.charset.StandardCharsets

    val jsonDoc = new JSONObject()
    jsonDoc.put("format", "geojson")
    val jsonData = new JsonParser().parse(new StringReader(json))
    jsonDoc.put("request", jsonData)

    println(jsonDoc)

    val s = new Socket(host, port)

    val outStr: StringBuilder = new StringBuilder()

    try {
      val out = new OutputStreamWriter(s.getOutputStream, StandardCharsets.UTF_8)

      try {
        out.write(jsonDoc.toString)
        out.flush()


        val in = new BufferedReader(new InputStreamReader(s.getInputStream))
        var line: String = null
        while ({line = in.readLine; line != null}) {
          outStr.append(line)
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

    new JSONObject(new StringReader(outStr.toString()))
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
        |[{"point":"POINT(11.437500417040214 48.126714126235505)","time":"2014-09-10 07:13:22+0200","id":"\\x0001"},
        |{"point":"POINT(11.435615169119298 48.127753929402985)","time":"2014-09-10 07:13:37+0200","id":"\\x0001"},
        |{"point":"POINT(11.433758971976749 48.12851044900588)","time":"2014-09-10 07:13:52+0200","id":"\\x0001"}]
      """.stripMargin

    val response = new BarefootMapMatcherSocket(host = "127.0.0.1", port = 1234).query(json)
    print(response)
  }
}