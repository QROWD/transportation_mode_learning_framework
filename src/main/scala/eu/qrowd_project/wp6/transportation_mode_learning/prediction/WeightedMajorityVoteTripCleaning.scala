package eu.qrowd_project.wp6.transportation_mode_learning.prediction

import java.sql.Timestamp
import java.time.LocalDateTime

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Trip

/**
  * Weighted majority voting based cleaning of a sequence of transportation modes.
  * The weight of each element in the window
  *
  * step = 1 -> single element taken into account and replaced
  * step > 1 -> multiple elements taken into account and replaced
  * step = window -> the whole window taken into account and replaced, no overlap
  *
  * @param window the number of elements taken into account before and after each element
  * @param iterations number of iterations the whole sequence will be cleaned
  * @param step the step size when sliding over the sequence
  *
  * @author Lorenz Buehmann
  */
class WeightedMajorityVoteTripCleaning(window: Int, iterations: Int = 1, step: Int = 1)
  extends MajorityVoteTripCleaning(window, iterations, step) {

  override val logger = com.typesafe.scalalogging.Logger("WeightedMajorityVoteTripCleaning")

  override def singleCleanStep(trip: Trip, modes: Seq[(String, Double, Timestamp)]): (Trip, Seq[(String, Double, Timestamp)]) = {

    // sliding window and keep majority
    val cleanedModes = modes
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


