package eu.qrowd_project.wp6.transportation_mode_learning.util

import org.apache.commons.math3.ml.clustering.Clusterable

case class Point(lat: Double, long: Double) extends Clusterable {
  override def getPoint: Array[Double] = Array(lat, long)
}