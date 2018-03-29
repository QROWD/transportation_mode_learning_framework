package eu.qrowd_project.wp6.transportation_mode_learning.util

import com.google.common.net.UrlEscapers

/**
  * @author Lorenz Buehmann
  */
object GeoJSONIoRenderer {

  val GEOJSON_IO_URL = "http://geojson.io"

  def url(points: Seq[TrackPoint], format: String): String = {
    val json = GeoJSONConverter.convert(points, format)

    UrlEscapers.urlFragmentEscaper().escape(s"$GEOJSON_IO_URL/#data=data:application/json,${json.toString}")
  }

}
