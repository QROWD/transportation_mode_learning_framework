package eu.qrowd_project.wp6.transportation_mode_learning.vocab

import org.apache.jena.rdf.model.{Property, ResourceFactory}

/**
  * This vocabulary object is not complete!!!
  *
  * TODO: Finalize vocabulary
  */
object BFO {
  val ns = "http://purl.obolibrary.org/obo/"

  // Classes

  // Object properties
  val partOf: Property = ResourceFactory.createProperty(ns + "BFO_0000050")
  val memberOf: Property = ResourceFactory.createProperty(ns + "RO_0002350")
  val hasPart: Property = ResourceFactory.createProperty(ns + "BFO_0000051")
  val hasMember: Property = ResourceFactory.createProperty(ns + "RO_0002351")
  val realizedIn: Property = ResourceFactory.createProperty(ns + "BFO_0000054")
  val realizes: Property = ResourceFactory.createProperty(ns + "BFO_0000055")
  val occursIn: Property = ResourceFactory.createProperty(ns + "BFO_0000066")
  val containsProcess: Property = ResourceFactory.createProperty(ns + "BFO_0000067")
  val inheresIn: Property = ResourceFactory.createProperty(ns + "RO_0000052")
  val functionOf: Property = ResourceFactory.createProperty(ns + "RO_0000079")
  val qualityOf: Property = ResourceFactory.createProperty(ns + "RO_0000080")
  val roleOf: Property = ResourceFactory.createProperty(ns + "RO_0000081")
  val dispositionOf: Property = ResourceFactory.createProperty(ns + "RO_0000092")
  val bearerOf: Property = ResourceFactory.createProperty(ns + "RO_0000053")
  val hasFunction: Property = ResourceFactory.createProperty(ns + "RO_0000085")
  val hasQuality: Property = ResourceFactory.createProperty(ns + "RO_0000086")
  val hasRole: Property = ResourceFactory.createProperty(ns + "RO_0000087")
  val hasDisposition: Property = ResourceFactory.createProperty(ns + "RO_0000091")
  val participatesIn: Property = ResourceFactory.createProperty(ns + "RO_0000056")
  val hasParticipant: Property = ResourceFactory.createProperty(ns + "RO_0000057")
  val isConcretizedAs: Property = ResourceFactory.createProperty(ns + "RO_0000058")
  val concretizes: Property = ResourceFactory.createProperty(ns + "RO_0000059")
  val derivesFrom: Property = ResourceFactory.createProperty(ns + "RO_0001000")
  val derivesInto: Property = ResourceFactory.createProperty(ns + "RO_0001001")
  val locationOf: Property = ResourceFactory.createProperty(ns + "RO_0001015")
  val locatedIn: Property = ResourceFactory.createProperty(ns + "RO_0001025")
  val `2DBoundaryOf`: Property = ResourceFactory.createProperty(ns + "RO_0002000")
  val has2DBoundaryOf: Property = ResourceFactory.createProperty(ns + "RO_0002002")

  // Datatype properties

  // Individuals
}
