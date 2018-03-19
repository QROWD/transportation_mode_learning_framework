package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification._
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.sql.SparkSession

/**
  * Used to learn a model that classifies wavelet data.
  *
  * Currently, we do
  * - use a MLP network
  * - take data of form [features: Vector, label: Double]
  * - split the input data into train and test data (e.g. 80/20)
  * - perform k-fold cross validation on the training data
  * - test predication accuracy on the test data
  */
object WaveletClassificationModelLearner {

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org.apache.spark").setLevel(Level.WARN)

    val path = args(0)
    val targetPath = args(1)
    val windowSize: Int = args(2).toInt
    val folds: Int = args(3).toInt


    val session = SparkSession.builder()
      .master("local[4]")
      .config("spark.default.parallelism", "40")
      .getOrCreate()

    // read data
    var data = session.read.parquet(path)//.limit(100000)
//    data.show(false)
//    data.cache()
    data.printSchema()

    // determine number of labels
    val numClasses = data.select("label").distinct().count().asInstanceOf[Int]
    println(s"#Classes:$numClasses")

//    val f = udf{v: Vector => v.size}
//    data.select("features").withColumn("size", f(col("features"))).select("size").distinct().show()

    // convert data
//    data = FrequencyConverter.convert(data, windowSize)
//    data.printSchema()
//    data.cache()
//    print(data.count())
//    data.show(200)

    // Split the data into training and test sets (20% held out for testing).
    val Array(trainingData, testData) = data.randomSplit(Array(0.8, 0.2), 1234L)

    // specify layers for the neural network
    // input layer of size 4 (features)
    // two intermediate
    // and output of size 3 (classes)
    val layers = Array[Int](windowSize, 12, 6, numClasses + 1)

    // create the MLP and set its parameters
    val mlp = new MultilayerPerceptronClassifier()
      .setLayers(layers)
      .setTol(1E-6)
      .setBlockSize(128)
      .setSeed(1234L)
      .setMaxIter(400)

    // Chain indexers and tree in a Pipeline.
    val pipeline = new Pipeline()
      .setStages(Array(mlp))

    val paramGrid = new ParamGridBuilder().build() // No parameter search

    // Select (prediction, true label) and compute test error.
    val evaluator = new MulticlassClassificationEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
      .setMetricName("accuracy")

//    // Train model. This also runs the indexers.
//    val model = pipeline.fit(trainingData)
//
//    // Make predictions.
//    val predictions = model.transform(testData)
//
//    val accuracy = evaluator.evaluate(predictions)
//    println(s"Test Error = ${(1.0 - accuracy)}")

    val cv = new CrossValidator()
      .setEstimator(mlp)
      .setEvaluator(evaluator)
      .setEstimatorParamMaps(paramGrid)
      .setNumFolds(10)
//      .setParallelism(1)  // Evaluate up to 2 parameter settings in parallel

    val cvModel = cv.fit(data)
    println(s"CV metrics: ${cvModel.avgMetrics.mkString(",")}")
    println(s"Best model params: ${cvModel.bestModel.asInstanceOf[MultilayerPerceptronClassificationModel].explainParams()}")

    // save model to disk
    cvModel.save(targetPath)

    val predictions = cvModel.bestModel.transform(testData)
    val accuracy = evaluator.evaluate(predictions)
    println(s"Test Error = ${(1.0 - accuracy)}")

    session.stop()
  }
}