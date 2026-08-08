package com.phasmidsoftware.core

/**
 * A utility class that wraps around a StringBuilder and provides additional functionality
 * such as appending strings with optional padding, trimming trailing spaces, and formatting output.
 * Most of the methods appear to be non-mutating pure functions but, in fact,
 * they all take advantage of the mutable nature of SmartBuffer.
 *
 * @param sb the underlying StringBuilder instance.
 */
case class SmartBuffer(sb: StringBuilder) {

  /**
   * Method to append a String (delegates to sb.append)
   *
   * @param s the String to be appended.
   */
  def append(s: String): SmartBuffer = {
    val delimAppend = s.startsWith("\n")
    if (delimAppend) trim
    sb.append(s)
    if (sb.result().contains(" \n"))
      println(s"SmartBuffer: ${sb.result()}") // XXX what's going on here?
    this
  }

  /**
   * Method to append a String (delegates to sb.append) with padding if necessary.
   *
   * @param s the String to be appended.
   * @return this SmartBuffer (mutated)
   */
  def appendPadded(s: String): SmartBuffer = {
    if (sb.nonEmpty && !delimExist && s.nonEmpty && !s.startsWith("\n")) sb.append(" ")
    append(s)
  }

  /**
   * Trims trailing spaces from the internal StringBuilder of this SmartBuffer.
   *
   * @return this SmartBuffer instance after mutating its content.
   */
  def trim: SmartBuffer = {
    SmartBuffer.trimStringBuilder(sb)
    this
  }

  /**
   * Clears the internal StringBuilder content while returning the current result.
   *
   * @return the result string before the internal StringBuilder is cleared.
   */
  def clear: String = {
    val s = result
    sb.clear()
    s
  }

  /**
   * Get the resulting String.
   *
   * NOTE: this method contains a hack which collapses output of the form &lt;tag ...&gt;&lt;/tag&gt; into &lt;tag .../&gt;
   * As such, it is useful for e.g. hotSpot which has no children.
   *
   * NOTE: the method assumes there is at most one newline character at the beginning of the string.
   *
   * @return
   */
  def result: String = {
    val str = sb.result()
    if (str.isEmpty) str
    else {
      val regex = """(\s*)<([a-zA-Z0-9 ="]+)></(\w+)>""".r
      str.substring(1) match {
        case regex(p, q, _) => str.substring(0, 1) + p + "<" + q + "/>"
        case _ => str
      }
    }
  }

  /**
   * Returns the string representation of the internal state of this object.
   *
   * @return the string representation of the internal StringBuilder.
   */
  override def toString: String = sb.toString

  /**
   * Return tru if sb ends with a delimiter such as ">"
   *
   * @return
   */
  private def delimExist: Boolean = {
    val result = sb.result()
    result.endsWith(">") || result.endsWith("{")
  }

}

/**
 * Represents a utility class for efficiently building and manipulating strings
 * using a mutable `StringBuilder`.
 * Provides methods to append strings with
 * optional padding, trim trailing spaces, clear the buffer, and retrieve the
 * current result as a string.
 */
object SmartBuffer {
  /**
   * Creates a new instance of `SmartBuffer` initialized with an empty `StringBuilder`.
   * This method provides a convenient way to instantiate a `SmartBuffer` object.
   *
   * @return a new instance of `SmartBuffer` with an empty internal `StringBuilder`.
   */
  def apply(): SmartBuffer = new SmartBuffer(new StringBuilder())

  /**
   * Trims trailing spaces from the given StringBuilder instance.
   * CONSIDER a faster way to do this
   *
   * @param sb the StringBuilder instance to modify by removing trailing spaces
   * @return Unit
   */
  def trimStringBuilder(sb: StringBuilder): Unit = {
    while (sb.endsWith(" ")) sb.setLength(sb.length() - 1)
  }

}
