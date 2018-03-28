package eu.qrowd_project.wp6.transportation_mode_learning.preprocessing

import java.io.File

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
object ParquetViewer {

  def main(args: Array[String]): Unit = {
    val session = SparkSession.builder()
      .master("local[4]")
      .getOrCreate()

    // read data
    var data = session.read.parquet(args(0))
    data.printSchema()
    data.show(1000)
  }
}
