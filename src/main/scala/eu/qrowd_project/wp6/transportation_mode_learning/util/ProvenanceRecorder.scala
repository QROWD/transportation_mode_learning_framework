package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.io.{File, FileOutputStream, FileWriter}
import java.util.Calendar

import com.typesafe.config.ConfigFactory
import eu.qrowd_project.wp6.transportation_mode_learning.prediction.PredictedStage
import eu.qrowd_project.wp6.transportation_mode_learning.scripts.Trip
import eu.qrowd_project.wp6.transportation_mode_learning.vocab.{BFO, DC, MLSchema, MexAlgo, PROV_O}
import org.apache.jena.datatypes.xsd.XSDDateTime
import org.apache.jena.rdf.model.{Model, ModelFactory, Property, Resource, ResourceFactory}
import org.apache.jena.vocabulary.{OWL2, RDF, RDFS}
import play.api.libs.json
import play.api.libs.json.{Json, _}

/**
  * The provenance recorder is instatiated per user and day.
  *
  * Used vocabularies/ontologies:
  * - PROV-O
  * - ML-Schema
  * - MEX
  * - Custom vocabulary
  *
  */
class ProvenanceRecorder(userID: String, date: String, userIDIndex1: Int = -1) {
  private val prefixStr = "http://qrowd-project.eu/prov#"
  private var tripDetectionSettings: Map[String, String] = Map.empty
  private var errors: List[String] = List.empty
  private var numDetectedOutliers: Int = 0
  private var trips: Seq[Trip] = Seq.empty
  private var predictions: Seq[Seq[PredictedStage]] = Seq.empty
  var tripsWereMergedInPostProcessing = false
  var writeRDFProvData = true
  val userIDIndex = userIDIndex1

  /* ------- vocabulary start ------- */
  // classes
  val Prediction: Resource =
    ResourceFactory.createResource(prefixStr + "Prediction")
  val GPSDataset: Resource =
    ResourceFactory.createResource(prefixStr + "GPSDataset")
  val AccelerometerDataset: Resource =
    ResourceFactory.createResource(prefixStr + "AccelerometerDataset")

  // object properties
  val madePrediction: Property =
    ResourceFactory.createProperty(prefixStr + "made_prediction")

  // datatype properties
  val classLabel: Property =
    ResourceFactory.createProperty(prefixStr + "class_label")
  val errorMsg: Property =
    ResourceFactory.createProperty(prefixStr + "error_message")
  val numOutliers: Property = ResourceFactory.createProperty(
    prefixStr + "number_of_detected_outliers")
  val tripsMergedInPostprocessing: Property =
    ResourceFactory.createProperty(prefixStr + "trips_merged_in_postprocessing")

  // individuals
  val guessedDummyAlgorithm: Resource =
    ResourceFactory.createResource(prefixStr + "guessed")
  val decisionTreeAlgorithm: Resource =
    ResourceFactory.createResource(prefixStr + "decision_tree")
  val randomForest: Resource =
    ResourceFactory.createResource(prefixStr + "random_forest")
  val adaBoost: Resource =
    ResourceFactory.createResource(prefixStr + "ada_boost")
  val gradientBoosting: Resource =
    ResourceFactory.createResource(prefixStr + "gradient_boosting")
  val extraTrees: Resource =
    ResourceFactory.createResource(prefixStr + "extra_trees")
  val svm: Resource = ResourceFactory.createResource(prefixStr + "svm")
  val spaceTimeClustering: Resource =
    ResourceFactory.createResource(prefixStr + "space_time_clustering")
  val windowDistanceBasedTripDetection: Resource =
    ResourceFactory.createResource(prefixStr + "window_distance_based_trip_detection")
  val algorithmIndividuals = Seq(
    guessedDummyAlgorithm, decisionTreeAlgorithm, randomForest, adaBoost,
    gradientBoosting, extraTrees, svm,
    spaceTimeClustering, windowDistanceBasedTripDetection)

  val guessedDummyAlgorithmImpl: Resource =
    ResourceFactory.createResource(prefixStr + "guessed_implementation")
  val decisionTreeAlgorithmImpl: Resource =
    ResourceFactory.createResource(prefixStr + "decision_tree_sk_learn_implementation")
  val randomForestImpl: Resource =
    ResourceFactory.createResource(prefixStr + "random_forest_sk_learn_implementation")
  val adaBoostImpl: Resource =
    ResourceFactory.createResource(prefixStr + "ada_boost_sk_learn_implementation")
  val gradientBoostingImpl: Resource =
    ResourceFactory.createResource(prefixStr + "gradient_boosting_sk_learn_implementation")
  val extraTreesImpl: Resource =
    ResourceFactory.createResource(prefixStr + "extra_trees_sk_learn_implementation")
  val svmImpl: Resource =
    ResourceFactory.createResource(prefixStr + "svm_sk_learn_impl")
  val spaceTimeClusteringImpl: Resource =
    ResourceFactory.createResource(prefixStr + "space_time_clustering_qrowd_implementation")
  val windowDistanceBasedTripDetectionImpl: Resource =
    ResourceFactory.createResource(
      prefixStr + "window_distance_based_trip_detection_qrowd_implementation")
  val implementationIndividuals = Seq(
    guessedDummyAlgorithmImpl, decisionTreeAlgorithmImpl, randomForestImpl,
    adaBoostImpl, gradientBoostingImpl, extraTreesImpl, svmImpl,
    spaceTimeClusteringImpl, windowDistanceBasedTripDetectionImpl)

  val confidence: Resource = ResourceFactory.createResource(prefixStr + "confidence")

  def baseOntology: Model = {
    val ont = ModelFactory.createDefaultModel()

    ont.add(confidence, RDF.`type`, OWL2.NamedIndividual)
    ont.add(confidence, RDF.`type`, MLSchema.EvaluationMeasure)

    ont.add(GPSDataset, RDF.`type`, OWL2.Class)
    ont.add(GPSDataset, RDFS.subClassOf, MLSchema.Dataset)

    ont.add(AccelerometerDataset, RDF.`type`, OWL2.Class)
    ont.add(AccelerometerDataset, RDFS.subClassOf, MLSchema.Dataset)

    algorithmIndividuals.foreach(ont.add(_, RDF.`type`, MLSchema.Algorithm))
    implementationIndividuals.foreach(
      ont.add(_, RDF.`type`, MLSchema.Implementation))

    for ((impl, algo) <- implementationIndividuals.zip(algorithmIndividuals)) {
      ont.add(impl, MLSchema.implements, algo)
    }

    ont.add(spaceTimeClustering, RDFS.subClassOf, MexAlgo.UnsupervisedApproach)

    ont
  }
  /* ------- vocabulary end ------- */

  /*
   * Example settings:
   *
   * Cluster-based trip detection:
   *   Map[String, String](
   *     "tripDetectionType" -> "ClusterBasedTripDetection",
   *     "useMapMatching" -> useMapMatching.toString,
   *     "clusterer" -> "tdbscan",
   *     "cEps" -> config.getDouble("stop_detection.clustering.tdbscan.cEps").toString,
   *     "eps" -> config.getDouble("stop_detection.clustering.tdbscan.eps").toString,
   *     "minPts" -> config.getInt("stop_detection.clustering.tdbscan.minPts").toString
   *   )
   *
   * Window-distance-based trip detection:
   *   Map[String, String](
   *     "tripDetectionType" -> "WindowDistanceBasedTripDetection",
   *     "windowSize" -> windowSize.toString,
   *     "distanceThresholdInKm" -> distanceThresholdInKm.toString,
   *     "minNrOfSegments" -> minNrOfSegments.toString,
   *     "noiseSegments" -> noiseSegments.toString
   *   )
   */
  def setTripDetectionSettings(settings: Map[String, String]): Unit = {
    tripDetectionSettings = settings
  }

  def setNumDetectedOutliers(num: Int): Unit = {
    numDetectedOutliers = num
  }

  def addError(errMsg: String): Unit = {
    errors = errors ++ List(errMsg)
  }

  def addTrips(trips: Seq[Trip]): Unit = {
    this.trips = this.trips ++ trips
  }

  def addPredictions(predictions: Seq[PredictedStage]): Unit = {
    this.predictions = this.predictions ++ Seq(predictions)
  }

  def close = {
    // sanity check
    assert(trips.size == predictions.size)

    val settingsJson = Json.toJsObject(
      Map[String, JsValue](
        "tripDetectionSettings" -> Json.toJsObject(tripDetectionSettings),
        "errors" -> new json.JsArray(errors.toIndexedSeq.map(JsString)),
        "numRemovedOutliers" -> json.JsNumber(numDetectedOutliers)))

    val appConfig = ConfigFactory.load()
    val dir = appConfig.getString("provenance_tracking.output_dir")

    if (writeRDFProvData && tripDetectionSettings.nonEmpty) {
      val outRDFFile = new File(dir + File.separator + s"${userID}_$date.ttl")
      writeRDF(outRDFFile)
    }

    val outFile = new File(dir + File.separator + s"${userID}_$date.json")
    val fileWriter = new FileWriter(outFile)
    fileWriter.write(Json.prettyPrint(settingsJson))
    fileWriter.close()
  }

  def writeRDF(file: File): Unit = {
    val rdfModel = baseOntology

    // trip detection method details
    val tripDetectionImpl = tripDetectionSettings("tripDetectionType") match {
      case "ClusterBasedTripDetection" =>
        spaceTimeClusteringImpl
      case "WindowDistanceBasedTripDetection" =>
        windowDistanceBasedTripDetectionImpl
    }

    val studyID = getStudyIdentifier
    val study = rdfModel.createResource(prefixStr + s"study_$studyID")
    rdfModel.add(study, RDF.`type`, MLSchema.Study)

    val experimentID = getExperimentIdentifier
    val experiment = rdfModel.createResource(
      prefixStr + s"experiment_$experimentID")

    rdfModel.add(experiment, RDF.`type`, MLSchema.Experiment)
    rdfModel.add(study, MLSchema.hasPart, experiment)

    for (parameter <- tripDetectionSettings.keys.filter(_ != "tripDetectionType")) {
      // Parameters stay the same throughout all runs. Thus, there is no need
      // to encode the run identifier into the IRIs
      val paramIRI = rdfModel.createResource(
        prefixStr + s"param_${parameter}_${studyID}_${experimentID}_$userID")
      val hyperParamSetting = rdfModel.createResource(
        prefixStr + s"param_setting_${parameter}_${studyID}_" +
          s"${experimentID}_$userID")

      val value = ResourceFactory.createPlainLiteral(tripDetectionSettings(parameter))
      rdfModel.add(tripDetectionImpl, MLSchema.hasHyperParameter, paramIRI)
      rdfModel.add(paramIRI, RDF.`type`, MLSchema.HyperParameter)
      rdfModel.add(hyperParamSetting, RDF.`type`, MLSchema.HyperParameterSetting)
      rdfModel.add(hyperParamSetting, MLSchema.specifiedBy, paramIRI)
      rdfModel.add(hyperParamSetting, MLSchema.hasValue, value)
    }

    // Errors and outliers are related to the experiment
    for (error <- errors) {
      val errorLiteral = ResourceFactory.createPlainLiteral(error)

      rdfModel.add(experiment, errorMsg, errorLiteral)
    }

    val numOutliersLiteral = ResourceFactory.createTypedLiteral(numDetectedOutliers)
    rdfModel.add(experiment, numOutliers, numOutliersLiteral)

    val tripsMerged = ResourceFactory.createTypedLiteral(tripsWereMergedInPostProcessing)
    rdfModel.add(experiment, tripsMergedInPostprocessing, tripsMerged)

    // User IDs are stable throughout all experiments/studies so there is no
    // need to encode the study ID or experiment ID into the user IRI
    val userIRI = ResourceFactory.createResource(prefixStr + s"user_$userID")
    rdfModel.add(userIRI, RDF.`type`, PROV_O.Person)
    rdfModel.add(experiment, DC.creator, userIRI)

    for ((trip: Trip, tripIdx: Int) <- trips.zipWithIndex) {
      // Each trip corresponds to a :Dataset in ML-Schema and an :Activity in
      // PROV-O
      val gpsTripDataIRI = rdfModel.createResource(
        prefixStr + s"trip_${studyID}_${experimentID}_${tripIdx}_gps_dataset")
      rdfModel.add(gpsTripDataIRI, RDF.`type`, GPSDataset)

      val accTripDataIRI = rdfModel.createResource(
        prefixStr + s"trip_${studyID}_${experimentID}_${tripIdx}_" +
          s"accelerometer_dataset")
      rdfModel.add(accTripDataIRI, RDF.`type`, AccelerometerDataset)

      val tripActivity = rdfModel.createResource(
        prefixStr + s"trip_${studyID}_${experimentID}_${tripIdx}_activity")
      rdfModel.add(tripActivity, RDF.`type`, PROV_O.Activity)

      val startCalendar = Calendar.getInstance
      startCalendar.setTimeInMillis(trip.start.timestamp.getTime)
      val startLiteral =
        ResourceFactory.createTypedLiteral(new XSDDateTime(startCalendar))

      val endCalendar = Calendar.getInstance()
      endCalendar.setTimeInMillis(trip.end.timestamp.getTime)
      val endLiteral =
        ResourceFactory.createTypedLiteral(new XSDDateTime(endCalendar))

      rdfModel.add(tripActivity, PROV_O.startedAtTime, startLiteral)
      rdfModel.add(tripActivity, PROV_O.endedAtTime, endLiteral)
      rdfModel.add(userIRI, PROV_O.wasAssociatedWith, tripActivity)

      // trip generated by either clustering or time-distance checks
      tripDetectionSettings("tripDetectionType") match {
        case "ClusterBasedTripDetection" =>
          rdfModel.add(spaceTimeClustering, PROV_O.generated, tripActivity)
          rdfModel.add(tripActivity, PROV_O.wasGeneratedBy, spaceTimeClustering)
          rdfModel.add(spaceTimeClustering, PROV_O.wasAttributedTo, userIRI)
        case "WindowDistanceBasedTripDetection" =>
          rdfModel.add(windowDistanceBasedTripDetection, PROV_O.generated, tripActivity)
          rdfModel.add(tripActivity, PROV_O.wasGeneratedBy, windowDistanceBasedTripDetection)
          rdfModel.add(windowDistanceBasedTripDetection, PROV_O.wasAttributedTo, userIRI)
      }

      // Accelerometer-specific
      for ((prediction: PredictedStage, windowIdx: Int) <- predictions(tripIdx).zipWithIndex) {
        // A prediction was made per window. Hence the window data and the
        // respective prediction is associated with a :Run in ML-Schema
        val runIdentifier = getRunIdentifier(studyID, experimentID, tripIdx, windowIdx)
        val runIRI = rdfModel.createResource(prefixStr + s"run_$runIdentifier")
        rdfModel.add(runIRI, RDF.`type`, MLSchema.Run)

        val windowIRI = rdfModel.createResource(
          prefixStr + s"window_${studyID}_${experimentID}_${tripIdx}_$windowIdx")
        rdfModel.add(windowIRI, RDF.`type`, MLSchema.Data)
        rdfModel.add(windowIRI, BFO.partOf, accTripDataIRI)
        rdfModel.add(runIRI, MLSchema.hasInput, windowIRI)

        // The actual prediction
        val predictionIRI = rdfModel.createResource(
          prefixStr + s"prediction_${studyID}_${experimentID}_${tripIdx}_" +
            s"${windowIdx}_${prediction.label}")
        rdfModel.add(predictionIRI, RDF.`type`, Prediction)
        rdfModel.add(
          predictionIRI,
          RDFS.label,
          ResourceFactory.createPlainLiteral(prediction.label))
        rdfModel.add(runIRI, madePrediction, predictionIRI)

        // The confidence score
        val confidenceMeasure = rdfModel.createResource(
          prefixStr + s"confidence_${studyID}_${experimentID}_${tripIdx}_" +
            s"${windowIdx}_${prediction.label}")
        rdfModel.add(confidenceMeasure, RDF.`type`, MLSchema.ModelEvaluation)
        rdfModel.add(confidenceMeasure, MLSchema.specifiedBy, confidence)
        rdfModel.add(
          confidenceMeasure,
          MLSchema.hasValue,
          ResourceFactory.createTypedLiteral(prediction.prob))
        rdfModel.add(runIRI, MLSchema.hasOutput, confidenceMeasure)

        // The algorithm and its implementation
        if (prediction.modelInfo == "guessed") {
          rdfModel.add(runIRI, MLSchema.executes, guessedDummyAlgorithmImpl)

        } else {
          val algoImplNameAndAlgoImplSettingsStr = prediction.modelInfo.split("\\(")
          val algoImplName = algoImplNameAndAlgoImplSettingsStr.head
          var algoImplSettingsStr = algoImplNameAndAlgoImplSettingsStr(1)

          // strip off trailing, closing parenthesis
          algoImplSettingsStr = algoImplSettingsStr.substring(
            0, algoImplNameAndAlgoImplSettingsStr(1).length-1)

          val algoImplSettings: Seq[(String, String)] =
            algoImplSettingsStr.split(",").map(e => e.split("="))
              .map(keyValue => (keyValue(0), keyValue(1)))

          val algoImpl = algoImplName match {
            case "ExtraTreesClassifier" => extraTreesImpl
            case "GradientBoostingClassifier" => gradientBoostingImpl
            case "RandomForestClassifier" => randomForestImpl
            case "DecisionTreeClassifier" => decisionTreeAlgorithmImpl
            case "AdaBoostClassifier" => adaBoostImpl
            case "SVC" => svmImpl
            case _ => throw new RuntimeException(
              s"Unknown algorithm: $algoImplName")
          }

          rdfModel.add(runIRI, MLSchema.executes, algoImpl)

          // this will generate duplicates which will hopefully be removed by Jena
          for ((key, value) <- algoImplSettings) {
            val hyperParam = ResourceFactory.createResource(
              prefixStr + s"param_${key}_${studyID}_${experimentID}_" +
                s"${userID}_$windowIdx")
            val hyperParamSetting = rdfModel.createResource(
              prefixStr + s"param_setting_${key}_${studyID}_${experimentID}_" +
                s"${userID}_$windowIdx")
            val hyperParamValue = ResourceFactory.createPlainLiteral(value)

            rdfModel.add(algoImpl, MLSchema.hasHyperParameter, hyperParam)
            rdfModel.add(hyperParam, RDF.`type`, MLSchema.HyperParameter)
            rdfModel.add(hyperParamSetting, MLSchema.specifiedBy, hyperParam)
            rdfModel.add(hyperParamSetting, RDF.`type`, MLSchema.HyperParameterSetting)
            rdfModel.add(hyperParamSetting, MLSchema.hasValue, hyperParamValue)
          }
        }
      }
    }

//    rdfModel.write(System.out, "ttl")
    rdfModel.write(new FileOutputStream(file), "ttl")
  }

  /**
    * In the context of QROWD a run corresponds to a mode prediction (i.e.
    * classification) of one single window.
    */
  private def getRunIdentifier(
                                studyID: String,
                                experimentID: String,
                                tripIdx: Int,
                                windowIdx: Int): String = {

    s"${studyID}_${experimentID}_${tripIdx}_${userID}_$windowIdx"
  }

  /**
    * In the context of QROWD an experiment is an analytics run which is
    * performed on a daily basis. Thus, we take the date as main identifier
    * within the local part.
    */
  private def getExperimentIdentifier: String = {
    date
  }

  /**
    * In the context of QROWD a study is a series of analytics runs performed,
    * e.g. over a period of two weeks. The study name is configured in the
    * main configuration file for the mode detection framework.
    */
  private def getStudyIdentifier: String = {
    val study_name = ConfigFactory.load().getString("provenance_tracking.study_name")

    study_name
  }

}
