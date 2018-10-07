package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.sql.{Connection, DriverManager, ResultSet}
import java.time.format.DateTimeFormatter

import scalikejdbc.ConnectionPool
import scalikejdbc._

trait SQLiteAccess {

  // initialize JDBC driver & connection pool
//  Class.forName("org.sqlite.JDBC")
//  ConnectionPool.singleton("jdbc:sqlite", "user", "pass")

  Class.forName("org.sqlite.JDBC")
  var connection: Connection = _

  def connect(dbFilePath: String): Unit = {
    connection = DriverManager.getConnection(s"jdbc:sqlite:$dbFilePath")
  }

  def close(): Unit = {
    ConnectionPool.closeAll()
  }

  implicit val session = AutoSession
  def runQuery(queryStr: String): ResultSet = {
    connection.createStatement().executeQuery(queryStr)
  }

  def insert(query: String): Int = {
    sql"$query".executeUpdate().apply()
  }

  def select(query: String) = {
    sql"$query".map(rs => rs.int(0)).first.apply()
  }

  val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss")
}
