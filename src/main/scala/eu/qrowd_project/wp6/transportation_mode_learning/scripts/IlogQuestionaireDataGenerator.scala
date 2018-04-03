package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.{BufferedWriter, FileWriter}

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.LocationDataAnalyzer.jsonDir
import eu.qrowd_project.wp6.transportation_mode_learning.util._
import javax.json.Json
import javax.json.stream.JsonGenerator

/**
  * @author Lorenz Buehmann
  */
object IlogQuestionaireDataGenerator extends JSONExporter {

  val poiRetrieval = POIRetrieval("http://linkedgeodata.org/sparql")
  val tripDetector = new TripDetection()

  def main(args: Array[String]): Unit = {

    val date = "20180330"
    val path = "/tmp/questionaire_20180330.json"

    // connect to Trento Cassandra DB
    val cassandra = CassandraDBConnector()

    // get the data for the given day, i.e. (user, entries)
    val data = cassandra.readData(date)

    // detect trips per each user
    val result = data.flatMap {
      case Seq(userId: String, entries: Seq[LocationEventRecord]) =>
        // extract GPS data
        val trajectory = entries.map(e => TrackPoint(e.latitude, e.longitude, e.timestamp))

        // find trips (start, end, trace)
        val trips = tripDetector.find(trajectory)

        // get possible POIs at start and end of trip
        trips.map(trip => {
          val poisStart = poiRetrieval.getPOIsAt(trip._1, 0.1)
          val poisEnd = poiRetrieval.getPOIsAt(trip._2, 0.1)

          (userId, trip._1, poisStart.head, trip._2, poisEnd.head)
        })
    }

    // write to JSON
    val json = Json.createArrayBuilder()
    result.foreach {
      case (userId: String, start: TrackPoint, startPOI: POI, end: TrackPoint, endPOI: POI) =>
        val points = Json.createArrayBuilder()
          .add(Json.createObjectBuilder()
            .add("point", Json.createArrayBuilder().add(start.long).add(start.lat))
            .add("address", startPOI.label)
            .add("datetime", start.timestamp.toString)
          )
          .add(Json.createObjectBuilder()
            .add("point", Json.createArrayBuilder().add(end.long).add(end.lat))
            .add("address", endPOI.label)
            .add("datetime", end.timestamp.toString)
          )
        json.add(Json.createObjectBuilder()
          .add("uuid", userId)
          .add("points", points))

    }
    write(json.build(), path)

  }

}
