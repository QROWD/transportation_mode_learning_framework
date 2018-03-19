package eu.qrowd_project.wp6.transportation_mode_learning

import eu.qrowd_project.wp6.transportation_mode_learning.learning.dataset.{LabeledDataset, RawDataset}
import org.apache.spark.ml.classification.LogisticRegression

/**
  * Template for the general workflow.
  *
  * @author Lorenz Buehmann
  */
object Workflow {



  def main(args: Array[String]): Unit = {


    // load raw dataset
    val rawData: RawDataset = null

    // 1. preprocessing
    val trainingData: LabeledDataset = null

    // 2. learning
    val la = new LogisticRegression()
      .setMaxIter(10)
      .setRegParam(0.01)

    val model = la.fit(trainingData)


  }



}
