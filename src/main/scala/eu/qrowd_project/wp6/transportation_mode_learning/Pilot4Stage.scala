package eu.qrowd_project.wp6.transportation_mode_learning

import eu.qrowd_project.wp6.transportation_mode_learning.util.TrackPoint

/**
  * Stage class corresponding to the following SQLite schema:
  *
  *   trip
  *
  *   CREATE TABLE IF NOT EXISTS "trip" (
  *      "trip_id" INTEGER NOT NULL PRIMARY KEY,
  *      "citizen_id" VARCHAR(255) NOT NULL,
  *      "start_coordinate" VARCHAR(255) NOT NULL,
  *      "start_address" VARCHAR(255) NOT NULL,
  *      "stop_coordinate" VARCHAR(255) NOT NULL,
  *      "stop_address" VARCHAR(255) NOT NULL,
  *      "start_timestamp" DATETIME NOT NULL,
  *      "stop_timestamp" DATETIME NOT NULL,
  *      "transportation_mode" VARCHAR(255),
  *      "segment_confidence" REAL,
  *      "transportation_confidence" REAL,
  *      "path" TEXT,
  *      "json_file_path" TEXT, FOREIGN KEY ("citizen_id") REFERENCES "citizen" ("citizen_id")
  *   );
  *
  * A stage is a sequence of points representing a movement done with one means
  * of transportation, like e.g. a bus trip from a start to a destination stop.
  */
case class Pilot4Stage(userID: String, mode: String, start: TrackPoint,
                       startAddress: String, stop: TrackPoint,
                       stopAddress: String, segmentConfidence: Double = Double.NaN,
                       modeConfidence: Double = Double.NaN,
                       trajectory: Seq[TrackPoint], jsonFilePath: String) {
}
