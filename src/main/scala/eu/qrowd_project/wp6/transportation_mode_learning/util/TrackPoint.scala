package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.sql.Timestamp

/**
  * @author Lorenz Buehmann
  */
class TrackPoint(lat: Double, long: Double, _timestamp: Timestamp) extends Point(lat, long) {
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

}

object TrackPoint {
  def apply(lat: Double, long: Double, timestamp: Timestamp) = new TrackPoint(lat, long, timestamp)
}
