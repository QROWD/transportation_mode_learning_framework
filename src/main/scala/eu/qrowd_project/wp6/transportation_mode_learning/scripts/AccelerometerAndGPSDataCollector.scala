package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.io.File

import eu.qrowd_project.wp6.transportation_mode_learning.util.CassandraDBConnector

object AccelerometerAndGPSDataCollector {
  def main(args: Array[String]): Unit = {
    val cassandra = new CassandraDBConnector()

    val user = args(0)
    val date = args(1)
    val outputFileDir = args(2)
    val accDataOutFile = new File(outputFileDir, "accelerometer.csv")
    val gpsDataOutFile = new File(outputFileDir, "gps.csv")

    cassandra.writeAccelerometerDataToCSV(user, date, accDataOutFile)
    cassandra.writeGPSDataToCSV(user, date, gpsDataOutFile)

    cassandra.close()
  }
}
