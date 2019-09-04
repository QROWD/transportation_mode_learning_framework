package eu.qrowd_project.wp6.transportation_mode_learning.vocab

import org.apache.jena.rdf.model.{Property, Resource, ResourceFactory}

/**
  * This vocabulary is incomplete!!!
  *
  * TODO: Finish this!
  */
object MLSchema {
  val ns: String = "http://www.w3.org/ns/mls#"

  // classes
  val Algorithm: Resource = ResourceFactory.createResource(ns + "Algorithm")
  val Data: Resource = ResourceFactory.createResource(ns + "Data")
  val DataCharacteristic: Resource = ResourceFactory.createResource(ns + "DataCharacteristic")
  val Dataset: Resource = ResourceFactory.createResource(ns + "Dataset")
  val DatasetCharacteristic: Resource = ResourceFactory.createResource(ns + "DatasetCharacteristic")
  val EvaluationMeasure: Resource = ResourceFactory.createResource(ns + "EvaluationMeasure")
  val EvaluationProcedure: Resource = ResourceFactory.createResource(ns + "EvaluationProcedure")
  val EvaluationSpecification: Resource = ResourceFactory.createResource(ns + "EvaluationSpecification")
  val Experiment: Resource = ResourceFactory.createResource(ns + "Experiment")
  val Feature: Resource = ResourceFactory.createResource(ns + "Feature")
  val FeatureCharacteristic: Resource = ResourceFactory.createResource(ns + "FeatureCharacteristic")
  val HyperParameter: Resource = ResourceFactory.createResource(ns + "HyperParameter")
  val HyperParameterSetting: Resource = ResourceFactory.createResource(ns + "HyperParameterSetting")
  val Implementation: Resource = ResourceFactory.createResource(ns + "Implementation")
  val ImplementationCharacteristic: Resource = ResourceFactory.createResource(ns + "ImplementationCharacteristic")
  val InformationEntity: Resource = ResourceFactory.createResource(ns + "InformationEntity")
  val Model: Resource = ResourceFactory.createResource(ns + "Model")
  val ModelCharacteristic: Resource = ResourceFactory.createResource(ns + "ModelCharacteristic")
  val ModelEvaluation: Resource = ResourceFactory.createResource(ns + "ModelEvaluation")
  val Process: Resource = ResourceFactory.createResource(ns + "Process")
  val Quality: Resource = ResourceFactory.createResource(ns + "Quality")
  val Run: Resource = ResourceFactory.createResource(ns + "Run")
  val Software: Resource = ResourceFactory.createResource(ns + "Software")
  val Study: Resource = ResourceFactory.createResource(ns + "Study")
  val Task: Resource = ResourceFactory.createResource(ns + "Task")

  // properties
  val achieves: Property = ResourceFactory.createProperty(ns, "achieves")
  val definedOn: Property = ResourceFactory.createProperty(ns, "definedOn")
  val defines: Property = ResourceFactory.createProperty(ns, "defines")
  val executes: Property = ResourceFactory.createProperty(ns, "executes")
  val hasHyperParameter: Property = ResourceFactory.createProperty(ns, "hasHyperParameter")
  val hasInput: Property = ResourceFactory.createProperty(ns, "hasInput")
  val hasOutput: Property = ResourceFactory.createProperty(ns, "hasOutput")
  val hasPart: Property = ResourceFactory.createProperty(ns, "hasPart")
  val hasQuality: Property = ResourceFactory.createProperty(ns, "hasQuality")
  val implements: Property = ResourceFactory.createProperty(ns, "implements")
  val realizes: Property = ResourceFactory.createProperty(ns, "realizes")
  val specifiedBy: Property = ResourceFactory.createProperty(ns, "specifiedBy")
  val hasValue: Property = ResourceFactory.createProperty(ns, "hasValue")
}
