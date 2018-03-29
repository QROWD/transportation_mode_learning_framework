package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.time.LocalDateTime
import java.time.temporal.{ChronoUnit, TemporalUnit}

/**
  * @author Lorenz Buehmann
  */
object GPSTrajectorySplitter {

  def split(entries: Seq[TrackPoint], value: Int = 1, unit: TemporalUnit) = {
    var list = Seq[(LocalDateTime, Seq[TrackPoint])]()

    val first = entries.head.timestamp.toLocalDateTime.truncatedTo(ChronoUnit.DAYS)
    val last = entries.last.timestamp.toLocalDateTime.plusDays(1).truncatedTo(ChronoUnit.DAYS)
    println(s"First day: $first")
    println(s"Last day: $last")

    var current = first
    while(current.isBefore(last)) {
      val next = current.plus(value, unit)

      val currentEntries = entries.filter(e => {
        e.timestamp.toLocalDateTime.isAfter(current) && e.timestamp.toLocalDateTime.isBefore(next)
      })
      println(s"$current  --  $next: ${currentEntries.size} entries")

      list :+= (current, currentEntries)

      current = next
    }
    //    (list.indices zip list).toMap
    list
  }

}
