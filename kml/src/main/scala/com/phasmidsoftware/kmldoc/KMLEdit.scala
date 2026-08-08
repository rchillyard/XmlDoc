package com.phasmidsoftware.kmldoc

import cats.effect.IO
import cats.implicits._
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Try
import scala.util.parsing.combinator.JavaTokenParsers

/**
 * Case class to represent the definition of a KML edit.
 *
 * @param command  the function of the edit.
 * @param operands the number of operands required for this edit.
 * @param op1      the target of the edit.
 * @param maybeOp2 the result of the edit.
 */
case class KmlEdit(command: String, operands: Int, op1: Element, maybeOp2: Option[Element]) {
  def isValid: Boolean = command.nonEmpty && operands > 0
}

/**
 * Companion object for KmlEdit, providing methods and constants used for editing KML elements.
 */
object KmlEdit {

  /**
   * Determines the number of operands required for a given command.
   *
   * @param command the command string to evaluate.
   * @return the number of operands required for the specified command.
   */
  def operands(command: String): Int = command match {
    case JOIN | JOINX => 2
    case DELETE => 1
    case INVERT => 1
    case _ => 0
  }

  /**
   * Process the given feature set and return a new feature set that is the result of the processing.
   *
   * @param edit the Edit to be applied.
   * @param fs   a sequence of Feature, the children of a particular object that extends HasFeatures (i.e. Kml, Document, or Folder).
   * @return a (potentially) different sequence of Feature.
   */
  def editFeatures(edit: KmlEdit, fs: Seq[Feature]): Seq[Feature] = for (f <- fs; z <- f.editToOption(edit, fs)) yield z

  /**
   * Method to create a KmlEdit from a command, an Element, and an optional Element.
   *
   * @param command  the command string.
   * @param op1      the first element.
   * @param maybeOp2 (optional) second element.
   * @return a KmlEdit.
   */
  def apply(command: String, op1: Element, maybeOp2: Option[Element]): KmlEdit = KmlEdit(command, operands(command), op1, maybeOp2)

  /**
   * Method to parse a line of text as a KmlEdit.
   *
   * @param w the String to be parsed.
   * @return an IO[KmlEdit].
   */
  def parse(w: String): IO[KmlEdit] = IO.fromTry(parser.parseEdit(w -> 0)) // need to get the line sequence number

  /**
   * Method to parse an iterator of lines of text.
   *
   * @param ws an Iterator[String] typically from Source.getLines.
   * @return a Seq[KmlEdit] wrapped in IO.
   */
  def parseLines(ws: Iterator[String]): IO[Seq[KmlEdit]] = (for (w <- ws) yield parse(w)).toSeq.sequence

  /**
   * Join two elements (typically using the merge method of Mergeable KML objects).
   */
  val JOIN = "join"

  /**
   * like JOIN but retains the first name (i.e. it excludes the second name).
   */
  val JOINX = "joinX"

  /**
   * delete an element.
   */
  val DELETE = "delete"

  /**
   * invert an element.
   */
  val INVERT = "invert"

  private val parser = KMLEditParser.apply
}

/**
 * Case class to represent a KML element that will take part in an edit.
 *
 * @param tag the tag.
 * @param name the identifier.
 */
case class Element(tag: String, name: String)

/**
 * KMLEditParser: class to parse lines of an edit file.
 * NOTE: list elements always appear as a string in the form { element0 , element1 , ... }
 *
 * NOTE: Much of this is probably overkill (it was copied from TableParser).
 *
 * @param enclosures    the enclosure characters around a list (if any).
 * @param listSeparator the list separator character.
 * @param quote         the quote character which is able to preempt the string regex:
 *                      between two quote characters,
 *                      there can be any number of any character (other than quote).
 * @param verbose       will print the various parameters.
 */
class KMLEditParser(enclosures: String, listSeparator: Char, quote: Char, verbose: Boolean = false) extends JavaTokenParsers {

  if (verbose) KMLEditParser.logger.info(s"enclosures: '$enclosures', quote: '$quote', listSeparator: '$listSeparator', ")
  runChecks()

  override def skipWhitespace: Boolean = false

  /**
   * Method to parse a Row.
   *
   * NOTE: the expression "end of input expected" must be the same as the failure defined in (trait) Parsers: def phrase[T](p: Parser[T]): Parser[T]
   * It's a shame that they didn't make it a constant in Parsers!
   *
   * @param indexedString a tuple of String and Int denoting the line and its index in the file.
   * @return a Try[Strings].
   */
  def parseEdit(indexedString: (String, Int)): Try[KmlEdit] = parseAll(line, indexedString._1) match {
    case Success(s, _) => scala.util.Success(s)
    case Failure("end of input expected", _) => scala.util.Failure(MultiLineException(s"at line ${indexedString._2}: ${indexedString._1}"))
    case Failure(x, _) => scala.util.Failure(formException(indexedString, x))
    case Error(x, _) => scala.util.Failure(formException(indexedString, x))
  }

  /**
   * Parser for a line representing a KML edit command.
   *
   * This parser constructs a `KmlEdit` instance by parsing the input to extract a command,
   * a mandatory first element, and an optional second element.
   * It combines these components
   * using the `KmlEdit` case class. The use of conjunction allows linking a second element
   * if present. Blank spaces are handled as per provided rules in the parsing sequence.
   *
   * The parser follows the pattern:
   * `command ~ blank ~ element ~ opt(blank ~ conjunction ~ blank ~ element)`
   *
   * Components:
   * - `command`: Represents the operation to be performed.
   * - `element`: The primary target for the operation.
   * - `conjunction`: Optionally combines a second element with the command.
   * - `blank`: Represents spaces that may occur between parts of the input.
   * - `KmlEdit`: Combines the parsed parts into a structured representation.
   */
  lazy val line: Parser[KmlEdit] = command ~ (blank ~> element) ~ opt(blank ~> conjunction ~> blank ~> element) ^^ { case c ~ e1 ~ e2o => KmlEdit(c, e1, e2o) }

  /**
   * A lazy val representing a parser for a single command.
   * The parser is expected to parse an identifier, which represents the command string.
   */
  lazy val command: Parser[String] = identifier

  /**
   * Parses an `Element` consisting of a `tag` followed by an `identifier` separated by optional whitespace.
   * The `tag` is parsed using the `tag` parser, and the `identifier` is parsed using the `identifier` parser.
   * Parsed values are combined into an `Element` case class instance.
   *
   * The parser discards any whitespace following the `tag` due to the `<~ blank` combinator.
   *
   * @return a parser that produces an `Element` with parsed `tag` and `identifier`.
   */
  lazy val element: Parser[Element] = (tag <~ blank) ~ identifier ^^ { case t ~ i => Element(t, i) }

  /**
   * Parser that matches a sequence of characters that do not contain spaces or quotation marks.
   *
   * This parser captures strings that consist of non-whitespace and non-quotation
   * characters using a regular expression pattern.
   */
  lazy val tag: Parser[String] = """[^ "]*""".r

  /**
   * A parser that matches a sequence of characters that are neither a comma, a space,
   * nor a double quote.
   * It is defined using a regular expression parser.
   *
   * This lazy value avoids immediate initialization and allows the regular expression
   * to be used as part of a combinator parsing process.
   */
  lazy val string: Parser[String] = """[^, "]*""".r

  /**
   * A parser that matches zero or more occurrences of whitespace characters
   * and combines them into a single string.
   *
   * This parser uses the `rep` combinator to repeatedly match `whiteSpace` zero or more times.
   * The resulting list of matched strings is concatenated into a single string using `mkString("")`.
   *
   * @return A Parser that produces a string containing the matched whitespace characters.
   */
  lazy val blank: Parser[String] = rep(whiteSpace) ^^ (xs => xs.mkString(""))

  /**
   * A lazy val representing a parser for the literal string "with".
   *
   * Used to match and parse the conjunction keyword in KML editing/parser contexts
   * as part of the broader parsing logic within the `KMLEditParser` class.
   *
   * It is defined as `private lazy` to ensure encapsulation and to initialize
   * the parser only when needed, improving efficiency.
   */
  private lazy val conjunction: Parser[String] = "with"

  /**
   * A lazy parser that attempts to parse an identifier.
   *
   * The identifier can be:
   * - A quoted string.
   * - A plain string.
   *
   * If the input does not match any of the above patterns, it fails with the message "invalid identifier".
   * This parser uses the logical OR (`|`) operator to sequentially try each parsing rule until one succeeds or all fail.
   */
  private lazy val identifier: Parser[String] = quotedString | string | failure("invalid identifier")

  /**
   * A lazily-defined `Parser` used to define the behavior of a `cell` in the input.
   *
   * The `cell` parser attempts to match one of the following:
   * - `quotedString`: A parser that interprets strings enclosed in quotes.
   * - `list`: A parser that matches a list structure.
   * - `string`: A parser for general strings.
   * - `failure("invalid string")`: A fallback parser that gracefully handles invalid inputs.
   *
   * This parser is private to the `KMLEditParser` class and is used in the processing of KML edits.
   */
  private lazy val cell: Parser[String] = quotedString | list | string | failure("invalid string")

  /**
   * Parses a quoted string using one of three options:
   * - `quotedStringWithQuotes`: Parses a string enclosed in quotation marks.
   * - `pureQuotedString`: Parses a simplified quoted string.
   * - Returns a failure message "invalid quoted string" if neither of the above patterns match.
   *
   * This parser combines different quoted string parsing strategies into a single lazy evaluation.
   */
  lazy val quotedString: Parser[String] = quotedStringWithQuotes | pureQuotedString | failure("invalid quoted string")

  /**
   * A parser that matches and extracts a string enclosed within quotes.
   * This parser skips the opening and closing quote characters, returning
   * only the content within the quotes.
   *
   * The structure of the parser is defined as:
   * - `quote`: Matches the opening quote character.
   * - `stringInQuotes`: Matches the content between the quotes.
   * - `quote`: Matches the closing quote character.
   */
  private lazy val pureQuotedString: Parser[String] = quote ~> stringInQuotes <~ quote

  /**
   * A parser for extracting a string enclosed in quotes, excluding the quote character.
   *
   * Uses a regular expression to match any sequence of characters that does not
   * contain the defined quote character.
   * The matched substring becomes the result of this parser.
   */
  private lazy val stringInQuotes: Parser[String] = s"""[^$quote]*""".r

  private type Strings = Seq[String]

  /**
   * A parser that processes a quoted string, including the surrounding quotes.
   *
   * This parser first utilizes `quotedStringWithQuotesAsList` to parse the quoted string as a list of components.
   * Afterwards, it concatenates the components back into a single string while retaining the original quotes.
   *
   * The definition depends on the surrounding environment where `quote` is defined to represent the quote character used.
   */
  private lazy val quotedStringWithQuotes: Parser[String] = quotedStringWithQuotesAsList ^^ (ws => ws.mkString(s"$quote"))

  /**
   * A parser that processes a quoted string containing substrings separated by double quotes (`""`),
   * where the entire string is enclosed in quotes (`"`).
   * This is parsed as a list of substrings.
   *
   * The structure of this parser is as follows:
   * - It starts by consuming an opening quote (`quote`).
   * - Then parses a sequence of substrings (`stringInQuotes`) separated by double quotes (`""`).
   * - Finally, it consumes a closing quote (`quote`).
   *
   * This parser uses the following components:
   * - `quote`: Represents the quote character.
   * - `stringInQuotes`: Represents each individual substring within the quoted string.
   * - The `repsep` parsing combinator ensures that the substrings are separated by double quotes.
   */
  private lazy val quotedStringWithQuotesAsList: Parser[Strings] = quote ~> repsep(stringInQuotes, s"$quote$quote") <~ quote

  /**
   * A lazy parser that parses a list structure with specific delimiters and formats it as a string.
   *
   * The parser expects the list to be enclosed within specific open and close characters,
   * which are obtained via the methods `getOpenChar` and `getCloseChar`.
   * Within the list, components are separated by a defined list separator (`listSeparator`).
   *
   * It matches the following structure:
   * - Starts with an open character defined by `getOpenChar`.
   * - Parses a component followed by a list separator.
   * - Parses one or more additional components, each separated by the list separator.
   * - Ends with a close character defined by `getCloseChar`.
   *
   * After parsing, the components are concatenated into a string and formatted as a comma-separated
   * list enclosed in curly braces.
   *
   * Example format produced: `{element1,element2,element3}`
   */
  private lazy val list: Parser[String] = getOpenChar ~> (component ~ listSeparator ~ rep1sep(component, listSeparator)) <~ getCloseChar ^^ { case x ~ _ ~ xs => (x +: xs).mkString("{", ",", "}") }

  /**
   * A regular expression pattern used to match components in a string.
   * This pattern excludes the specified list separator and delimiter characters.
   * It is utilized within the parsing logic of the KMLEditParser class to identify
   * and extract individual elements during the parsing process.
   */
  private val regexComponent = s"""[^,$listSeparator}]+"""

  /**
   * A lazy val that defines a parser for matching components using a regular expression.
   * Utilizes the `regexComponent` value and ensures the parsing logic is only
   * instantiated when needed to optimize performance.
   */
  private lazy val component: Parser[String] = regexComponent.r

  /**
   * Lazy value that represents a parser for obtaining the "open character" based on the first element
   * within the `enclosures` collection.
   * If `enclosures` is empty, the parser defaults to an empty string.
   */
  private lazy val getOpenChar: Parser[String] = s"${enclosures.headOption.getOrElse("")}"

  /**
   * A lazy evaluation of the parser that retrieves the closing character
   * from the last element of the `enclosures` sequence.
   * If the sequence is empty, it defaults to an empty string.
   *
   * Used in parsing to handle enclosures such as brackets, quotes, etc.
   */
  private lazy val getCloseChar: Parser[String] = s"${enclosures.lastOption.getOrElse("")}"

  /**
   * Constructs a ParserException with a detailed error message indicating the failure to parse a specific line.
   *
   * @param indexedString A tuple containing the line content as a String and its index as an Int.
   * @param x             A String describing the reason for the parsing failure.
   */
  private def formException(indexedString: (String, Int), x: String) = ParserException(s"Cannot parse line ${indexedString._2}: '${indexedString._1}' due to: $x")

  // XXX used only for debugging
  override def toString: String = s"""LineParser: listSeparator='$listSeparator', enclosures='$enclosures', quote="$quote""""

  /**
   * Provides the character used as a delimiter during the parsing process.
   * This is a lazy value initialized to a single space character (' ').
   * The delimiter is used to separate components when parsing KML edits.
   */
  private lazy val getDelimiterChar: Char = ' '

  /**
   * Executes a series of validation checks to ensure the integrity and correctness of specific `Parser` objects
   * within the `KMLEditParser` context.
   * It verifies the parser behavior against expected input and matched values,
   * and logs any warnings if discrepancies occur.
   *
   * The method internally uses an implicit `Trial` class to chain multiple validations and handle potential
   * failures gracefully. Any warnings or errors encountered during the checks are logged using the `KMLEditParser.logger`.
   *
   * @return A `Unit` indicating the completion of the validation checks.
   */
  private def runChecks(): Unit = {
    def check[X](parser: Parser[X], input: String, matchedValue: X) =
      Try(parseAll(parser, input) match {
        case Success(`matchedValue`, _) =>
        case Success(z, _) => throw ParserException(s"Warning: (LineParser constructor validity check): '$input' did not result in $matchedValue but instead matched: $z")
        case Failure(z, _) => throw ParserException(s"Warning: (LineParser constructor validity check): '$input' did not result in $matchedValue because: $z")
        case Error(z, _) => throw ParserException(s"Warning: (LineParser constructor validity check): '$input' did not result in $matchedValue because: $z")
      }
      )

    /**
     * Implicit class which operates on a `Try[Unit]`
     *
     * @param x a `Try[Unit]`
     */
    implicit class Trial(x: Try[Unit]) {
      /**
       * Logs a warning message if the `Try[Unit]` instance represents a failure.
       * If the instance is a `Failure`, it extracts the failure cause and logs the message
       * prefixed with "squawk: ".
       * If the instance is a success, no action is taken.
       *
       * @return Unit, indicating that the operation does not produce any resulting value.
       */
      def squawk(): Unit = x match {
        case scala.util.Failure(z) => KMLEditParser.logger.warn(s"squawk: $z")
        case _ =>
      }

      /**
       * Performs a logical AND operation between two `Trial` instances.
       * If the current instance is a `Success`, it evaluates and returns the provided `Trial` instance.
       * Otherwise, if the current instance is a `Failure`, it is returned without evaluating the provided `Trial`.
       *
       * @param y another `Trial` instance to evaluate if the current instance is a `Success`
       * @return the provided `Trial` instance if the current instance is a `Success`, otherwise the current `Trial` instance
       */
      def &&(y: Trial): Trial =
        if (x.isSuccess) y else x
    }

    /**
     * Following is the code that implements `runChecks`
     */
    (
      check(cell, "Hello", "Hello") &&
        //        check(cell, "http://www.imdb.com/title/tt0499549/?ref_=fn_tt_tt_1", "http://www.imdb.com/title/tt0499549/?ref_=fn_tt_tt_1") &&
        check(quotedString, s"""${quote}Hello${getDelimiterChar}Goodbye$quote""", s"""Hello${getDelimiterChar}Goodbye""")
      ).squawk()
  }

}

/**
 * Companion object for the `KMLEditParser` class, providing utilities and default instantiation.
 *
 * The `apply` method creates a new instance of `KMLEditParser` with default settings.
 *
 * This object also includes a logger instance for managing logging across the parser.
 */
object KMLEditParser {

  /**
   * Creates a new instance of `KMLEditParser` with default configuration.
   *
   * The parser is initialized with a default JSON string, a default delimiter character,
   * and a default quote character.
   *
   * @return A new instance of `KMLEditParser` configured with default parameters.
   */
  def apply: KMLEditParser = new KMLEditParser("{}", '|', '"')

  /**
   * Logger instance used for logging messages related to the functionality
   * of the `KMLEditParser` companion object.
   * This logger is configured to
   * log messages for the `KMLEditParser` class and its utilities.
   */
  val logger: Logger = LoggerFactory.getLogger(KMLEditParser.getClass)
}

/**
 * Represents an exception that occurs during the parsing process.
 *
 * @param msg the detail message describing the error encountered
 * @param e   the underlying cause of the exception, if any (optional)
 */
case class ParserException(msg: String, e: Throwable = null) extends Exception(msg, e)

/**
 * A case class representing an exception that formats the provided input into a multi-line
 * exception message.
 * This can be used to encapsulate exception-related details in a formatted string.
 *
 * @tparam X The type of the input that will be converted into an exception message.
 * @param x The input value that will be incorporated into the exception message.
 */
case class MultiLineException[X](x: X) extends Exception(s"multi-line exception: $x")

/**
 * Trait to satisfy invert command.
 * See also comments re: Mergeable.
 *
 * @tparam T the underlying type.
 */
trait Invertible[T] {
  def invert: Option[T]
}