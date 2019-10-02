package eu.qrowd_project.wp6.transportation_mode_learning.prediction

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.sql.Timestamp

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._


/**
  * Based on these kind of output lines:
  *
  * walk,2018-04-13 09:15:07.981000,2018-04-13 09:32:37.981000,0.983933,\
  * GradientBoostingClassifier(criterion='friedman_mse',init=None,\
  * learning_rate=0.1,loss='deviance',max_depth=3,max_features=None,\
  * max_leaf_nodes=None,min_impurity_decrease=0.0,min_impurity_split=None,\
  * min_samples_leaf=1,min_samples_split=2,min_weight_fraction_leaf=0.0,\
  * n_estimators=100,n_iter_no_change=None,presort='auto',random_state=None,\
  * subsample=1.0,tol=0.0001,validation_fraction=0.1,verbose=0,warm_start=False)
  */
case class PredictedStage(
                           label: String,
                           var startTimestamp: Timestamp,
                           var endTimeStamp: Timestamp,
                           prob: Double,
                           modelInfo: String)

/**
  * Wraps an external Python script for mode prediction. In particular, we're
  *
  *  - writing data as CSV to tmp dir
  *  - running the script
  *  - reading the generated output CSV from disk
  *
  * @param baseDir base directory of the Python project
  * @param scriptPath path to the Python script for prediction
  * @param modelPath path to the Python model pickle file
  *
  */
class PythonClient(baseDir: String, scriptPath: String, modelPath: String) {
  private val python: String =
    ConfigFactory.load().getString("prediction_settings.python_interpreter")

  /**
    * Calls the mode prediction given the accelerometer data.
    *
    * @param accelerometerData x,y,z values of the raw accelerometer data
    * @param id some optional identifier to keep track of input data for Python
    *           code written to disk in temp folder
    */
  def predict(
               accelerometerData: Seq[(Double, Double, Double, Timestamp)],
               id: String = ""): Seq[PredictedStage] = {

    // write data as CSV to disk
    val inputFile = serializeInput(accelerometerData, id)

    val resultFilePath = Paths.get(baseDir).resolve(id + "_out.csv")
    val command = s"$python $scriptPath --predict --model_dir $modelPath " +
      s"--output_file ${resultFilePath.toFile.getAbsolutePath} " +
      s"${inputFile.toFile.getAbsolutePath} "

    /*
     * expected output:
     * walk,2018-04-13 09:15:07.981000,2018-04-13 09:32:37.981000,0.983933,GradientBoostingClassifier(criterion='friedman_mse',init=None,learning_rate=0.1,loss='deviance',max_depth=3,max_features=None,max_leaf_nodes=None,min_impurity_decrease=0.0,min_impurity_split=None,min_samples_leaf=1,min_samples_split=2,min_weight_fraction_leaf=0.0,n_estimators=100,n_iter_no_change=None,presort='auto',random_state=None,subsample=1.0,tol=0.0001,validation_fraction=0.1,verbose=0,warm_start=False)
     * ...
     */
    sys.process.Process(command, new java.io.File(baseDir)).!

    // read output from CSV to internal list
    val stagePredictions: Seq[PredictedStage] = readOutput(resultFilePath)

    // post-processing/clean up
    if (stagePredictions.nonEmpty &&
        stagePredictions.head.startTimestamp.after(accelerometerData.head._4)) {
      stagePredictions.head.startTimestamp = accelerometerData.head._4
    }

    if (stagePredictions.nonEmpty &&
        stagePredictions.last.endTimeStamp.before(accelerometerData.last._4)) {
      stagePredictions.last.endTimeStamp = accelerometerData.last._4
    }

    // Maybe all this isn't necessary since later we will only consider the
    // start timestamp anyway
//    var i = 0
//    if (stagePredictions.size >= 2) {
//      for (i <- 0 to (stagePredictions.size-1)) {
//        val prediction1 = stagePredictions(i)
//        val prediction2 = stagePredictions(i+1)
//
//        if ((prediction2.startTimestamp.getTime -
//          prediction1.endTimeStamp.getTime) > smallestAcceptableGapInMillisecs) {
//          prediction1.endTimeStamp = prediction2.startTimestamp
//        }
//      }
//
//      stagePredictions.slice(0, 2)
//    }

    stagePredictions
  }

  def serializeInput(
                      accelerometerData: Seq[(Double, Double, Double, Timestamp)],
                      id: String = ""): Path = {

    // write data as CSV to disk
    val inputFile =
      File.createTempFile(s"qrowd_acc_data-$id-", ".tmp").toPath

    val content = "x,y,z,timestamp\n" + accelerometerData
      .map(entry => (entry._1, entry._2, entry._3, entry._4)
        .productIterator.mkString(",")).mkString("\n")

    Files.write(inputFile, content.getBytes("UTF-8"))
  }

  def readOutput(resultFilePath:Path): Seq[PredictedStage] = {
    val outputCSV = resultFilePath

    // read all non-empty lines
    val lines = Files.readAllLines(outputCSV).asScala.filter(_ != "")

    /*
     * One line looks like this:
     * car,2019-05-27 06:47:59.532000,2019-05-27 06:59:09.532000,0.707885,\
     * GradientBoostingClassifier(criterion='friedman_mse',init=None,\
     * learning_rate=0.1,loss='deviance',max_depth=3,max_features=None,\
     * max_leaf_nodes=None,min_impurity_decrease=0.0,min_impurity_split=None,\
     * min_samples_leaf=1,min_samples_split=2,min_weight_fraction_leaf=0.0,\
     * n_estimators=100,n_iter_no_change=None,presort='auto',\
     * random_state=None,subsample=1.0,tol=0.0001,validation_fraction=0.1,\
     * verbose=0,warm_start=False)
     */
    val entries = lines
      .map(line => line.split(",", 5))
      .map(e =>
        PredictedStage(
          e(0),
          Timestamp.valueOf(e(1)),
          Timestamp.valueOf(e(2)),
          e(3).toDouble,
          e(4)))

    entries
  }

  def stop(): Unit = {}
}
