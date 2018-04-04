package eu.qrowd_project.wp6.transportation_mode_learning.util

import org.aksw.jena_sparql_api.core.{FluentQueryExecutionFactory, QueryExecutionFactory}
import org.apache.jena.query.{ParameterizedSparqlString, ResultSetCloseable, ResultSetFactory, ResultSetFormatter}
import scala.collection.JavaConversions._

/**
  * @author Lorenz Buehmann
  */
class POIRetrieval(val url: String) {

  val logger = com.typesafe.scalalogging.Logger("POI Retrieval (SPARQL@LGD)")

  lazy val qef: QueryExecutionFactory = FluentQueryExecutionFactory
    .http(url)
    .create()

  lazy val queryTemplate = new ParameterizedSparqlString(
    """
      |Prefix lgdo:<http://linkedgeodata.org/ontology/>
      |Prefix geom:<http://geovocab.org/geometry#>
      |Prefix ogc: <http://www.opengis.net/ont/geosparql#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |SELECT ?s ?l ?type (<bif:st_x>(?point) as ?long) (<bif:st_y>(?point) as ?lat) WHERE {
      |    ?s a lgdo:Amenity ; a <http://linkedgeodata.org/meta/Node> ;
      |    rdfs:label ?l ;
      |    geom:geometry [
      |        ogc:asWKT ?point
      |    ] ;
      |    a ?type . ?type rdfs:subClassOf lgdo:Amenity
      |    FILTER(<bif:st_intersects>(?point, <bif:st_point> (?p_long, ?p_lat), 0.1))
      |    FILTER(?type != lgdo:Amenity)
      |}
      |LIMIT 10
    """.stripMargin)

  /**
    * Get surrounding POIs given a point and an optional distance radius
    * @param point
    * @param radius
    */
  def getPOIsAt[P <: Point](point: P, radius: Double): Seq[POI] = {
    getPOIsAt(point.long, point.lat, radius)
  }

  /**
    * Get surrounding POIs given coordinates and an optional distance radius
    * @param lat
    * @param long
    * @param radius
    */
  def getPOIsAt(long: Double, lat: Double, radius: Double): Seq[POI] = {
    queryTemplate.clearParams()
    queryTemplate.setLiteral("p_long", long)
    queryTemplate.setLiteral("p_lat", lat)

    logger.info(s"running query\n $queryTemplate")
    TryWith(ResultSetCloseable.closeableResultSet(qef.createQueryExecution(queryTemplate.asQuery())))({ rs =>
      val pois = ResultSetFormatter.toList(rs).map(qs =>
        POI(
          qs.getResource("s").getURI,
          qs.getLiteral("l").getLexicalForm,
          qs.getResource("type").getURI,
          qs.getLiteral("long").getDouble,
          qs.getLiteral("lat").getDouble))
      logger.info(s"got ${pois.size} POI candidates")

      pois
    })
      .getOrElse(Seq())

  }

}

case class POI(uri: String, label: String, cls: String, long: Double, lat: Double)

object POIRetrieval {
  def apply(url: String): POIRetrieval = new POIRetrieval(url)
}
