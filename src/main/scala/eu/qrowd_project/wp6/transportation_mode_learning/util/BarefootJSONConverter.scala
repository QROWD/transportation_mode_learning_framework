package eu.qrowd_project.wp6.transportation_mode_learning.util

import javax.json.{Json, JsonArray}

/**
  * @author Lorenz Buehmann
  */
object BarefootJSONConverter {

  /**
    * Convert a GPS trajectory to Barefoot JSON format which expects coordinates as (long, lat):
    *
    * `{"id":"x001","time":1410324847000,"point":"POINT (11.564388282625075 48.16350662940509)"}`
    *
    * @param points the GPS trajectory
    * @return the JSON array
    */
  def convert(points: Seq[TrackPoint]): JsonArray = {
    val features = Json.createArrayBuilder()

    points.zipWithIndex.foreach{
      case(p, i) =>
        features.add(i, Json.createObjectBuilder()
          .add("id", i)
          .add("time", p.timestamp.getTime)
          .add("point", s"POINT (${p.long} ${p.lat})"))
    }

    features.build()
  }
}
