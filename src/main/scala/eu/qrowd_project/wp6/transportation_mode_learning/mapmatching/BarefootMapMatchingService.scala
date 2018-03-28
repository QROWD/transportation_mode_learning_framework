package eu.qrowd_project.wp6.transportation_mode_learning.mapmatching

import javax.json.JsonObject


/**
  * An experimental class to analyze the Barefoot map matching framework. (the Docker service must be running, see the
  * Github documentation (https://github.com/bmwcarit/barefoot/wiki))
  *
  * @author Lorenz Buehmann
  */
trait BarefootMapMatchingService extends MapMatchingService[String, Option[JsonObject]]{

  /**
    * Takes a bunch of location points as input and returns a GeoJSON object which denotes the matching path in the map
    *
    *
    * Input format: `{"id":"x001","time":1410324847000,"point":"POINT (11.564388282625075 48.16350662940509)"}`
    * Output format (GeoJSON): `{"coordinates":[
    *                                 [[11.437501291320407,48.12671481654555],[52424546,48.127696897407596]],
    *                                 [[11.435556752424546,48.127696897407596],[11.435556752424546,48.127696897407596]]
    *                                 ],"type":"MultiLineString"}`
    * @param input the input data
    * @return the matching GeoJSON object
    */
  def query(input: String): Option[JsonObject]

}
