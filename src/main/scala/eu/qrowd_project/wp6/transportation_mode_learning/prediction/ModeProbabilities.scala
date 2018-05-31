package eu.qrowd_project.wp6.transportation_mode_learning.prediction

import java.sql.Timestamp

case class ModeProbabilities(
                              schema: Seq[String],
                              probabilities: Seq[(Timestamp, (Double, Double, Double, Double, Double, Double))]) {
  override def toString: String = s"$schema\n${probabilities.take(20)} (${Math.max(0, probabilities.size - 20)} remaining)"
}