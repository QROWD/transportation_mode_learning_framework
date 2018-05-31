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
  * @param window     the number of elements taken into account before and after each element
  * @param iterations number of iterations the whole sequence will be cleaned
  * @param step       the step size when sliding over the sequence
  * @author Lorenz Buehmann
  */
class WeightedMajorityVoteTripCleaning(window: Int, iterations: Int = 1, step: Int = 1)
  extends MajorityVoteTripCleaning(window, iterations, step) {

  override val logger = com.typesafe.scalalogging.Logger("WeightedMajorityVoteTripCleaning")

  val dummyElement2 = (Timestamp.valueOf(LocalDateTime.MIN), (-1.0, -1.0 ,-1.0, -1.0, -1.0, -1.0))

  override def singleCleanStep(trip: Trip,
                               modes: Seq[(String, Double, Timestamp)],
                               modeProbabilities: ModeProbabilities): (Trip, Seq[(String, Double, Timestamp)]) = {

    // add dummy elements to begin and end of the list of modes
    val extendedModeProbabilities = padding(modeProbabilities.probabilities, dummyElement2)


    // sliding window and keep majority
    val cleanedModes = extendedModeProbabilities
      .sliding(window * 2 + 1) // n elements before + the current element + n elements after
      .map(weightedSumMajority) // majority voting, but timestamp taken from middle element
      .map { idx =>
      val bestMode = modeProbabilities.schema(idx)

      // take the middle element
      val anchorElt = extendedModeProbabilities(window)

      // highest probability of anchor elt
      val anchorIdx = anchorElt._2
        .productIterator
        .map(_.asInstanceOf[Double])
        .zipWithIndex
        .maxBy(_._1)._2

      val probability = if (anchorIdx == idx) anchorElt._2.productIterator.toSeq(idx).asInstanceOf[Double] else 0.000001

      //    println(values + "=>" + (bestMode, probability, anchorElt._3))
      (bestMode, probability, anchorElt._1)
    }.toSeq

    assert(cleanedModes.size == modes.size)

    (trip, cleanedModes)
  }

  private def weightedSumMajority(probabilities: Seq[(Timestamp, (Double, Double, Double, Double, Double, Double))])= {

    // sum of all probabilities
    val sumProbs = probabilities
      .filter(v => v != dummyElement2)
      .map (_._2)
      .foldLeft((.0, .0, .0, .0, .0, .0)) {
      case ((sum1, sum2, sum3, sum4, sum5, sum6), (p1, p2, p3, p4, p5, p6)) =>
        (sum1 + p1, sum2 + p2, sum3 + p3, sum4 + p4, sum5 + p5, sum6 + p6)
    }

    val bestModeIdx = sumProbs
      .productIterator
      .map(_.asInstanceOf[Double])
      .zipWithIndex
      .maxBy(_._1)._2

    bestModeIdx
  }

  def rr[A](elt: A, elements: Seq[A]): Double = 1 / elements.indexOf(elt)

  def mrr[A](elt: A, sequenceOfElements: Seq[Seq[A]]) = sequenceOfElements.map(elements => rr(elt, elements))

}


