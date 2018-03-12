package eu.qrowd_project.wp6.transportation_mode_learning.util

import shapeless._, ops.hlist._, ops.nat._

import scala.util.{

  Failure,

  Success,

  Try

}

/**
  * Autoclosable wrapper for Scala, see https://dzone.com/articles/simple-try-with-resources-construct-in-scala
  */
object Loan {

  def apply[A <: AutoCloseable, B](resource: A)(block: A => B): B = {
    Try(block(resource)) match {
      case Success(result) =>
        resource.close()
        result
      case Failure(e) =>
        resource.close()
        throw e
    }
  }

  def apply[Tup, L <: HList, Len <: Nat, B](resources: Tup)(block: Tup => B)(
    implicit gen: Generic.Aux[Tup, L],
    con: ToList[L, AutoCloseable],
    length: Length.Aux[L, Len],
    gt: GT[Len, nat._1]
  ): B = {

    Try(block(resources)) match {
      case Success(result) =>
        gen.to(resources).toList.foreach {
          _.close()
        }
        result
      case Failure(e) =>
        gen.to(resources).toList.foreach {
          _.close()
        }
        throw e
    }
  }
}