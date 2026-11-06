package com.phasmidsoftware.xmldoc.json

/**
 * A Spark SQL data type, as it appears in a DDL schema string (e.g. `STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>`).
 *
 * This is a model of just the types which a JSON record can map to, so it needs no Spark dependency.
 * See [[SparkSchema]], which derives one from a case class.
 */
enum SparkType {

  /**
   * An atomic type, such as `STRING`, `INT` or `DOUBLE`.
   *
   * @param name the Spark SQL name of the type.
   */
  case Atomic(name: String)

  /**
   * An array (a Scala `Seq`, `List`, `Vector` or `Set`).
   *
   * @param element the type of each element.
   */
  case Array(element: SparkType)

  /**
   * A map with string keys (a Scala `Map[String, X]`, which circe writes as a JSON object).
   *
   * @param value the type of each value.
   */
  case Map(value: SparkType)

  /**
   * A struct (a Scala case class).
   *
   * @param fields the name and type of each field, in declaration order.
   */
  case Struct(fields: Seq[(String, SparkType)])

  /**
   * The Spark SQL text of this type, e.g. `ARRAY<STRING>` or `STRUCT<`lon`: DOUBLE, `lat`: DOUBLE>`.
   *
   * @return this type, as it appears in a DDL schema string.
   */
  def sql: String = this match {
    case Atomic(name) => name
    case Array(element) => s"ARRAY<${element.sql}>"
    case Map(value) => s"MAP<STRING, ${value.sql}>"
    case Struct(fields) => fields.map((name, t) => s"${SparkType.quote(name)}: ${t.sql}").mkString("STRUCT<", ", ", ">")
  }
}

object SparkType {
  val string: SparkType = Atomic("STRING")
  val boolean: SparkType = Atomic("BOOLEAN")
  val byte: SparkType = Atomic("TINYINT")
  val short: SparkType = Atomic("SMALLINT")
  val int: SparkType = Atomic("INT")
  val long: SparkType = Atomic("BIGINT")
  val float: SparkType = Atomic("FLOAT")
  val double: SparkType = Atomic("DOUBLE")
  val decimal: SparkType = Atomic("DECIMAL(38,18)")
  val date: SparkType = Atomic("DATE")
  val timestamp: SparkType = Atomic("TIMESTAMP")

  /**
   * Method to quote a field name with backticks, as Spark SQL requires for a name which is a reserved word
   * (`order`, `from`, ...) or which has characters other than letters, digits and underscores.
   * Every name is quoted, so that the schema never depends on Spark's list of reserved words.
   *
   * @param name a field name.
   * @return the name between backticks, with any backtick in it doubled.
   */
  def quote(name: String): String = s"`${name.replace("`", "``")}`"
}
