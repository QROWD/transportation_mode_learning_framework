package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.util

import eu.qrowd_project.wp6.transportation_mode_learning.util.TrackPoint
import org.apache.commons.math3.ml.clustering.Cluster

trait Clusterer {

  def cluster (points : Seq[TrackPoint]) : Seq[Seq[TrackPoint]]

}
