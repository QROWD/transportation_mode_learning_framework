package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import scala.collection.mutable

import com.datastax.driver.core.Row

/**
  * @author Lorenz Buehmann
  */
case class LocationEventRecord(day: String,
                               timestamp: Timestamp,
                               accuracy: Float,
                               bearing: Double,
                               latitude: Double,
                               longitude: Double,
                               altitude: Double,
                               provider: String,
                               speed: Float) {

}

object LocationEventRecord {
  /**
    * Returns an entry from a Cassandra DB row.
    * @param row the Cassandra DB row
    * @return a location event record
    */
  def from(row: Row): LocationEventRecord = {
    val day = row.getString("day")
    val timestamp = asTimestamp(row.getString("timestamp"))
    val accuracy = row.getFloat("accuracy")
    val bearing = row.getDouble("bearing")
    val point = new org.json.JSONObject(row.getObject("point").toString)
    val latitude = point.getDouble("latitude")
    val longitude = point.getDouble("longitude")
    val altitude = point.getDouble("altitude")
    val provider = row.getString("provider")
    val speed = row.getFloat("speed")

    LocationEventRecord(day, timestamp, accuracy, bearing, latitude, longitude, altitude, provider, speed)
  }

  /**
    * Returns an entry from an Eels API row.
    * @param row the Eels API row
    * @return a location event record
    */
  def from(row: io.eels.Row): LocationEventRecord = {
    val day = row.getAs[String]("day")
    val timestamp = asTimestamp(row.getAs[String]("timestamp"))
    val accuracy = row.getAs[Float]("accuracy")
    val bearing = row.getAs[Double]("bearing")
    val point = row.getAs[mutable.WrappedArray[Double]]("point")
    val latitude = point(0)
    val longitude = point(1)
    val altitude = point(2)
    val provider = row.getAs[String]("provider")
    val speed = row.getAs[Float]("speed")

    LocationEventRecord(day, timestamp, accuracy, bearing, latitude, longitude, altitude, provider, speed)
  }

  // this DateTimeFormatter bug was fixed somewhere in Java 9
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
  private def asTimestamp(timestamp :String) =
    Timestamp.valueOf(LocalDateTime.parse(timestamp, dateTimeFormatter))
//    Timestamp.valueOf(LocalDateTime.parse(timestamp.substring(0, 14), dateTimeFormatter))

}
