package eu.qrowd_project.wp6.transportation_mode_learning.learning.dataset

import org.apache.spark.sql

/**
  * A dataset whose instances are labeled.
  *
  * @author Lorenz Buehmann
  */
trait LabeledDataset extends sql.Dataset[String] with Dataset {

}
