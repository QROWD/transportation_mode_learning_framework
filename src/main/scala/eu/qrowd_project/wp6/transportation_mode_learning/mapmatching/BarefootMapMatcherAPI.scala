package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import java.io.{ByteArrayInputStream, File, FileOutputStream}
import java.util.Properties

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

import com.bmwcarit.barefoot.matcher.{Matcher, MatcherSample}
import com.bmwcarit.barefoot.road.BfmapReader
import com.bmwcarit.barefoot.roadmap._
import com.bmwcarit.barefoot.spatial.Geography
import com.bmwcarit.barefoot.topology.Dijkstra
import javax.json.{Json, JsonObject}
import org.json.JSONArray

/**
  * @author Lorenz Buehmann
  */
class BarefootMapMatcherAPI extends BarefootMapMatchingService {

  // Load and construct road map
  val temp = File.createTempFile("barefoot-tmp", "properties")
  val properties = new Properties()
  properties.load(getClass.getClassLoader.getResourceAsStream("barefoot/barefoot.properties"))
  properties.setProperty("database.road-types", getClass.getClassLoader.getResource("barefoot/road-types.json").getPath)
  properties.store(new FileOutputStream(temp), "header")
  //    val map = Loader.roadmap(temp.getPath, true).construct
  val map = RoadMap.Load(new BfmapReader("/home/user/work/java/barefoot/oberbayern.bfmap")).construct()

  // Instantiate matcher and state data structure
  val matcher = new Matcher(map, new Dijkstra[Road, RoadPoint], new TimePriority, new Geography)


  override def query(input: String): Option[JsonObject] = {
    // convert input JSON to sample objects
    val samples = convert(input)

    // Match full sequence of samples
    val state = matcher.mmatch(samples, 1, 500)

    // Access map matching result: sequence for all samples
    import scala.collection.JavaConversions._
    for (cand <- state.sequence) {
      cand.point.edge.base.refid // OSM id
      println("road:" + cand.point.edge.base.id) // road id
      cand.point.edge.heading // heading
      cand.point.geometry // GPS position (on the road)

      if (cand.transition != null) cand.transition.route.geometry // path geometry from last matching candidate
    }

    println(state.toSlimJSON)
    Some(Json.createReader(new ByteArrayInputStream(state.toGeoJSON.toString.getBytes())).readObject())
  }

  /**
    * Convert input JSON
    * @param json
    */
  private def convert(json: String): ListBuffer[MatcherSample] = {
    import com.bmwcarit.barefoot.matcher.MatcherSample
    val jsonsamples = new JSONArray(json)
    val samples: List[MatcherSample] =
      (for (i <- 0 until jsonsamples.length()) yield new MatcherSample(jsonsamples.getJSONObject(i))) (collection.breakOut)

    samples.to[ListBuffer]
  }

}

  object BarefootMapMatcherAPI {
    def main(args: Array[String]): Unit = {

      val json =
        """
          |[{"point":"POINT(11.437500417040214 48.126714126235505)","time":"2014-09-10 07:13:22+0200","id":"\\x0001"},
          |{"point":"POINT(11.435615169119298 48.127753929402985)","time":"2014-09-10 07:13:37+0200","id":"\\x0001"},
          |{"point":"POINT(11.433758971976749 48.12851044900588)","time":"2014-09-10 07:13:52+0200","id":"\\x0001"}]
        """.stripMargin

      val response = new BarefootMapMatcherAPI().query(json)

      println(response)
    }
  }
