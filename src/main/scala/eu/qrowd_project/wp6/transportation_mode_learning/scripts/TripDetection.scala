package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import eu.qrowd_project.wp6.transportation_mode_learning.util.TrackPoint

class TripDetection {
  def find(trajectory: Seq[TrackPoint]): Seq[Trip] = ???

  def getSettings: Map[String, String] = ???
}
