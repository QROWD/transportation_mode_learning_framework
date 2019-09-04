package eu.qrowd_project.wp6.transportation_mode_learning.vocab

import org.apache.jena.rdf.model.{Property, Resource, ResourceFactory}

/**
  * This vocabulary object is incomplete!!!
  *
  * TODO: Finish this!
  */
object PROV_O {
  val ns = "http://www.w3.org/ns/prov#"

  // Classes
  val Activity: Resource = ResourceFactory.createResource(ns + "Activity")
  val Agent: Resource = ResourceFactory.createResource(ns + "Agent")
  val Organization: Resource =
    ResourceFactory.createResource(ns + "Organization")
  val Person: Resource = ResourceFactory.createResource(ns + "Person")
  val SoftwareAgent: Resource =
    ResourceFactory.createResource(ns + "SoftwareAgent")
  val Entity: Resource = ResourceFactory.createResource(ns + "Entity")
  val Bundle: Resource = ResourceFactory.createResource(ns + "Bundle")
  val Collection: Resource = ResourceFactory.createResource(ns + "Collection")
  val EmptyCollection: Resource =
    ResourceFactory.createResource(ns + "EmptyCollection")
  val Plan: Resource = ResourceFactory.createResource(ns + "Plan")
  val Influence: Resource = ResourceFactory.createResource(ns + "Influence")
  val ActivityInfluence: Resource =
    ResourceFactory.createResource(ns + "ActivityInfluence")
  val Communication: Resource =
    ResourceFactory.createResource(ns + "Communication")
  val Generation: Resource = ResourceFactory.createResource(ns + "Generation")
  val Invalidation: Resource =
    ResourceFactory.createResource(ns + "Invalidation")
  val AgentInfluence: Resource =
    ResourceFactory.createResource(ns + "AgentInfluence")
  val Association: Resource = ResourceFactory.createResource(ns + "Association")
  val Attribution: Resource = ResourceFactory.createResource(ns + "Attribution")
  val Delegation: Resource = ResourceFactory.createResource(ns + "Delegation")
  val EntityInfluence: Resource =
    ResourceFactory.createResource(ns + "EntityInfluence")
  val Derivation: Resource = ResourceFactory.createResource(ns + "Derivation")
  val PrimarySource: Resource =
    ResourceFactory.createResource(ns + "PrimarySource")
  val Quotation: Resource = ResourceFactory.createResource(ns + "Quotation")
  val Revision: Resource = ResourceFactory.createResource(ns + "Revision")
  val End: Resource = ResourceFactory.createResource(ns + "End")
  val Start: Resource = ResourceFactory.createResource(ns + "Start")
  val Usage: Resource = ResourceFactory.createResource(ns + "Usage")
  val InstantaneousEvent: Resource =
    ResourceFactory.createResource(ns + "InstantaneousEvent")
  val Location: Resource = ResourceFactory.createResource(ns + "Location")
  val Role: Resource = ResourceFactory.createResource(ns + "Role")


  // Object properties
  val alternateOf: Property = ResourceFactory.createProperty(ns + "alternateOf")
  val specializationOf: Property =
    ResourceFactory.createProperty(ns + "specializationOf")
  val atLocation: Property = ResourceFactory.createProperty(ns + "atLocation")
  val hadActivity: Property = ResourceFactory.createProperty(ns + "hadActivity")
  val hadGeneration: Property =
    ResourceFactory.createProperty(ns + "hadGeneration")
  val hadPlan: Property = ResourceFactory.createProperty(ns + "hadPlan")
  val hadRole: Property = ResourceFactory.createProperty(ns + "hadRole")
  val hadUsage: Property = ResourceFactory.createProperty(ns + "hadUsage")
  val influenced: Property = ResourceFactory.createProperty(ns + "influenced")
  val generated: Property = ResourceFactory.createProperty(ns + "generated")
  val invalidated: Property = ResourceFactory.createProperty(ns + "invalidated")
  val influencer: Property = ResourceFactory.createProperty(ns + "influencer")
  val activity: Property = ResourceFactory.createProperty(ns + "activity")
  val agent: Property = ResourceFactory.createProperty(ns + "agent")
  val entity: Property = ResourceFactory.createProperty(ns + "entity")
  val qualifiedInfluence: Property =
    ResourceFactory.createProperty(ns + "qualifiedInfluence")
  val qualifiedAssociation: Property =
    ResourceFactory.createProperty(ns + "qualifiedAssociation")
  val qualifiedAttribution: Property =
    ResourceFactory.createProperty(ns + "qualifiedAttribution")
  val qualifiedCommunication: Property =
    ResourceFactory.createProperty(ns + "qualifiedCommunication")
  val qualifiedDelegation: Property =
    ResourceFactory.createProperty(ns + "qualifiedDelegation")
  val qualifiedDerivation: Property =
    ResourceFactory.createProperty(ns + "qualifiedDerivation")
  val qualifiedEnd: Property =
    ResourceFactory.createProperty(ns + "qualifiedEnd")
  val qualifiedGeneration: Property =
    ResourceFactory.createProperty(ns + "qualifiedGeneration")
  val qualifiedInvalidation: Property =
    ResourceFactory.createProperty(ns + "qualifiedInvalidation")
  val qualifiedPrimarySource: Property =
    ResourceFactory.createProperty(ns + "qualifiedPrimarySource")
  val qualifiedQuotation: Property =
    ResourceFactory.createProperty(ns + "qualifiedQuotation")
  val qualifiedRevision: Property =
    ResourceFactory.createProperty(ns + "qualifiedRevision")
  val qualifiedStart: Property =
    ResourceFactory.createProperty(ns + "qualifiedStart")
  val qualifiedUsage: Property =
    ResourceFactory.createProperty(ns + "qualifiedUsage")
  val wasInfluencedBy: Property =
    ResourceFactory.createProperty(ns + "wasInfluencedBy")
  val actedOnBehalfOf: Property =
    ResourceFactory.createProperty(ns + "actedOnBehalfOf")
  val hadMember: Property = ResourceFactory.createProperty(ns + "hadMember")
  val used: Property = ResourceFactory.createProperty(ns + "used")
  val wasAssociatedWith:Property =
    ResourceFactory.createProperty(ns + "wasAssociatedWith")
  val wasAttributedTo: Property =
    ResourceFactory.createProperty(ns + "wasAttributedTo")
  val wasDerivedFrom: Property =
    ResourceFactory.createProperty(ns + "wasDerivedFrom")
  val hasPrimarySource: Property =
    ResourceFactory.createProperty(ns + "hasPrimarySource")
  val wasQuotedFrom: Property =
    ResourceFactory.createProperty(ns + "wasQuotedFrom")
  val wasRevisionOf: Property =
    ResourceFactory.createProperty(ns + "wasRevisionOf")
  val wasEndedBy: Property = ResourceFactory.createProperty(ns + "wasEndedBy")
  val wasGeneratedBy: Property =
    ResourceFactory.createProperty(ns + "wasGeneratedBy")
  val wasInformedBy: Property =
    ResourceFactory.createProperty(ns + "wasInformedBy")
  val wasInvalidatedBy: Property =
    ResourceFactory.createProperty(ns + "wasInvalidatedBy")
  val wasStartedBy: Property =
    ResourceFactory.createProperty(ns + "wasStartedBy")

  // Datatype properties
  val atTime: Property = ResourceFactory.createProperty(ns + "atTime")
  val endedAtTime: Property = ResourceFactory.createProperty(ns + "endedAtTime")
  val generatedAtTime: Property =
    ResourceFactory.createProperty(ns + "generatedAtTime")
  val invalidatedAtTime: Property =
    ResourceFactory.createProperty(ns + "invalidatedAtTime")
  val startedAtTime: Property =
    ResourceFactory.createProperty(ns + "startedAtTime")
  val value: Property = ResourceFactory.createProperty(ns + "value")

  // Individuals
}
