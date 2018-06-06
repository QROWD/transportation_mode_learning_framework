package eu.qrowd_project.wp6.transportation_mode_learning.util

import scala.collection.mutable

import org.aksw.jena_sparql_api.core.FluentQueryExecutionFactory
import org.apache.jena.query.QuerySolution

/**
  * Utility class to get locations from LinkedGeoData KB.
  *
  * @author Lorenz Buehmann
  */
class GeoLocationRetrieval(val endpointURL: String) {

  val qef = FluentQueryExecutionFactory
    .http(endpointURL)
    .create()

  def executeQuery[A](query: String, f: QuerySolution => A): Seq[A] = {
    val qe = qef.createQueryExecution(query)
    val rs = qe.execSelect()
    var res = mutable.Seq[A]()
    while(rs.hasNext) {
      res :+= f(rs.next())
    }
    qe.close()
    res
  }

  def getLocations(cls: String) = {
    val query =
      s"""
        |select * where {
        |?s a <$cls> ;
        |rdfs:label ?l ;
        |<http://geovocab.org/geometry#geometry>/<http://www.opengis.net/ont/geosparql#asWKT> ?geo
        |filter(lang(?l) = "")
        |}
      """.stripMargin

    val locations = executeQuery(query,
      qs => Location(cls,
                    qs.getLiteral("l").getLexicalForm,
                    qs.getLiteral("geo").getLexicalForm))

    locations
  }

  def getRailwayStations() = {
    getLocations("http://linkedgeodata.org/ontology/RailwayStation")
  }

  def getBusStations() = {
    getLocations("http://linkedgeodata.org/ontology/BusStation")
  }

}

object GeoLocationRetrieval {
  def main(args: Array[String]): Unit = {
    println(new GeoLocationRetrieval("http://rdf.qrowd.aksw.org/sparql").getRailwayStations().mkString("\n"))
  }
}

case class Location(locationType: String, label: String, geoObject: String)
