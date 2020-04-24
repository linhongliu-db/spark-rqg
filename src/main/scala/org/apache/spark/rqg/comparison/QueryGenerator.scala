package org.apache.spark.rqg.comparison

import org.apache.hadoop.fs.Path
import org.apache.spark.internal.Logging
import org.apache.spark.rqg.ast.{Column, Query, QueryContext, Table}
import org.apache.spark.rqg._
import org.apache.spark.rqg.parser.QueryGeneratorOptions
import org.apache.spark.rqg.runner.SparkSubmitQueryRunner
import org.apache.spark.sql.SparkSession

import scala.collection.mutable.ArrayBuffer

object QueryGenerator extends Logging {
  def main(args: Array[String]): Unit = {

    // NOTE: workaround for derby init exception in sbt:
    // java.security.AccessControlException:
    //   access denied org.apache.derby.security.SystemPermission( "engine", "usederbyinternals" )
    System.setSecurityManager(null)

    val options = QueryGeneratorOptions.parse(args)
    RandomUtils.setSeed(options.randomizationSeed)

    val warehouse = new Path(RQGUtils.getBaseDirectory, "warehouse").toString

    val rqgConfig = RQGConfig.load(options.configFile)

    val refQueryRunner = new SparkSubmitQueryRunner(
      options.refSparkVersion, options.refSparkHome, options.refMaster)
    val testQueryRunner = new SparkSubmitQueryRunner(
      options.testSparkVersion, options.testSparkHome, options.testMaster)

    val sparkSession = SparkSession.builder()
      .master("local[*]")
      .enableHiveSupport()
      .config("spark.sql.warehouse.dir", warehouse)
      .getOrCreate()

    val tables = describeTables(sparkSession, options.dbName)

    var queryIdx = 0
    val count = math.min(options.queryCount - queryIdx, 100)
    println(s"Generating $count queries to compare")

    var queries = ArrayBuffer[String]()
    var successCount = 0
    var failedCount = 0
    while (successCount < count && failedCount < count) {
      try {
        queries += Query(QueryContext(rqgConfig = rqgConfig, availableTables = tables)).sql
        successCount += 1
      } catch {
        case e: RQGEmptyChoiceException =>
          logInfo(e.toString)
          failedCount += 1
      }
    }
    println(s"Failed $failedCount times while generating queries")

    if (options.dryRun) {
      println("Running in dryRun mode")
      val queryStrs = queries
      queryStrs.zipWithIndex.foreach {
        case (query, i) => println(f"Query ${i}: ${query}")
      }
    } else {
      while (queryIdx < options.queryCount) {

        val extraSparkConf = rqgConfig.getSparkConfigs.map(
          entry => entry._1 -> RandomUtils.nextChoice(entry._2)
        )
        println("Running queries with randomly generated spark configurations: ")
        println(extraSparkConf.mkString("\n"))
        println(s"Running queries $queryIdx to ${queryIdx + count - 1} in Reference Spark version")
        val refResult = refQueryRunner.runQueries(queries, extraSparkConf)
        println(s"Running queries $queryIdx to ${queryIdx + count - 1} in Test Spark version")
        val testResult = testQueryRunner.runQueries(queries, extraSparkConf)
        println(s"Comparing queries $queryIdx to ${queryIdx + count - 1}")

        refResult.zip(testResult).foreach {
          case (refOutput, testOutput) =>
            assert(refOutput.sql == testOutput.sql, "Comparing result between different queries")
            println(s"Comparing query $queryIdx: ${refOutput.sql}")
            if (refOutput.output.contains("Exception") || testOutput.output.contains("Exception")) {
              val errorMessage =
                s"""== Exception occurs while executing query ==
                  |== Expected Answer ==
                  |schema: ${refOutput.schema}
                  |output: ${refOutput.output}
                  |== Actual Answer ==
                  |schema: ${testOutput.schema}
                  |output: ${testOutput.output}
                """.stripMargin
              if (options.stopOnCrash) {
                sys.error(errorMessage)
              } else {
                println(errorMessage)
              }
            } else if (refOutput != testOutput) {
              val errorMessage =
                s"""== Result did not match ==
                  |== Expected Answer ==
                  |schema: ${refOutput.schema}
                  |output: ${refOutput.output}
                  |== Actual Answer ==
                  |schema: ${testOutput.schema}
                  |output: ${testOutput.output}
                """.stripMargin
              if (options.stopOnMismatch) {
                sys.error(errorMessage)
              } else {
                println(errorMessage)
              }
            } else {
              println("PASS.")
            }
            queryIdx += 1
        }
      }
    }

    sparkSession.stop()
  }

  def describeTables(sparkSession: SparkSession, dbName: String): Array[Table] = {
    import  sparkSession.implicits._
    sparkSession.sql(s"USE $dbName")
    val tableNames = sparkSession.sql("SHOW TABLES").select("tableName").as[String].collect()
    tableNames.map { tableName =>
      val result = sparkSession.sql(s"DESCRIBE $tableName")
        .select("col_name", "data_type").as[(String, String)].collect()
      val columns = result.map {
        case (columnName, columnType) => Column(tableName, columnName, parseDataType(columnType))
      }
      Table(tableName, columns)
    }
  }

  private def parseDataType(dataType: String): DataType[_] = {
    val decimalPattern = "decimal\\(([0-9]+),([0-9]+)\\)".r
    dataType match {
      case "boolean" => BooleanType
      case "tinyint" => TinyIntType
      case "smallint" => SmallIntType
      case "int" => IntType
      case "bigint" => BigIntType
      case "float" => FloatType
      case "double" => DoubleType
      case "string" => StringType
      case "date" => DateType
      case "timestamp" => TimestampType
      case decimalPattern(precision, scale) => DecimalType(precision.toInt, scale.toInt)
    }
  }
}
