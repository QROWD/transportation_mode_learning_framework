package eu.qrowd_project.wp6.transportation_mode_learning.scripts

import java.nio.file.Paths
import java.time.LocalDate

case class TrentoStudyConfig(
                              date: LocalDate = LocalDate.now(),
                              writeDebugOutput: Boolean = false,
                              tripSQLiteFilePath: String =
                                Paths.get(System.getProperty("java.io.tmpdir"))
                                  .resolve("trips.sqlite").toString,
                              stageSQLiteFilePath: String =
                                Paths.get(System.getProperty("java.io.tmpdir"))
                                  .resolve("stages.sqlite").toString,
                              dryRun: Boolean = false,
                              userIdsOnly: Boolean = false,
                              usersFromCassandra: Boolean = false,
                              alwaysOSM: Boolean = false,
                              threads: Int = 1,
                              singleUserID: String = null
                            ) {}
