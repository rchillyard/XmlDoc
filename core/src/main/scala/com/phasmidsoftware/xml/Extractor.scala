package com.phasmidsoftware.xml

import com.phasmidsoftware.core.*
import com.phasmidsoftware.core.FP.{sequence, sequenceForgiving}
import com.phasmidsoftware.core.Utilities.{lensFilter, renderNode}
import com.phasmidsoftware.flog.Flog
import com.phasmidsoftware.xml.Extractors.extractOptional
import com.phasmidsoftware.xml.NamedFunction.name
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.matching.Regex
import scala.util.parsing.combinator.JavaTokenParsers
import scala.util.{Failure, Success, Try}
import scala.xml.{Node, NodeSeq}

/**
 * Trait to define the behavior of an extractor (parser) that takes an XML `Node`
 * and return `Try[T]` where `T` is the underlying type of the `Extractor`.
 *
 * @tparam T the type to be constructed.
 */
trait Extractor[T] extends NamedFunction[Extractor[T]] {
  self =>

  /**
   * Method to convert a `Node` into a `Try[T]`.
   *
   * @param node a `Node`.
   * @return a `Try[T]`.
   */
  def extract(node: Node): Try[T]

  /**
   * Method to map this `Extractor[T]` into an `Extractor[U]`.
   *
   * @param f a `T => U`.
   * @tparam U the underlying type of the result.
   * @return an `Extractor[U]`.
   */
  def map[U](f: T => U): Extractor[U] = Extractor { (node: Node) => self.extract(node) map f } ^^ s"map of $self"

  /**
   * Method to `flatMap` this `Extractor[T]` into an `Extractor[U]`.
   *
   * @param f a `T => Try[U]`.
   * @tparam U the underlying type of the result.
   * @return an `Extractor[U]`.
   */
  def flatMap[U](f: T => Try[U]): Extractor[U] = Extractor { (node: Node) => self.extract(node) flatMap f } ^^ s"flatMap of $self"

  /**
   * Method to create an `Extractor[T]` such that, if this `Extractor[T]` fails, then we invoke the (implicit) `Extractor[P]` instead.
   *
   * TESTME
   *
   * @tparam P the type of the alternative `Extractor`.
   *           `P` must provide implicit evidence of `Extractor[P]`, and `P` must be a subclass of `T`.
   * @return an `Extractor[T]`.
   */
  def |[P <: T : Extractor](): Extractor[T] = (node: Node) => self.extract(node) orElse implicitly[Extractor[P]].mapTo[T].extract(node)

  /**
   * Lifts this `Extractor[T]` into an `Extractor[Option[T]]`.
   * If the extraction is successful, the resulting value is wrapped in `Some`.
   *
   * @return an `Extractor[Option[T]]`, which extracts an `Option[T]` from a `Node`.
   */
  def lift: Extractor[Option[T]] = Extractor { (node: Node) => self.extract(node) map Some.apply } ^^ s"optional extractor of $self"

  /**
   * Method to create an `Extractor[P]` which instantiates a `Try[T]` but treats it as a `Try[P]` where `P` is a super-class of `T`.
   *
   * @tparam P the type of the `Extractor` we wish to return.
   * @return an `Extractor[P]`.
   */
  private def mapTo[P >: T]: Extractor[P] = (node: Node) => self.extract(node)
}

/**
 * Companion object to Extractor.
 */
object Extractor {

  val flog: Flog = Flog[Extractors]

  // NOTE: this is needed if you enable logging (by uncommenting) in extract and extractMulti methods (below).
//    import flog._

  /**
   * Method to create an Extractor[T] from a Node => Try[T] function.
   * Note that this isn't strictly necessary because of the SAM conversion mechanism which turns a Node => Try[T] function into an Extractor[T].
   *
   * @param extractorFunc a Node => Try[T] function.
   * @tparam T the underlying type of the resulting Extractor.
   * @return an Extractor[T].
   */
  def apply[T](extractorFunc: Node => Try[T]): Extractor[T] = (node: Node) => extractorFunc(node)

  /**
   * Method to create an Extractor[T] such that the result of the extraction is always a constant, regardless of what's in the node provided.
   *
   * @param ty a Try[T].
   * @tparam T the underlying type of the result.
   * @return an Extractor[T] which always produces ty when extract is invoked on it.
   */
  def apply[T](ty: => Try[T]): Extractor[T] = Extractor(_ => ty) ^^ s"Extractor.apply($ty)"

  /**
   * Method to create a lazy Extractor[T] from an explicit Extractor[T] which is call-by-name.
   * The purpose of this method is to break the infinite recursion caused when implicit values are defined recursively.
   * See the Play JSON library method in JsPath called lazyRead.
   *
   * @param te an Extractor[T].
   * @tparam T the underlying type of the input and output Extractors.
   * @return an Extractor[T].
   */
  def createLazy[T](te: => Extractor[T]): Extractor[T] = (node: Node) => te.extract(node)

  /**
   * Method to create an Extractor[T] from a String => Try[T] function.
   *
   * @param parser a String => Try[T] function.
   * @tparam T the underlying type of the resulting Extractor.
   * @return an Extractor[T].
   */
  def parse[T](parser: String => Try[T]): Extractor[T] = (node: Node) => parser(node.text)

  /**
   * Method to extract a Try[T] from the implicitly defined extractor operating on the given node.
   *
   * @param node the node on which the extractor will work.
   * @tparam T the underlying result type.
   *           Required: implicit evidence of an Extractor[T].
   * @return a Try[T].
   */
  def extract[T: Extractor](node: Node): Try[T] =
    implicitly[Extractor[T]].extract(node)

  /**
   * Method to extract a `Try[T]` from the implicitly defined multi-extractor operating on the given nodes.
   * Usually, `T` is itself an `Iterable` type.
   *
   * NOTE that each implicit instanced of a `MultiExtractor[T]` has its own definition of Range.
   *
   * @param nodeSeq the nodes on which the extractor will work.
   * @tparam T the underlying result type.
   *           Required: implicit evidence of a MultiExtractor[T].
   * @return a Try[T].
   */
  def extractMulti[T: MultiExtractor](nodeSeq: NodeSeq): Try[T] =
    implicitly[MultiExtractor[T]].extract(nodeSeq)

  /**
   * Method to extract all possible Try[T] from the implicitly defined multi-extractor operating on the given nodes.
   * Usually, T is itself an Iterable type.
   *
   * The difference between this method and extractMulti is that a Node is passed in, rather than a NodeSeq.
   *
   * @param node the node on which the extractor will work--it will extract from all the node's children.
   * @tparam T the underlying result type.
   *           Required: implicit evidence of a MultiExtractor[T].
   * @return a Try[T].
   */
  def extractAll[T: MultiExtractor](node: Node): Try[T] = extractMulti(node / "_")

  /**
   * Method to extract a singleton from a NodeSeq.
   *
   * @param nodeSeq a sequence of Nodes.
   * @tparam P the underlying type of the result.
   *           Required: implicit evidence of an Extractor[P].
   * @return a Try[P].
   */
  def extractSingleton[P: Extractor](nodeSeq: NodeSeq): Try[P] =
    extractSequence[P](nodeSeq) match {
      case Success(Nil) =>
        Failure(XmlException(s"extractSingleton: empty"))
      case Success(p :: Nil) => Success(p)
      case Success(ps) => Failure(XmlException(s"extractSingleton: ambiguous values: $ps"))
      case Failure(x) => Failure(x)
    }

  /**
   * Method which tries to extract a sequence of objects from a NodeSeq.
   * The difference between this method and extractMulti is in the declaration of the parametric type and its
   * implicit evidence.
   * The difference between this method and extractSingleton is in the result type.
   *
   * @param nodeSeq a NodeSeq.
   * @tparam P the (Extractor) type to which each individual Node should be converted.
   *           Required: implicit evidence of type Extractor[P].
   * @return a Try of Seq[P].
   */
  def extractSequence[P: Extractor](nodeSeq: NodeSeq): Try[Seq[P]] =
    sequence(for (node <- nodeSeq) yield Extractor.extract[P](node))

  /**
   * Extracts a key-value pair from a given XML/HTML node based on the specified key and type.
   *
   * @param k The key used to extract the value from the node.
   * @tparam T The type of the value to be extracted, constrained by the provided Extractor context.
   * @return A function that takes a Node and returns a tuple containing the key and a Try wrapper with either the extracted value or an exception if the extraction fails.
   */
  private def extractorKeyValuePair[T: Extractor](k: String): Node => (String, Try[T]) =
    node => doExtractField[T](k, node)

  /**
   * Extracts a value of type `T` from a `Node` by a given key and applies a specified partial function to the extracted key-value pair.
   *
   * @param k The key to be used for extracting the value from the node.
   * @param f A partial function that operates on a tuple containing the key and the result of the extraction attempt.
   * @tparam T The type of the value to be extracted, for which an implicit `Extractor` must be available.
   * @return A function that takes a `Node` and returns a `Try[T]` representing the result of the extraction and processing.
   */
  private def extractorByKey[T: Extractor](k: String)(f: PartialFunction[(String, Try[T]), Try[T]]): Node => Try[T] =
    node => f(extractorKeyValuePair[T](k).apply(node))

  /**
   * Method to yield a `Try[P]` for a particular child or attribute of the given node.
   *
   * NOTE: Plural members should use extractChildren and not extractField.
   *
   * NOTE: ideally, this should be private but is used for testing and the private method tester is struggling.
   *
   * @param field the name of a member field:
   *              if the (text) content of the node, then the field should be "$";
   *              if a singleton child, then field is as is;
   *              if an attribute, then field should begin with "_";
   *              if an optional child, then field should begin with "maybe".
   * @tparam P the type to which Node should be converted.
   *           Required: implicit evidence of type `Extractor[P]`.
   * @return a `Try[P]`.
   */
  def fieldExtractor[P: Extractor](field: String): Extractor[P] = Extractor(extractorByKey[P](field) {
    case _ -> Success(p) => Success(p)
    case m -> Failure(x) => x match {
      case _: NoSuchFieldException => Success(None.asInstanceOf[P])
      case _ =>
        val message = s"fieldExtractor(field=$field) using (${implicitly[Extractor[P]].name}): (field type = $m)"
        Failure(MissingFieldException(message, m, x))
    }
  }) ^^ s"fieldExtractor of $field"

  /**
   * Method to extract child elements from a node.
   *
   * CONSIDER rewriting this method: it has ended up being a huge a hack and needs work!!
   * NOTE: There are four different ways to get a successful result:
   * (1) node / member yields a non-empty result which is passed into extractMulti;
   * (2) TagProperties.mustMatch(member) in which case the result will be empty;
   * (3) extractAll(node) yields a successful non-empty result, which is returned;
   * (4) extractAll(node) yields a successful empty result, in which case ChildNames.translate(member) is used to match children.
   *
   * @param member the name of the element(s) to extract, according to the construct function (typically, this means the name of the member in a case class).
   * @param node   the node from which we want to extract.
   * @tparam P the underlying type of the result.
   *           Required: implicit evidence of type MultiExtractor[P].
   * @return a Try[P].
   */
  def extractChildren[P: MultiExtractor](member: String)(node: Node): Try[P] =
    member match {
      case Plural(tag) =>
        logger.debug(s"extractChildren with tag=$tag (where member=$member) for ${renderNode(node)}")
        val explicitChildren: NodeSeq = node / tag
        if (explicitChildren.nonEmpty || TagProperties.mustMatch(tag)) // TODO unite Plural and TagProperties
          extractMulti(explicitChildren) // requires implicit MultiExtractor[P]
        else Failure(XmlException(s"extractChildren: no children matched any of $tag (singular of $member) in ${renderNode(node)}"))
      case _ =>
        val explicitChildren: NodeSeq = node / member
        if (explicitChildren.nonEmpty || TagProperties.mustMatch(member))
          extractMulti(explicitChildren) // requires implicit MultiExtractor[P]
        else extractAll(node) match {
          case Success(Nil) =>
            extractChildless(node, member)
          case Success(x) =>
            logger.debug(s"extractChildren extracted $x using extractAll")
            Success(x)
          case Failure(x) =>
            Failure(x)
        }
    }

  /**
   * Extracts childless nodes of a given type from the specified parent XML `node`.
   * Uses a translation of the member name to identify the target child elements.
   *
   * @param node   the parent XML node from which childless nodes are to be extracted
   * @param member the name of the member whose corresponding child elements are targeted
   * @tparam P the type of elements to extract, enabled by the context-bound `MultiExtractor`
   */
  private def extractChildless[P: MultiExtractor](node: Node, member: String): Try[P] = {
    // CONSIDER use Flog logging
    val ts = ChildNames.translate(member)
    logger.debug(s"extractChildren(${name[MultiExtractor[P]]})($member)(${renderNode(node)}): get $ts")
    if (ts.isEmpty) logger.warn(s"extractChildren: logic error: no suitable tags found for children of member $member in ${renderNode(node)}")
    val nodeSeq: Seq[Node] = for (t <- ts; w <- node / t) yield w
    if (nodeSeq.nonEmpty) {
      logger.debug(s"extractChildren extracting ${nodeSeq.size} nodes for ($member)")
      extractMulti(nodeSeq)
    }
    else {
      logger.warn(s"extractChildless: no children matched any of $ts in ${renderNode(node)}")
      Try(Nil.asInstanceOf[P])
    }
  }

  /**
   * Method to extract a Seq or Try[P] from a NodeSeq, by filtering on the given label.
   *
   * @param nodeSeq the nodes whence to extract.
   * @param label   the label to match.
   * @tparam P the underlying type of the result.
   *           Required: implicit evidence of type Extractor[P].
   * @return a Seq of Try[P].
   */
  def extractElementsByLabel[P: Extractor](nodeSeq: NodeSeq, label: String): Seq[Try[P]] =
    for (node <- lensFilter[Node, String](_.label)(label)(nodeSeq)) yield extract[P](node)

  /**
   * Method to create an Extractor[T] which always fails.
   *
   * @tparam T the underlying type of the result.
   * @return a failing Extractor[T].
   */
  def none[T]: Extractor[T] = Extractor(Failure(new NoSuchElementException))

  /**
   * Method to determine, from the name of a property, whether it's an attribute or an element.
   *
   * @param name the member (property) name.
   * @return Some(true) if it's an optional attribute; Some(false) if it's a required attribute; else None.
   */
  def inferAttributeType(name: String): Option[Boolean] = name match {
    case optionalAttribute(_) => Some(true)
    case attribute(_) => Some(false)
    case _ => None
  }

  /**
   * Extracts a field value from a given XML node based on the specified field name.
   *
   * CONSIDER improving the model on which this extraction is based. It's messy!
   *
   * @param field The name of the field to extract. This may represent different types
   *              of elements or attributes, such as node content, attributes, optional attributes,
   *              plurals, singletons, etc.
   * @param node  The XML node from which the field value is to be extracted.
   * @tparam P The type of the field value, constrained by the `Extractor` type class.
   * @return A tuple containing the field description as a `String` and a `Try[P]` representing
   *         the success or failure of the extraction process. In case of failure, the `Try` includes
   *         information about the cause of failure.
   */
  private def doExtractField[P: Extractor](field: String, node: Node): (String, Try[P]) =
    field match {
      // NOTE special name for the (text) content of a node.
      case "$" =>
        "$" -> extractText[P](node)
      // NOTE attributes must match names where the case class member name starts with "_"
      case attribute("xmlns") =>
        "attribute xmlns" -> Failure(XmlException("it isn't documented but xmlns is a reserved attribute name"))
      case optionalAttribute(x) =>
        s"optional attribute: $x" -> extractAttribute[P](node, x, optional = true)
      case attribute(x) =>
        s"attribute: $x" -> extractAttribute[P](node, x)
      // NOTE child nodes are extracted using extractChildren, not here, but if the plural-sounding name is present in node, then we are OK
      case Plural(x) if (node \ field).isEmpty =>
        s"plural:" -> Failure(XmlException(s"extractField: incorrect usage for plural field: $x. Use extractChildren instead."))
      // NOTE optional members such that the name begins with "maybe"
      case optional(x) =>
        s"optional: $x" -> extractOptional[P](node / x) // CONSIDER using \\ like singleton below
      // NOTE this is the default case which is used for a singleton entity (plural entities would be extracted using extractChildren).
      // TODO Issue #21 why would we be looking for a singleton LinearRing in a node which is an extrude node?
      case x =>
        s"singleton: $x" -> (extractSingleton[P](node / x) orElse extractSingleton[P](node \\ x))
    }

  /**
   * Extracts an attribute value from the given XML node based on its name.
   * The extraction utilizes an implicit `Extractor` for the specified type `P`.
   * Returns a `Failure` if the attribute is not found and `optional` is false,
   * or if the attribute cannot be uniquely determined.
   *
   * @param node     the XML node from which the attribute value is retrieved (a `Node`).
   * @param x        the name of the attribute to be extracted.
   * @param optional a flag indicating if the attribute is optional. Defaults to false.
   * @tparam P the type of the result, constrained by the implicit `Extractor` type class.
   * @return a `Try[P]` containing the extracted value, or a failure if extraction fails.
   */
  private def extractAttribute[P: Extractor](node: Node, x: String, optional: Boolean = false): Try[P] =
    (for (ns <- node.attribute(x)) yield for (n <- ns) yield Extractor.extract[P](n)) match {
      case Some(py :: Nil) => py
      case _ if optional => Failure(new NoSuchFieldException)
      case _ => Failure(XmlException(s"failure to retrieve unique attribute $x from node ${renderNode(node)}"))
    }

  /**
   * Regular expression to match an attribute name, viz. _...
   */
  val attribute: Regex = """_(\w+)""".r

  /**
   * Regular expression to match an optional attribute name, viz. &#95;&#95;.....
   * With an optional attribute, it will have a default value that does not need to be overridden.
   */
  val optionalAttribute: Regex = """__(\w+)""".r

  /**
   * Regular expression to match an optional name, viz. maybe....
   *
   * NOTE that the initial character of the result will have been set to lower case.
   */
  val optional: Regex = new LowerCaseInitialRegex("""maybe(\w+)""")

  /**
   * Extracts text content from the provided Node and attempts to parse it into a value of type `P`.
   *
   * CONSIDER what's the point of this method? Couldn't we just inline it as `extract`?
   *
   * @param node The `Node` from which text content is to be extracted.
   * @tparam P The expected type of the extracted content, which must have an associated `Extractor`.
   * @return A `Try` containing the extracted value of type `P` if extraction is successful,
   *         or a `Failure` if it is not.
   */
  private def extractText[P: Extractor](node: Node): Try[P] = extract[P](node)

  /**
   * Unit extractor.
   */
  implicit val unitExtractor: Extractor[Unit] = Extractor(Success(())) ^^ "unitExtractor"

  /**
   * CharSequence extractor.
   */
  implicit object charSequenceExtractor extends Extractor[CharSequence] {

    /**
     * Extracts a `CharSequence` from an XML `Node`.
     *
     * The method attempts to decode the content of the provided `Node`.
     * If the `Node` is of type `Text` or `CDATA`, the content is extracted directly.
     * Otherwise, it attempts to retrieve the single child node's text content.
     * If the node cannot be decoded into a `CharSequence`, a failure is returned.
     *
     * @param node the XML `Node` to extract the `CharSequence` from.
     * @return a `Try` containing a `CharSequence` if successfully extracted, or a `Failure` with an `XmlException` if decoding fails.
     */
    def extract(node: Node): Try[CharSequence] = node match {
      case CDATA(x) => Success(x)
      case x: xml.Text => Success(x.data)
      case _ => node.child.toSeq match {
        case Seq(x) => Success(x.text)
        case x => Failure(XmlException(s"charSequenceExtractor: cannot decode text node: $node: $x"))
      }
    }

    override def toString: String = "charSequenceExtractor"
  }
  /**
   * Int extractor.
   */
  implicit val intExtractor: Extractor[Int] = (charSequenceExtractor flatMap {
    w => NodeParser.tryParse(NodeParser.parseInt, w)
  }) ^^ "intExtractor"

  /**
   * Boolean extractor.
   * TODO use NodeParser.tryParse
   */
  implicit val booleanExtractor: Extractor[Boolean] = (charSequenceExtractor flatMap {
    case "1" | "true" | "T" | "Y" => Success(true)
    case _: String => Success(false)
    case x =>
      Failure(XmlException(s"cannot convert $x to a Boolean"))
  }) ^^ "booleanExtractor"

  /**
   * Double extractor.
   */
  implicit val doubleExtractor: Extractor[Double] = (charSequenceExtractor flatMap {
    w => NodeParser.tryParse(NodeParser.parseDouble, w)
  }) ^^ "doubleExtractor"

  /**
   * Long extractor.
   * TODO use NodeParser.tryParse
   */
  implicit val longExtractor: Extractor[Long] = (charSequenceExtractor flatMap {
    case w: String => Success(w.toLong)
    case x => Failure(XmlException(s"cannot convert $x to a Long"))
  }) ^^ "longExtractor"

  val logger: Logger = LoggerFactory.getLogger(Extractor.getClass)
}

/**
 * Object NodeParser provides a functionality to parse strings into integers using the JavaTokenParsers.
 * In particular, these parsers do not "see" white space.
 * This object leverages the built-in parser combinators to interpret and transform input data.
 */
object NodeParser extends JavaTokenParsers {
  def tryParse[T](p: Parser[T], cs: java.lang.CharSequence): Try[T] =
    parse(p, cs) match {
      case Success(x, _) => scala.util.Success(x)
      case Failure(msg, _) => scala.util.Failure(XmlException(s"tryParse `$cs` failure because $msg"))
      case Error(msg, _) => scala.util.Failure(XmlException(s"tryParse `$cs` error because $msg"))
    }

  def allWhiteSpace(w: String): Boolean = {
    val result = !tryParse("""^\S+""".r, w).isSuccess
    result
  }

  /**
   * Parses a numeric string into an integer.
   *
   * This method utilizes the `floatingPointNumber` parser from the `JavaTokenParsers` to parse
   * a numeric string and transforms the result into an `Int`. Only numeric strings that can be
   * successfully converted to integers are valid inputs; otherwise, a parsing failure will occur.
   *
   * @return a `NodeParser[Int]` that processes a numeric string and converts it into an integer.
   */
  def parseInt: Parser[Int] = floatingPointNumber ^^ (_.toInt)

  /**
   * Parses a numeric string into a double-precision floating-point number.
   *
   * This method leverages the `floatingPointNumber` parser from the `JavaTokenParsers` to interpret
   * a numeric string and transform it into a `Double`. Only valid numeric strings that can be
   * converted to doubles will succeed; otherwise, a parsing failure will occur.
   *
   * @return a `NodeParser[Double]` that processes a numeric string and converts it into a double.
   */
  def parseDouble: Parser[Double] = floatingPointNumber ^^ (_.toDouble)
}

/**
 * Trait to define the behavior of an iterable type which can be constructed from an XML NodeSeq.
 *
 * @tparam T the (iterable) type to be constructed.
 */
trait MultiExtractor[T] extends NamedFunction[MultiExtractor[T]] {
  /**
   * Method to convert a NodeSeq into a Try[T], usually an iterable type.
   * This method will typically be used on the result of: <code>node \ tag</code> where tag is a particular tag String.
   *
   * @param nodeSeq a NodeSeq.
   * @return a Try[T].
   */
  def extract(nodeSeq: NodeSeq): Try[T]
}

/**
 * Companion object to MultiExtractor.
 */
object MultiExtractor {

  /**
   * Method to create an MultiExtractor[T] from a NodeSeq => Try[T] function.
   * Note that this isn't strictly necessary because of the SAM conversion mechanism which turns a Node => Try[T] function into an Extractor[T].
   *
   * TESTME
   *
   * @param f a NodeSeq => Try[T] function.
   * @tparam T the underlying type of the resulting MultiExtractor.
   * @return a MultiExtractor[T].
   */
  def apply[T](f: NodeSeq => Try[T]): MultiExtractor[T] = (nodeSeq: NodeSeq) => f(nodeSeq)

  /**
   * Method to create a lazy MultiExtractor[T] from an explicit MultiExtractor[T] which is call-by-name.
   * The purpose of this method is to break the infinite recursion caused when implicit values are defined
   * recursively.
   * See the Play JSON library method in JsPath called lazyRead.
   *
   * @param tm a MultiExtractor[T].
   * @tparam T the underlying type of the MultiExtractor required.
   * @return a MultiExtractor[T].
   */
  def createLazy[T](tm: => MultiExtractor[T]): MultiExtractor[T] = MultiExtractor { (nodes: NodeSeq) => tm.extract(nodes) } ^^ "lazy extractor for $tm"
}

/**
 * MultiExtractorBase class to deal with minimum and maximum numbers of elements.
 * This is not used for situations where different subtypes are grouped together (for example, Folder can contain any number of "Features,"
 * which can be of type Container, Document, Folder, or Placemark.)
 *
 * @tparam P element type of the MultiExtractor to be returned.
 *           requires implicit evidence of Extractor[P].
 */
case class MultiExtractorBase[P: Extractor](range: Range) extends MultiExtractor[Seq[P]] {
  /**
   * Extracts a sequence of elements of type `P` from the provided `NodeSeq`, ensuring that the number of extracted elements
   * falls within a specified range. If the extraction succeeds but the resulting sequence does not meet the range requirements,
   * a failure is returned. Non-fatal issues during the extraction are logged and ignored.
   *
   * @param nodeSeq the sequence of XML nodes from which elements of type `P` are to be extracted.
   * @return a `Try` containing a sequence of extracted elements of type `P` if successful and the number of elements is within the specified range;
   *         otherwise, a failure with the corresponding exception.
   */
  def extract(nodeSeq: NodeSeq): Try[Seq[P]] =
    sequenceForgiving(logWarning)(nodeSeq map Extractor.extract[P]) match {
      case x@Success(ps) if range.contains(ps.size) => x
      case Success(ps) =>
        Failure(XmlException(
        s"""MultiExtractorBase.extract: the number (${ps.size}) of elements extracted is not in the required range: $range
           |Consider changing the Range in the appropriate call to multiExtractorBase.""".stripMargin))
      case x@Failure(_) => x
    }

  /**
   * Logs warnings for specific exceptions encountered during the extraction process.
   *
   * This method handles different types of `Throwable` instances based on certain conditions:
   * - If the exception is a `MissingFieldException` for a "singleton" field and the range's start is less than or equal to zero,
   * it considers the situation acceptable and does not log anything.
   * - If the exception is an `XmlException`, it logs the associated message and localized message using the logger.
   *
   * @param x the `Throwable` to be examined and potentially logged as a warning
   * @return Unit, the method does not produce any value
   */
  private def logWarning(x: Throwable): Unit = x match {
    case MissingFieldException(_, "singleton", _) if range.start <= 0 => // NOTE: OK -- no need to log anything.
    case XmlException(message, x) => logger.warn("MultiExtractorBase: $message" + x.getLocalizedMessage)
  }
}

/**
 * Companion object for the MultiExtractorBase class. This object provides predefined ranges
 * that can be used when working with extractors or other numerical validations.
 */
object MultiExtractorBase {
  /**
   * All integers greater than zero (the "counting numbers" or Z+).
   */
  val Positive: Range.Inclusive = 1 to Int.MaxValue

  /**
   * All non-negative integers (Z0+).
   */
  val NonNegative: Range.Inclusive = 0 to Int.MaxValue

  /**
   * Exactly One.
   */
  val ExactlyOne: Range.Inclusive = 1 to 1

  /**
   * Either Zero or One.
   */
  val AtMostOne: Range.Inclusive = 0 to 1
}

/**
 * Trait which extends a function of type String => Extractor[T].
 * When the apply method is invoked with a particular label, an appropriate Extractor[T] is returned.
 *
 * CONSIDER renaming this because it isn't an extractor, but a function which creates an extractor from a String.
 *
 * @tparam T the underlying type of the result of invoking apply. T may be an Iterable type.
 */
trait TagToExtractorFunc[T] extends (String => Extractor[T]) {

  /**
   * Method to yield an `Extractor[T]`, given `label`.
   *
   * @param label the label of a node or sequence of nodes we wish to extract (a `String`).
   * @return an `Extractor[T]`.
   */
  def apply(label: String): Extractor[T]
}

/**
 * Trait which extends TagToExtractorFunc for an underlying sequence type.
 *
 * @tparam T the underlying type of the resulting sequence when invoking apply.
 */
trait TagToSequenceExtractorFunc[T] extends TagToExtractorFunc[Seq[T]] {
  /**
   * A sequence of tags associated with the extractor function.
   * This is used to describe or qualify the type of sequences that the extractor handles.
   */
  val tags: Seq[String]

  /**
   * A string that represents a pseudo tag or identifier.
   * It serves as a default or auxiliary string used in the context of sequence extraction
   * within the TagToSequenceExtractorFunc trait.
   */
  val pseudo: String

  /**
   * Determines if the provided string matches the pseudo tag or identifier.
   *
   * @param w the string to be checked against the pseudo tag.
   * @return true if the provided string matches the pseudo tag, otherwise false.
   */
  def valid(w: String): Boolean = w == pseudo

  /**
   * A MultiExtractor instance used for extracting a sequence of type `T` from an XML `NodeSeq`.
   * This value represents a reusable mechanism to perform the extraction process
   * based on specific tags or identifiers within the XML structure.
   */
  val tsm: MultiExtractor[Seq[T]]
}

/**
 * Class which extends an `Extractor` of `Seq[T]`.
 *
 * NOTE: used by subclassExtractor1 method (itself unused).
 *
 * @param labels a set of labels (tags)
 * @param tsm    an (implicit) `MultiExtractor` of `Seq[T]`.
 * @tparam T the type to be constructed.
 */
class SubclassExtractor[T](val labels: Seq[String])(implicit tsm: MultiExtractor[Seq[T]]) extends Extractor[Seq[T]] {
  /**
   * TESTME in particular regarding the use of sequenceForgiving
   *
   * @param node a Node.
   * @return a Try[T].
   */
  def extract(node: Node): Try[Seq[T]] = {
    val f: Throwable => Unit = x => Extractor.logger.warn(x.getLocalizedMessage)
    sequenceForgiving(f)(for (label <- labels) yield tsm.extract(node \ label)) map (_.flatten)
  }
}

/**
 * Object to manage translation of a name to a Seq of names.
 *
 * CONSIDER removing this altogether because there is another mechanism (Feature.multiExtractor) which does something similar.
 *
 */
object ChildNames {
  // CONSIDER make this immutable.
  val map: mutable.HashMap[String, Seq[String]] = new mutable.HashMap()

  /**
   * Adds a translation mapping a key to a sequence of values.
   *
   * @param key   the key for the translation mapping
   * @param value the sequence of strings that corresponds to the key
   * @return Unit, as the method modifies the mutable map in place
   */
  def addTranslation(key: String, value: Seq[String]): Unit = map += key -> value

  /**
   * Translates a given member string to a sequence of strings based on predefined mappings
   * or certain extraction rules. If no mapping or rule applies, the method returns the input
   * string wrapped in a sequence.
   *
   * @param member the input string to be translated
   * @return a sequence of strings corresponding to the translation of the input member
   */
  def translate(member: String): Seq[String] =
    map.getOrElse(member,
      member match {
        case Plural(x) => Seq(x)
        case _ => map.getOrElse(member, Seq(member))
      })
}

/**
 * The `Plural` object is a parser utility extending `JavaTokenParsers` to analyze a given string
 * and determine its plural or singular nature based on specific patterns.
 *
 * It provides the following primary functionality:
 *
 * - The `unapply` method evaluates if a string represents a plural form. It attempts to parse the input
 * as both singular (that ends with `s`) and plural in reverse.
 * - Definitions of multiple helper `Parser` methods to define the behavior for detecting specific plural
 * or singular cases:
 *   - `singularEndsInS`: Identifies certain patterns of singular words that end in `s`.
 *   - `plural`: Consists of combined patterns (`plural1`, `plural2`, `endsInS`, or fails a match).
 *   - `plural1`, `plural2`: Encodes specific reverse transformations for certain plural endings.
 *   - `endsInS`: Matches plural strings ending in `s` followed by a root word.
 *   - `root`: Matches a generic alphanumeric word boundary.
 *
 * The parsing logic uses compositional and pattern-matching techniques to identify and reverse strings as needed.
 */
object Plural extends JavaTokenParsers {
  /**
   * Attempts to extract the root of a plural word from a given string.
   *
   * The method first checks if the string matches specific singular forms that end in "s".
   * If it does, it returns `None`.
   * If not, it attempts to parse the reversed string as plural based on defined patterns.
   * If successful, it returns the root of the plural word.
   *
   * @param s the input string to be evaluated, which can be either singular or plural.
   * @return an `Option` containing the root of the plural word if the input is parsed as plural;
   *         otherwise, returns `None`.
   */
  def unapply(s: String): Option[String] = parseAll(singularEndsInS, s) match {
    case Success(_, _) => None
    case _ =>
      parseAll(plural, s.reverse) match {
        case Success(x, _) => Some(x.reverse)
        case _ => None
      }
  }

  /**
   * Attempts to parse a string as a plural form based on predefined patterns.
   *
   * The method combines multiple sub-parsers, including `plural1`, `plural2`, and `endsInS`,
   * to identify different plural forms.
   * If no match is found, it returns a failure indicating that the string is not plural.
   * The resulting string is reversed as part of the parsing process.
   *
   * @return a `Parser[String]` that parses plural forms and returns the reversed representation
   *         of the input string if successful, or a failure message for invalid inputs.
   */
  private def plural: Parser[String] = plural1 | plural2 | endsInS | failure("not plural") ^^ { (w: String) => w.reverse }

  /**
   * Parses a string that begins with the character 's' and continues with a valid root.
   * This method leverages the `~>` operator to combine the fixed prefix "s"
   * with a subsequent `root` parser.
   *
   * @return a `Parser[String]` that successfully parses strings starting with "s" followed by a root.
   */
  def endsInS: Parser[String] = "s" ~> root

  /**
   * Parses a string that begins with the sequence "sei" and continues with a valid root.
   * This method combines the fixed prefix "sei" with the subsequent `root` parser
   * and applies a transformation to prepend the character 'y' to the parsed root.
   *
   * @return a `Parser[String]` that matches strings starting with "sei", followed by a valid root,
   *         and produces a transformed string with 'y' prepended to the root.
   */
  private def plural1: Parser[String] = "sei" ~> root ^^ { (w: String) => s"y$w" }

  /**
   * Parses a (reversed) string that begins with the sequence "ice" preceded by a valid root.
   * This method uses the `~>` operator to match the fixed suffix "ice" and a `root` parser.
   * Upon successful parsing, it transforms the parsed root by appending "ouse" to it.
   *
   * @return a `Parser[String]` that matches strings starting with "eci" followed by a valid root,
   *         and returns the transformed string with "esuo" prepended to the root.
   */
  private def plural2: Parser[String] = "eci" ~> root ^^ { (w: String) => s"esuo$w" }

  /**
   * Matches specific singular terms that end in the character "s".
   * TODO coordinate this with TagProperties
   *
   * This method defines a parser that matches predefined strings commonly
   * recognized as singular but ending in "s". These include specific terms
   * such as "innerBoundaryIs", "coordinates", "features", "StyleSelectors", and "Styles".
   *
   * @return a `Parser[String]` that matches one of the predefined singular terms ending in "s".
   */
  private def singularEndsInS: Parser[String] = "innerBoundaryIs" | "outerBoundaryIs" | "coordinates" | "features" | "StyleSelectors" | "Styles"

  /**
   * Parses a valid root string composed of word characters (letters, digits, or underscores).
   * This parser uses a regular expression to match sequences of one or more word characters.
   *
   * @return a `Parser[String]` that matches and captures a root string consisting of word characters.
   */
  private def root: Parser[String] = """\w+""".r
}