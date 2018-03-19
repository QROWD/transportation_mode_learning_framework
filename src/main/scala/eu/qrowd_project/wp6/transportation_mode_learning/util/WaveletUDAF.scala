package eu.qrowd_project.wp6.transportation_mode_learning.util

import scala.collection.mutable

import org.apache.spark.ml.linalg.SQLDataTypes
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.sql.Row
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types.{DataType, DataTypes, DoubleType, StructType}

/**
  * @author Lorenz Buehmann
  */
class WaveletUDAF extends UserDefinedAggregateFunction {
  override def inputSchema: StructType = new StructType()
    .add("value", DoubleType)

  override def bufferSchema: StructType = new StructType()
    .add("x", DataTypes.createArrayType(DoubleType))

  override def dataType: DataType = SQLDataTypes.VectorType

  override def deterministic: Boolean = true

  override def initialize(buffer: MutableAggregationBuffer): Unit = {
    buffer(0) = Array(0.0)
  }

  override def update(buffer: MutableAggregationBuffer, input: Row): Unit = {
    buffer(0) = buffer.getAs[mutable.WrappedArray[Double]](0) :+ input.getDouble(0)
  }

  override def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {

  }
  override def evaluate(buffer: Row): Any = Vectors.dense(buffer.getAs[mutable.WrappedArray[Double]](0).array)
}
