package eu.qrowd_project.wp6.transportation_mode_learning

import eu.qrowd_project.wp6.transportation_mode_learning.learning.dataset.{LabeledDataset, RawDataset}

/**
  * @author Lorenz Buehmann
  */
abstract class Preprocessing {

  /**
    * Takes a raw dataset and returns a labeled dataset
    *
    * @param rawDataset
    * @return
    */
  def run(rawDataset: RawDataset): LabeledDataset

}
