package eu.qrowd_project.wp6.transportation_mode_learning.prediction

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.TimeUnit

import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Trip
import eu.qrowd_project.wp6.transportation_mode_learning.util.window.Window

/**
  * Majority voting based cleaning of a sequence of transportation modes.
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
class MajorityVoteTripCleaning(window: Window, iterations: Int = 1, step: Int = 1)
  extends TripCleaning {

  val logger = com.typesafe.scalalogging.Logger("MajorityVoteTripCleaning")

  val dummyElement = ("NONE", -1.0, Timestamp.valueOf(LocalDateTime.now()))

  val dropStillMode = true

  override def clean(trip: Trip,
                     modes: Seq[(String, Double, Timestamp)],
                     modeProbabilities: ModeProbabilities): (Trip, Seq[(String, Double, Timestamp)]) = {
    logger.info("cleaning mode sequence...")

    logger.info(s"window size = ${window.numEntries}")

    var tmp = (trip, modes)

    for(i <- 1 to iterations) {
      logger.info(s"iteration $i")
      tmp = singleCleanStep(tmp._1, tmp._2, modeProbabilities)
    }
    logger.info("done.")
    tmp
  }

  def singleCleanStep(trip: Trip,
                      modes: Seq[(String, Double, Timestamp)],
                      modeProbabilities: ModeProbabilities): (Trip, Seq[(String, Double, Timestamp)]) = {
    // add dummy elements to begin and end of the list of modes
    val extendedModes = padding(modes, dummyElement)

    // sliding window and keep majority
    val cleanedModes = extendedModes
      .sliding(window.numEntries * 2 + 1)    // n elements before + the current element + n elements after
      .map(majority) // majority voting, but timestamp taken from middle element
      .toSeq

    assert(cleanedModes.size == modes.size)

    (trip, cleanedModes)
  }

  def window(timeValue: Long,
             timeUnit: TimeUnit = TimeUnit.SECONDS,
             resolutionValue: Long = 50,
             resolutionTimeUnit: TimeUnit = TimeUnit.MILLISECONDS) =
    timeUnit.toMillis(timeValue) / resolutionTimeUnit.toMillis(resolutionValue)

  /**
    * Do padding left and right side of the sequence with the given element.
    */
  def padding[A](seq: Seq[A], elt: A): Seq[A] = List.fill(window.numEntries)(elt) ++ seq ++ List.fill(window.numEntries)(elt)

  private def majority(values: Seq[(String, Double, Timestamp)]) = {

    val mode2Frequency = values
      .filter(v => v != dummyElement) // omit dummy elements
      .groupBy(_._1)
      .mapValues(_.size)

    val bestMode = if (dropStillMode) {
      // keep "still" if there is no other mode in the window
      if(mode2Frequency.size == 1 && mode2Frequency.contains("still")) {
        "still"
      } else { // drop "still"
        mode2Frequency
          .filter(_._1 != "still")
          .maxBy(_._2)._1
      }
    } else {
      mode2Frequency.maxBy(_._2)._1
    }

    // take the middle element
    val anchorElt = values(window.numEntries)

    val probability = if (bestMode == anchorElt._1) anchorElt._2 else 0.000001

    //    println(values + "=>" + (bestMode, probability, anchorElt._3))
    (bestMode, probability, anchorElt._3)
  }

//  def singleCleanStepBatched(trip: Trip,
//                      modes: Seq[(String, Double, Timestamp)],
//                      modeProbabilities: ModeProbabilities): (Trip, Seq[(String, Double, Timestamp)]) = {
//    // add dummy elements to begin and end of the list of modes
//    val extendedModes = padding(modes, dummyElement)
//
//    // sliding window and keep majority
//    val cleanedModes = extendedModes
//      .sliding(window, step = window)    // n elements before + the current element + n elements after
//      .map(majorityBatch) // majority voting, but timestamp taken from middle element
//      .toSeq
//
//
//  }

  private def majorityBatch(values: Seq[(String, Double, Timestamp)]): Seq[(String, Double, Timestamp)] = {

    val mode2Frequency = values
      .filter(v => v != dummyElement) // omit dummy elements
      .groupBy(_._1)
      .mapValues(_.size)

    val bestMode = if (dropStillMode) {
      // keep "still" if there is no other mode in the window
      if(mode2Frequency.size == 1 && mode2Frequency.contains("still")) {
        "still"
      } else { // drop "still"
        mode2Frequency
          .filter(_._1 != "still")
          .maxBy(_._2)._1
      }
    } else {
      mode2Frequency.maxBy(_._2)._1
    }

    values.map {
      case (mode, p, t) => (bestMode, p, t)
    }
  }

}

object MajorityVoteTripCleaning {
  def apply(window: Window, iterations: Int = 1): MajorityVoteTripCleaning =
    new MajorityVoteTripCleaning(window, iterations)
}
