package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import org.json.JSONObject

/**
  * A service for map matching, i.e. match a sequence of real world coordinates into a digital map.
  *
  * @author Lorenz Buehmann
  */
trait MapMatchingService[S, T] {


  /**
    * Takes coordinates as input, e.g. a JSON string, plain list of points, etc. and
    * returns the matched, possible encoded in a specific format, e.g. in GeoJSON
    * format.
    *
    * @param input the input coordinates
    * @return the output coordinates describing the matched object, e.g. a road
    */
  def query(input: S): T

}
