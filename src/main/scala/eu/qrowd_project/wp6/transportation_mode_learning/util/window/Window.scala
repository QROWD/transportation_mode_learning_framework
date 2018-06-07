package eu.qrowd_project.wp6.transportation_mode_learning.util.window

import java.util.concurrent.TimeUnit

class Window(val numEntries: Int) {}

object Window {
  def apply(numEntries: Int): Window = new Window(numEntries)
}

class TimeWindow(timeValue: Long,
                 timeUnit: TimeUnit = TimeUnit.SECONDS,
                 resolutionValue: Long = 50,
                 resolutionTimeUnit: TimeUnit = TimeUnit.MILLISECONDS)
  extends Window((timeUnit.toMillis(timeValue) / resolutionTimeUnit.toMillis(resolutionValue)).toInt) {}

object TimeWindow {
  def of(timeValue: Long,
         timeUnit: TimeUnit = TimeUnit.SECONDS,
         resolutionValue: Long = 50,
         resolutionTimeUnit: TimeUnit = TimeUnit.MILLISECONDS) =
    new TimeWindow(timeValue, timeUnit, resolutionValue, resolutionTimeUnit)
}