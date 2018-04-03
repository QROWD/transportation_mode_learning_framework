package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.sql.Timestamp

import com.google.common.collect.ComparisonChain

/**
  * @author Lorenz Buehmann
  */
class TrackPoint(lat: Double, long: Double, _timestamp: Timestamp) extends Point(lat, long) with Ordered[TrackPoint] {
  def timestamp = _timestamp

  override def toString = s"$timestamp, ($lat, $long)"

  override def canEqual(a: Any): Boolean = a.isInstanceOf[TrackPoint]
  override def equals(that: Any): Boolean =
  that match {
    case that: TrackPoint => that.canEqual(this) && this.hashCode == that.hashCode
    case _ => false
  }
  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + super.hashCode()
    result = prime * result + (if (_timestamp == null) 0 else _timestamp.hashCode)
    result
  }

  override def compare(that: TrackPoint): Int = {
    ComparisonChain.start()
      .compare(this.timestamp, that.timestamp)
      .compare(this.lat, that.lat)
      .compare(this.long, that.long)
      .result()
  }

}

object TrackPoint {
  def apply(lat: Double, long: Double, timestamp: Timestamp) = new TrackPoint(lat, long, timestamp)
}
