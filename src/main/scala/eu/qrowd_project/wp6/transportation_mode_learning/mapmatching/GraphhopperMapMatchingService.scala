package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import io.jenetics.jpx.GPX
import javax.json.JsonObject


/**
  * An experimental class to analyze the Graphhopper map matching framework.
  * (the service must be running, see the
  * Github documentation (https://github.com/graphhopper/map-matching))
  *
  * @author Lorenz Buehmann
  */
trait GraphhopperMapMatchingService extends MapMatchingService[String, Option[GPX]]{

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
  def query(input: String): Option[GPX]

}
