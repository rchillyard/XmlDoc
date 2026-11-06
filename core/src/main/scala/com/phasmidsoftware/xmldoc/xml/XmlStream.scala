package com.phasmidsoftware.xmldoc.xml

import java.io.InputStream
import javax.xml.stream.XMLStreamConstants.{CDATA, CHARACTERS, COMMENT, END_ELEMENT, SPACE, START_ELEMENT}
import javax.xml.stream.{XMLInputFactory, XMLStreamReader}
import scala.annotation.tailrec
import scala.util.{Try, Using}
import scala.xml.*

/**
 * Streaming extraction: reading a (large) XML document with a pull parser (StAX), and yielding each element which
 * matches a selector, as a `scala.xml.Elem` or extracted as a `T`, as soon as it is complete.
 * Only the selected elements are built; the rest of the document is skipped, so the document is never in memory whole.
 *
 * A selector is a tag (e.g. `Placemark`, any element with that local name), or a path of tags
 * (e.g. `Folder/Placemark`, a `Placemark` whose parent is a `Folder`); a leading `/` anchors the path at the root
 * element (e.g. `/kml/Document/Placemark`). Tags are matched by their local names, so namespaces and prefixes are
 * ignored; each selected subtree keeps its prefixes and all the namespace bindings in scope where it appears.
 * A selected element's descendants are not selected again.
 *
 * Each subtree is built as `XML.load` would build it (adjacent text as one `Text`, CDATA sections as `PCData`,
 * comments as `Comment`), so the `Extractor[T]` of a selected element gives the same `T` as whole-document extraction.
 */
object XmlStream {

  /**
   * Method to create a StAX reader of an input stream, namespace-aware, and with external entities and DTDs
   * disabled (so that a document can't make the parser read other files or URLs).
   *
   * @param in an input stream (which the reader doesn't close).
   * @return an `XMLStreamReader`.
   */
  def reader(in: InputStream): XMLStreamReader = {
    val factory = XMLInputFactory.newFactory()
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true)
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false)
    // NOTE: the JDK's parser reports a CDATA section as ordinary characters unless this (JDK-specific) property is set.
    if (factory.isPropertySupported(reportCData)) factory.setProperty(reportCData, true)
    factory.createXMLStreamReader(in)
  }

  private val reportCData = "http://java.sun.com/xml/stream/properties/report-cdata-event"

  /**
   * Method to get the selected elements of a document, lazily, in document order.
   * NOTE: a malformed document makes the iterator throw an `XMLStreamException` when it reaches the error.
   *
   * @param reader   a StAX reader (at the start of the document).
   * @param selector a tag or path of tags (see above).
   * @return an iterator of the selected elements.
   */
  def elements(reader: XMLStreamReader, selector: String): Iterator[Elem] = new Selected(reader, Selector(selector))

  /**
   * Method to extract a `T` from each selected element of a document, lazily, in document order.
   * Each element's extraction succeeds or fails on its own.
   *
   * @param reader   a StAX reader (at the start of the document).
   * @param selector a tag or path of tags (see above).
   * @tparam T the type of each selected element, which has an `Extractor`.
   * @return an iterator of the results of extracting each selected element.
   */
  def extract[T: Extractor](reader: XMLStreamReader, selector: String): Iterator[Try[T]] =
    elements(reader, selector) map (e => Extractor.extract[T](e))

  /**
   * Method to extract a `T` from each selected element of the document read from an input stream, and to apply a
   * function to the results; the stream and the reader are closed afterwards.
   * {{{
   *   XmlStream.extractFrom[Placemark, Int](new FileInputStream("big.kml"), "Placemark")(_.count(_.isSuccess))
   * }}}
   *
   * @param in       the input stream (opened here, and closed when `f` returns).
   * @param selector a tag or path of tags (see above).
   * @param f        a function of the results, which must use them before it returns.
   * @tparam T the type of each selected element, which has an `Extractor`.
   * @tparam A the type of `f`'s result.
   * @return `f`'s result, or a failure (e.g. if the input stream can't be opened, or the document is malformed).
   */
  def extractFrom[T: Extractor, A](in: => InputStream, selector: String)(f: Iterator[Try[T]] => A): Try[A] =
    Using.Manager { use =>
      val r = reader(use(in))
      try f(extract[T](r, selector)) finally r.close()
    }

  /**
   * A selector: a path of local names, the last of which is the selected element's.
   *
   * @param tags     the local names, outermost first.
   * @param anchored true if the first tag is the root element's.
   */
  private case class Selector(tags: List[String], anchored: Boolean) {
    private val reversed = tags.reverse

    /**
     * @param path the local names of an element and its ancestors, innermost first.
     * @return true if the element is selected.
     */
    def matches(path: List[String]): Boolean =
      if (anchored) path == reversed else path.startsWith(reversed)
  }

  private object Selector {
    def apply(w: String): Selector = {
      val tags = w.split('/').toList.filter(_.nonEmpty)
      require(tags.nonEmpty, s"XmlStream: empty selector: '$w'")
      Selector(tags, w.startsWith("/"))
    }
  }

  /**
   * The iterator of the selected elements: it reads ahead to the next one when asked whether there is one.
   */
  private class Selected(reader: XMLStreamReader, selector: Selector) extends Iterator[Elem] {
    // The local names of the open (unselected) elements, and their namespace scopes, innermost first.
    private var path: List[String] = Nil
    private var scopes: List[NamespaceBinding] = List(TopScope)
    private var lookahead: Option[Elem] = None

    def hasNext: Boolean = {
      if (lookahead.isEmpty) lookahead = advance()
      lookahead.isDefined
    }

    def next(): Elem = {
      val result = if (hasNext) lookahead.get else throw new NoSuchElementException("XmlStream: no more selected elements")
      lookahead = None
      result
    }

    @tailrec
    private def advance(): Option[Elem] =
      if (!reader.hasNext) None
      else reader.next() match {
        case START_ELEMENT =>
          val scope = bindings(reader, scopes.head)
          val name = reader.getLocalName
          if (selector.matches(name :: path)) Some(subtree(reader, scope))
          else {
            path = name :: path
            scopes = scope :: scopes
            advance()
          }
        case END_ELEMENT =>
          path = path.drop(1)
          scopes = scopes.drop(1)
          advance()
        case _ =>
          advance()
      }
  }

  /**
   * Method to build the element at which the reader is (at its start), reading up to and including its end.
   *
   * @param reader a StAX reader, at a START_ELEMENT.
   * @param scope  the element's namespace scope (its own declarations and those of its ancestors).
   * @return the element.
   */
  private def subtree(reader: XMLStreamReader, scope: NamespaceBinding): Elem = {
    val prefix = Option(reader.getPrefix).filter(_.nonEmpty).orNull
    val label = reader.getLocalName
    val attributes = (0 until reader.getAttributeCount).foldRight[MetaData](Null) { (i, next) =>
      val name = reader.getAttributeLocalName(i)
      val value = reader.getAttributeValue(i)
      Option(reader.getAttributePrefix(i)).filter(_.nonEmpty) match {
        case Some(p) => new PrefixedAttribute(p, name, value, next)
        case None => new UnprefixedAttribute(name, value, next)
      }
    }
    val children = content(reader, scope)
    Elem(prefix, label, attributes, scope, children.isEmpty, children*)
  }

  /**
   * Method to read the content of an element, up to and including its end.
   * Adjacent character data become one `Text` (as `XML.load` makes them).
   */
  private def content(reader: XMLStreamReader, scope: NamespaceBinding): List[Node] = {
    @tailrec
    def inner(reversed: List[Node]): List[Node] = reader.next() match {
      case START_ELEMENT => inner(subtree(reader, bindings(reader, scope)) :: reversed)
      case CHARACTERS | SPACE => inner(reversed match {
        case (t: Text) :: rest => Text(t.data + reader.getText) :: rest
        case _ => Text(reader.getText) :: reversed
      })
      case CDATA => inner(PCData(reader.getText) :: reversed)
      case COMMENT => inner(Comment(reader.getText) :: reversed)
      case END_ELEMENT => reversed.reverse
      case _ => inner(reversed)
    }

    inner(Nil)
  }

  /**
   * Method to add the namespace declarations of the element at which the reader is to its parent's scope.
   */
  private def bindings(reader: XMLStreamReader, parent: NamespaceBinding): NamespaceBinding =
    (0 until reader.getNamespaceCount).foldLeft(parent) { (scope, i) =>
      NamespaceBinding(Option(reader.getNamespacePrefix(i)).filter(_.nonEmpty).orNull, reader.getNamespaceURI(i), scope)
    }
}
