package eu.qrowd_project.wp6.transportation_mode_learning.prediction

import java.sql.Timestamp

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Trip

/**
  * Cleaning of a sequence of modes comprising a single trip.
  *
  * @author Lorenz Buehmann
  */
trait TripCleaning {

  def clean(trip: Trip, modes: Seq[(String, Double, Timestamp)]): (Trip, Seq[(String, Double, Timestamp)])

}
