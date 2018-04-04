package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import java.nio.file.Path

import org.apache.spark.ml.linalg.Vector
import org.apache.spark.sql.SparkSession
import org.platanios.tensorflow.api.Tensor

/**
  * @author Lorenz Buehmann
  */
object AccelerometerDataLoader {

  def load(path: Path): AccelerometerDataset = {
    val session = SparkSession.builder()
      .master("local[4]")
      .getOrCreate()

    // read data
    val data = session.read.parquet(path.toAbsolutePath.toString)

    // split data
    val Array(train, test) = data.randomSplit(Array(0.8, 0.2), 1234L)

    // collect data to driver
    val trainData = train.collect()

    // map to tensors
    val trainFeatures = Tensor(trainData.map(row => Tensor(row.getAs[Vector](0).toArray)))
    val trainLabels = Tensor(trainData.map(row => row.getDouble(1)))

    // collect data to driver
    val testData = test.collect()

    // map to tensors
    val testFeatures = Tensor(testData.map(row => Tensor(row.getAs[Vector](0).toArray)))
    val testLabels = Tensor(testData.map(row => row.getDouble(1)))

    AccelerometerDataset(trainFeatures, trainLabels, testFeatures, testLabels)
  }

}

case class AccelerometerDataset(
                                 trainFeatures: Tensor,
                                 trainLabels: Tensor,
                                 testFeatures: Tensor,
                                 testLabels: Tensor)