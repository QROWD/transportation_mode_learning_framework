package eu.qrowd_project.wp6.transportation_mode_learning.util

import java.nio.file.Paths

import io.eels.component.parquet.ParquetSource
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem

/**
  * A loader for location event records from Parquet file.
  *
  * @author Lorenz Buehmann
  */
trait ParquetLocationEventRecordLoader {

  /**
    * Load data from Parquet file via Eels API.
    *
    * @param path the path to load from
    * @return the records
    */
  private def loadData(path: String): Seq[LocationEventRecord] = {
    // This is required
    implicit val hadoopConfiguration: Configuration = new Configuration()
    implicit val hadoopFileSystem: FileSystem = FileSystem.get(hadoopConfiguration)

    val source = ParquetSource(Paths.get(path))

    source
      .toDataStream()
      .collect
      .map(row => LocationEventRecord.from(row))
  }

}
