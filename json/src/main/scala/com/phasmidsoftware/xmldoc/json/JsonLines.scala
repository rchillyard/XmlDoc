package com.phasmidsoftware.xmldoc.json

import io.circe.Encoder
import io.circe.syntax.*

/**
 * Rendering records as JSON, one compact object per line (JSON Lines, as a Kafka message value or a `.jsonl` file).
 */
object JsonLines {

  /**
   * Method to render one record as compact JSON (no spaces or newlines).
   *
   * @param t the record.
   * @tparam T the type of the record, which has a circe `Encoder`.
   * @return the JSON.
   */
  def line[T: Encoder](t: T): String = t.asJson.noSpaces

  /**
   * Method to render records as JSON Lines, lazily.
   *
   * @param ts the records.
   * @tparam T the type of each record, which has a circe `Encoder`.
   * @return an iterator of the records' JSON, one per record.
   */
  def lines[T: Encoder](ts: IterableOnce[T]): Iterator[String] = ts.iterator map (t => line(t))

  /**
   * Method to render records as one JSON Lines string: each record's JSON followed by a newline.
   *
   * @param ts the records.
   * @tparam T the type of each record, which has a circe `Encoder`.
   * @return the JSON Lines text.
   */
  def render[T: Encoder](ts: IterableOnce[T]): String = lines(ts).map(_ + "\n").mkString
}
