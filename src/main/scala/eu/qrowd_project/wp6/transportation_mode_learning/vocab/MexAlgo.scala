package eu.qrowd_project.wp6.transportation_mode_learning.vocab

import org.apache.jena.rdf.model.{Resource, ResourceFactory}

/**
  * This vocabulary object is incomplete!!!
  *
  * TODO: Finish it!
  */
object MexAlgo {
  val ns = "http://mex.aksw.org/mex-algo#"

  // Classes
  // [...]
  val Algorithm: Resource = ResourceFactory.createResource(ns + "Algorithm")
  val BayesTheoryAlgorithms: Resource =
    ResourceFactory.createResource(ns + "BayesTheoryAlgorithms")
  val DecisionTreesAlgorithms: Resource =
    ResourceFactory.createResource(ns + "DecisionTreesAlgorithms")
  val DescriptiveMethod: Resource =
    ResourceFactory.createResource(ns + "DescriptiveMethod")
  val EnsambleTechnique: Resource =
    ResourceFactory.createResource(ns + "EnsambleTechnique")
  val ForClassificationProblem: Resource =
    ResourceFactory.createResource(ns + "ForClassificationProblem")
  val PredictiveMethod: Resource =
    ResourceFactory.createResource(ns + "PredictiveMethod")
  val StatisticalApproach: Resource =
    ResourceFactory.createResource(ns + "StatisticalApproach")
  val SupervisedApproach: Resource =
    ResourceFactory.createResource(ns + "SupervisedApproach")
  val SymbolicApproach: Resource =
    ResourceFactory.createResource(ns + "SymbolicApproach")
  val UnsupervisedApproach: Resource =
    ResourceFactory.createResource(ns + "UnsupervisedApproach")
  // [...]
}
