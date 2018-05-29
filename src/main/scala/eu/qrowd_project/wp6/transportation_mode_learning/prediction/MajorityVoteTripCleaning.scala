package eu.qrowd_project.wp6.transportation_mode_learning.prediction

import java.sql.Timestamp
import java.time.LocalDateTime

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Trip

/**
  * Majority voting based cleaning of a sequence of transportation modes.
  *
  * @param window the number of elements taken into account before and after each element
  * @author Lorenz Buehmann
  */
class MajorityVoteTripCleaning(window: Int, iterations: Int = 1) extends TripCleaning {

  private val dummyElement = ("NONE", -1.0, Timestamp.valueOf(LocalDateTime.now()))

  override def clean(trip: Trip, modes: Seq[(String, Double, Timestamp)]): (Trip, Seq[(String, Double, Timestamp)]) = {
    var tmp = (trip: Trip, modes: Seq[(String, Double, Timestamp)])
    for(i <- 1 to iterations) {
      tmp = singleCleanStep(tmp._1, tmp._2)
    }
    tmp
  }

  def singleCleanStep(trip: Trip, modes: Seq[(String, Double, Timestamp)]): (Trip, Seq[(String, Double, Timestamp)]) = {
    // add dummy elements to begin and end of the list of modes
    val extendedModes = List.fill(window)(dummyElement) ++ modes ++ List.fill(window)(dummyElement)

    // sliding window and keep majority
    val cleanedModes = extendedModes
      .sliding(window * 2 + 1)    // n elements before + the current element + n elements after
      .map(majority) // majority voting, but timestamp taken from middle element
      .toSeq

    assert(cleanedModes.size == modes.size)

    (trip, cleanedModes)
  }


  private def majority(values: Seq[(String, Double, Timestamp)]) = {
    val bestMode =
      values
        .filter(v => v != dummyElement) // omit dummy elements
        .groupBy(_._1)
        .mapValues(_.size)
        .maxBy(_._2)._1

    // take the middle element
    val anchorElt = values(window)

    val probability = if(bestMode == anchorElt._1) anchorElt._2 else 0.000001

//    println(values + "=>" + (bestMode, probability, anchorElt._3))
    (bestMode, probability, anchorElt._3)
  }

}

object MajorityVoteTripCleaning {
  def apply(window: Int, iterations: Int = 1): MajorityVoteTripCleaning = new MajorityVoteTripCleaning(window, iterations)
}
