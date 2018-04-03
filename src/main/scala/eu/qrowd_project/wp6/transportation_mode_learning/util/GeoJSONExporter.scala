package eu.qrowd_project.wp6.transportation_mode_learning.util

/**
  * Export a GPS trajectory to GeoJSON file.
  *
  * @author Lorenz Buehmann
  */
object GeoJSONExporter extends JSONExporter {

  /**
    * Export a GPS trajectory to GeoJSON file.
    *
    * @param points the GPS trajectory
    * @param format the GeoJSON format, either 'points' or 'linestring'
    * @param path path to export file
    * @return the GeoJSON object
    */
  def export(points: Seq[TrackPoint], format: String, path: String): Unit = {
    write(GeoJSONConverter.convert(points, format), path)
  }
}
