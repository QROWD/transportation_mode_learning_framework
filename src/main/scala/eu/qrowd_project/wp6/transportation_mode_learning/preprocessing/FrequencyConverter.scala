package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import java.io.File

import scala.util.Random

import eu.qrowd_project.wp6.transportation_mode_learning.util.WaveletUDAF
import jwave.Transform
import jwave.transforms.wavelets.haar.Haar1
import org.apache.spark.ml.linalg
import org.apache.spark.ml.linalg.{SQLDataTypes, Vectors}
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.types.{DoubleType, StructType}
import org.apache.spark.sql.{Dataset, Row, SparkSession}

/**
  * @author Lorenz Buehmann
  */
object FrequencyConverter {

  /**
    * L² norm, i.e. Euclidian
    */
  private def norm(x: Double, y: Double, z: Double): Double = Vectors.norm(Vectors.dense(x, y, z), 2)
  private val normUDF = udf[Double, Double, Double, Double](norm)

  private def wavelet(values: Array[Double]): linalg.Vector = {
    import jwave.transforms.FastWaveletTransform
    val t = new Transform(new FastWaveletTransform(new Haar1()))
    Vectors.dense(t.forward(values))
  }

  private val waveletUDAF = new WaveletUDAF()
  private val vectorUDF = udf{values: Seq[Double] => Vectors.dense(values.toArray)}
  private val sizeUDF = udf{values: Seq[Double] => values.size}
  private def commonLabel = {labels: Seq[Double] => labels.groupBy(identity).mapValues(_.size).maxBy(_._2)._1}
  private val commonLabelUDF = udf{commonLabel}

  /**
    * Take a raw dataset consisting of (x,y,z,label) rows and return a new dataset (features, label) with
    * features column being a vector having as length the window size.
    *
    * @param rawData the raw data
    * @param windowSize the size of the window used for sliding over the data such that each window will be a vector
    *                   representing the frequency chunk
    * @return
    */
  def convert(rawData: Dataset[Row], windowSize: Int): Dataset[Row] = {
    var data = rawData
      .withColumn("norm", normUDF(col("x"), col("y"), col("z"))) // vector norm
      .select("norm", "label")

    // slide over window
    import org.apache.spark.mllib.rdd.RDDFunctions._
    val rdd = data.rdd
      .sliding(windowSize)
      .map(rows => {
        val features = wavelet(rows.map(row => row.getDouble(0)))
        val label = commonLabel(rows.map(row => row.getDouble(1)))
        Row.fromTuple(features, label)
      })

    data = rawData.sqlContext.createDataFrame(rdd,
      new StructType()
        .add("features", SQLDataTypes.VectorType)
        .add("label", DoubleType))


    //    val window = Window.rowsBetween(0, windowSize - 1)
    //    data = data
    //      .select("norm", "label")
    //      .withColumn("window", collect_list("norm") over window)
    //      .withColumn("features", vectorUDF(col("window"))) // vectorize
    //      .withColumn("labels", collect_list("label") over window) // get most common label
    //      .withColumn("label", commonLabelUDF(col("labels")))
    //      .withColumn("size", sizeUDF(col("window")))
    //      .filter(s"size == $windowSize") // ensure window size such that vector size is fixed
    //      .select("features", "label")

    data
  }

  case class Config(in: File = new File("."), out: File = new File("."), windowSize: Int = 1, withLabels: Boolean = true)

  private val parser = new scopt.OptionParser[Config]("FrequencyConverter") {
    head("FrequencyConverter", "0.1.0")

    opt[File]('i', "in").required().valueName("<file>").
      action((x, c) => c.copy(in = x)).
      text("Path to input Parquet file containing the raw data of shape [x(Double), y(Double), z(Double), label(Double)]")

    opt[File]('o', "out").required().valueName("<file>").
      action((x, c) => c.copy(out = x)).
      text("Path to output Parquet file containing the converted frequency data of shape [features (Vector), label(Double)]")

    opt[Int]('w', "window").required().action( (x, c) =>
      c.copy(windowSize = x) ).text("Size of the window used for conversion, i.e. size of the feature vectors.")

    opt[Unit]("no-labels").action( (_, c) =>
      c.copy(withLabels = false) ).text("whether the dataset contains labels or not, i.e. is just of shape [x, y, z]")
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Config()) match {
      case Some(config) => run(config)
      case None =>
      // arguments are bad, error message will have been displayed
    }
  }

  private def run(config: Config): Unit = {
    val session = SparkSession.builder()
      .master("local[4]")
      .getOrCreate()

    // read data
    var data = session.read.parquet(config.in.getAbsolutePath)
//    data.printSchema()
//    data.select("answer").show(false)

    // convert data
    data = convert(data, config.windowSize)

    // save data
    data.write.parquet(config.out.getAbsolutePath)

    session.stop()
  }
}
