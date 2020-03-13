package org.apache.spark.rqg

import scala.collection.JavaConverters._

import com.typesafe.config.{Config, ConfigFactory}

class RQGConfig(config: Config) {

  def getBound(entry: RQGConfigEntry): (Int, Int) = {
    if (config.hasPath(entry.key)) {
      val v = config.getIntList(entry.key)
      (v.get(0), v.get(1))
    } else {
      entry.defaultValue.asInstanceOf[(Int, Int)]
    }
  }

  def getProbability(entry: RQGConfigEntry): Double = {
    if (config.hasPath(entry.key)) {
      config.getDouble(entry.key)
    } else {
      entry.defaultValue.asInstanceOf[Double]
    }
  }

  def getWeight(entry: RQGConfigEntry): List[WeightEntry] = {
    if (config.hasPath(entry.key)) {
      config.getConfig(entry.key).entrySet().asScala.map { v =>
        WeightEntry(v.getKey, config.getDouble(entry.key + "." + v.getKey))
      }.toList
    } else {
      entry.defaultValue.asInstanceOf[List[WeightEntry]]
    }
  }

  def getSparkConfigs: Map[String, Array[String]] = {
    if (config.hasPath(RQGConfig.SPARK_CONF)) {
      config.getConfig(RQGConfig.SPARK_CONF).entrySet().asScala.map { v =>
        v.getKey -> config.getStringList(RQGConfig.SPARK_CONF + "." + v.getKey).asScala.toArray
      }.toMap
    } else {
      Map.empty
    }
  }
}

object RQGConfig {

  // Config keys
  val QUERY_PROFILE = "QUERY_PROFILE"
  val SPARK_CONF = "SPARK_CONF"

  // Category of query profile
  val BOUNDS = s"$QUERY_PROFILE.BOUNDS"
  val WEIGHTS = s"$QUERY_PROFILE.WEIGHTS"
  val PROBABILITIES = s"$QUERY_PROFILE.PROBABILITIES"

  // Bounds
  val MAX_NESTED_QUERY_COUNT = RQGConfigEntry(s"$BOUNDS.MAX_NESTED_QUERY_COUNT", (0, 2))
  val MAX_NESTED_EXPR_COUNT = RQGConfigEntry(s"$BOUNDS.MAX_NESTED_EXPR_COUNT", (0, 2))
  val SELECT_ITEM_COUNT = RQGConfigEntry(s"$BOUNDS.SELECT_ITEM_COUNT", (1, 5))
  val JOIN_COUNT = RQGConfigEntry(s"$BOUNDS.JOIN_COUNT", (0, 2))

  // Weights
  private val defaultJoinWeights =
    WeightEntry("CROSS", 0.01d) :: WeightEntry("FULL_OUTER", 0.04d) ::
      WeightEntry("INNER", 0.7d) :: WeightEntry("LEFT", 0.2d) :: WeightEntry("RIGHT", 0.05d) :: Nil
  val JOIN_TYPE = RQGConfigEntry(s"$WEIGHTS.JOIN_TYPE", defaultJoinWeights)

  private val defaultDataTypeWeights =
    WeightEntry("Int", 10d) :: WeightEntry("TinyInt", 2d) :: WeightEntry("SmallInt", 2d) ::
    WeightEntry("BigInt", 2d) :: WeightEntry("Float", 2d) :: WeightEntry("Double", 5d) ::
    WeightEntry("Boolean", 1d) :: WeightEntry("Decimal", 10d) :: WeightEntry("String", 2d) :: Nil
  val DATA_TYPE = RQGConfigEntry(s"$WEIGHTS.DATA_TYPE", defaultDataTypeWeights)

  // Probabilities
  val WITH = RQGConfigEntry(s"$PROBABILITIES.WITH", 0.0d)
  val FROM = RQGConfigEntry(s"$PROBABILITIES.FROM", 1.0d)
  val WHERE = RQGConfigEntry(s"$PROBABILITIES.WHERE", 0.5d)
  val GROUP_BY = RQGConfigEntry(s"$PROBABILITIES.GROUP_BY", 0.1d)
  val HAVING = RQGConfigEntry(s"$PROBABILITIES.HAVING", 0.25d)
  val UNION = RQGConfigEntry(s"$PROBABILITIES.UNION", 0.1d)
  val ORDER_BY = RQGConfigEntry(s"$PROBABILITIES.ORDER_BY", 0.1d)

  val SELECT_DISTINCT = RQGConfigEntry(s"$PROBABILITIES.SELECT_DISTINCT", 0.1d)

  def load(path: String = ""): RQGConfig = {
    if (path.isEmpty) {
      new RQGConfig(ConfigFactory.load("rqg-defaults.conf"))
    } else {
      new RQGConfig(ConfigFactory.load(path))
    }
  }
}

case class RQGConfigEntry(key: String, defaultValue: Any)

case class WeightEntry(key: String, value: Double)
