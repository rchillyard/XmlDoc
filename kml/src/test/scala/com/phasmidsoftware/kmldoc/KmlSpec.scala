package com.phasmidsoftware.kmldoc

import com.phasmidsoftware.core.Utilities.parseUnparsed
import com.phasmidsoftware.core.{CDATA, Text, TryUsing, XmlException}
import com.phasmidsoftware.render.{FormatXML, Renderer, StateR}
import com.phasmidsoftware.xml.Extractor.{extract, extractAll, extractMulti}
import com.phasmidsoftware.xml.{Extractor, Extractors, RichXml}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

import java.io.FileWriter
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

class KmlSpec extends AnyFlatSpec with should.Matchers {

  KML.init()

  behavior of "renderers"

  it should "render Open" in {
    val target = Open("1" == "1")
    val wy = TryUsing(StateR())(sr => Renderer.render[Open](target, FormatXML(), sr))
    wy.isSuccess shouldBe true
    wy.get shouldBe "<Open>1</Open>"
  }

  it should "render Placemark" in {
    val coordinates1 = Coordinates(Seq(Coordinate("-72", "0", "0"), Coordinate("-71", "1", "1000")))
    val point: Point = Point(Seq(coordinates1))(GeometryData(None, None)(KmlData.nemo))
    val featureData: FeatureData = FeatureData(Text("Hello"), None, None, None, None, Nil, Nil)(KmlData.nemo)
    val placemark = Placemark(Seq(point))(featureData)
    val wy = TryUsing(StateR())(sr => Renderer.render[Placemark](placemark, FormatXML(), sr))
    wy.isSuccess shouldBe true
    wy.get shouldBe s"<Placemark>\n  <name>Hello</name>\n  <Point>\n    <coordinates>\n      -72,0,0\n      -71,1,1000\n    </coordinates>\n  </Point>\n</Placemark>"
  }

  it should "render Folder" in {
    val coordinates1 = Coordinates(Seq(Coordinate("-72", "0", "0")))
    val point: Point = Point(Seq(coordinates1))(GeometryData(None, None)(KmlData.nemo))
    val featureData1: FeatureData = FeatureData(Text("Hello"), None, None, None, None, Nil, Nil)(KmlData.nemo)
    val featureData2: FeatureData = FeatureData(Text("Goodbye"), None, None, None, None, Nil, Nil)(KmlData.nemo)
    val placemark = Placemark(Seq(point))(featureData1)
    val containerData: ContainerData = ContainerData(featureData2)
    val folder = Folder(Seq(placemark))(containerData)
    val wy = TryUsing(StateR())(sr => Renderer.render[Folder](folder, FormatXML(), sr))
    // TODO remove the extra padding in front of the <name> tags
    wy shouldBe Success("<Folder>\n  <name>Goodbye</name>\n  <Placemark>\n    <name>Hello</name>\n    <Point>\n      <coordinates>\n        -72,0,0\n      </coordinates>\n    </Point>\n  </Placemark>\n</Folder>")
  }

  behavior of "KmlObject"

  it should "parse Scale with id 0" in {
    val xml: Elem = <scale id="Hello">2.0</scale>
    val triedScale = extract[Scale](xml)
    triedScale.isSuccess shouldBe true
    val scale = triedScale.get
    scale.$ shouldBe 2.0
    val wy = TryUsing(StateR())(sr => Renderer.render[Scale](scale, FormatXML(), sr))
    wy.isSuccess shouldBe true
    // CONSIDER should we force this result to have tag of "scale" instead of "Scale"?
    wy.get shouldBe "<Scale id=\"Hello\">2</Scale>"
  }

  it should "parse Scale with id 1" in {
    val xml: Elem =
      <scale id="Hello">2.0</scale>
    val triedScale = extract[Scale](xml)
    triedScale.isSuccess shouldBe true
    val scale = triedScale.get
    scale.$ shouldBe 2.0
    val wy = TryUsing(StateR())(sr => Renderer.render[Scale](scale, FormatXML(), sr))
    wy.isSuccess shouldBe true
    wy.get.contains(""""Hello">2""") shouldBe true
  }

  // Issue #43 Resolved by adding Some("scale")
  it should "parse Scale with id 2" in {
    val xml: Elem =
      <scale id="Hello">2.0</scale>
    val triedScale = extract[Scale](xml)
    triedScale.isSuccess shouldBe true
    val scale = triedScale.get
    scale.$ shouldBe 2.0
    // NOTE that because, in this test, scale is at the top-level,
    //  we have to help out by specifying the correct tag name in the initial StateR.
    val wy = TryUsing(StateR(Some("scale")))(sr => Renderer.render[Scale](scale, FormatXML(), sr))
    wy.isSuccess shouldBe true
    wy.get shouldBe "<scale id=\"Hello\">2</scale>"
  }

  it should "parse Scale without id" in {
    val xml: Elem = <scale>2.0</scale>
    val triedScale = Extractor.extract[Scale](xml)
    triedScale.isSuccess shouldBe true
    triedScale.get.$ shouldBe 2.0
  }

  behavior of "Style"

  it should "parse BalloonStyle" in {
    val xml: Elem = <xml>
      <Style id="noDrivingDirections">
        <BalloonStyle>
          <text>
            <![CDATA[
          <b>$[name]</b>
          <br /><br />
          $[description]
        ]]>
          </text>
        </BalloonStyle>
      </Style>
    </xml>

    extractAll[Seq[StyleSelector]](xml) match {
      case Success(ss) =>
        ss.size shouldBe 1
        val style: StyleSelector = ss.head
        println(style)
        style match {
          case Style(styles) =>
            styles.size shouldBe 1
            styles.head match {
              case b@BalloonStyle(_, _, _, _) =>
                val wy = TryUsing(StateR())(sr => Renderer.render(b, FormatXML(), sr))
                wy.isSuccess shouldBe true
                val expectedBalloonStyle = """<BalloonStyle>
                    |  <text>
                    |<![CDATA[
                    |          <b>$[name]</b>
                    |          <br /><br />
                    |          $[description]
                    |        ]]>
                    |</text>
                    |</BalloonStyle>""".stripMargin
                  wy.get shouldBe expectedBalloonStyle
            }
          case _ => fail(s"wrong sort of StyleSelector: $style")
        }
      case Failure(x) => fail(x)
    }
  }

  behavior of "LineStyle"

  it should "extract LineStyle" in {
    val xml: Elem = <LineStyle>
      <color>ff0000ff</color>
      <width>5</width>
    </LineStyle>
    val triedLineStyle: Try[LineStyle] = extract[LineStyle](xml)
    println(s"triedLineStyle: $triedLineStyle")
    triedLineStyle.isSuccess shouldBe true
    triedLineStyle.get shouldBe LineStyle(Some(Width(5.0)))(ColorStyleData(Some(Color(Hex4("ff0000ff"))), None)(SubStyleData(KmlData(None))))
  }

  it should "extract Color" in {
    val xml: Elem = <color>ff0000ff</color>
    val triedColor: Try[Color] = extract[Color](xml)
    println(s"triedLineStyle: $triedColor")
    triedColor.isSuccess shouldBe true
    triedColor.get shouldBe Color(Hex4("ff0000ff"))
  }

  behavior of "Coordinate"

  it should "parse Coordinate pair" in {
    Coordinate("-71.06992,42.49424,0") shouldBe Coordinate("-71.06992", "42.49424", "0")
  }

  behavior of "Coordinates"

  it should "parse Coordinates" in {
    val coordinates = Coordinates.parse(
      """-71.06992,42.49424,0
        |-71.07018,42.49512,0
        |""".stripMargin)
    coordinates.coordinates.size shouldBe 2
  }

  it should "extract Coordinates" in {
    val xml: Elem = <xml>
      <coordinates>
        -71.06992,42.49424,0
        -71.07018,42.49512,0
      </coordinates>
    </xml>
    extractAll[Seq[Coordinates]](xml) match {
      case Success(cs) =>
        cs.size shouldBe 1
        val coordinates: Coordinates = cs.head
        coordinates.coordinates.size shouldBe 2
        val wy = TryUsing(StateR())(sr => Renderer.render(cs, FormatXML(), sr))
        wy shouldBe Success("<Coordinates>\n  -71.06992,42.49424,0\n  -71.07018,42.49512,0\n</Coordinates>")
      case Failure(x) => fail(x)
    }
  }

  behavior of "geometrys"

  it should "extract LineString as geometry" in {
    val xml: Elem = <xml>
      <LineString>
        <tessellate>1</tessellate>
        <coordinates>
          -71.06992,42.49424,0
          -71.07018,42.49512,0
          -71.07021,42.49549,0
          -71.07008,42.49648,0
          -71.069849,42.497415,0
          -71.06954,42.49833,0
          -71.069173,42.49933,0
          -71.06879,42.50028,0
          -71.068121,42.501386,0
          -71.067713,42.501964,0
          -71.067327,42.502462,0
          -71.06634,42.503459,0
          -71.065825,42.503933,0
          -71.0653,42.504384,0
          -71.064742,42.504819,0
          -71.064205,42.505207,0
          -71.063637,42.505594,0
          -70.9254345,42.5262817,0
        </coordinates>
      </LineString>
    </xml>
    extractAll[Seq[Geometry]](xml) match {
      case Success(gs) =>
        gs.size shouldBe 1
        val lsHead = gs.head
        lsHead match {
          case LineString(tessellate, cs) =>
            tessellate shouldBe Tessellate(true)
            cs.size shouldBe 1
            cs.head.coordinates.size shouldBe 18
        }
        val wy = TryUsing(StateR())(sr => Renderer.render(gs, FormatXML(), sr))
        wy.isSuccess shouldBe true
        val expected = s"<LineString>\n  <tessellate>1</tessellate>\n  <coordinates>\n    -71.06992,42.49424,0\n    -71.07018,42.49512,0\n    -71.07021,42.49549,0\n    -71.07008,42.49648,0\n    -71.069849,42.497415,0\n    -71.06954,42.49833,0\n    -71.069173,42.49933,0\n    -71.06879,42.50028,0\n    -71.068121,42.501386,0\n    -71.067713,42.501964,0\n    -71.067327,42.502462,0\n    -71.06634,42.503459,0\n    -71.065825,42.503933,0\n    -71.0653,42.504384,0\n    -71.064742,42.504819,0\n    -71.064205,42.505207,0\n    -71.063637,42.505594,0\n    -70.9254345,42.5262817,0\n  </coordinates>\n</LineString>"
        wy.get shouldBe expected
      case Failure(x) => fail(x)
    }
  }
  it should "extract Point as geometry" in {
    val xml: Elem = <xml>
      <Point id="my point">
        <coordinates>
          -71.097293,42.478238,0
        </coordinates>
      </Point>
    </xml>
    extractMulti[Seq[Geometry]](xml / "_") match {
      case Success(gs) =>
        gs.size shouldBe 1
        gs.head match {
          case Point(cs) =>
            cs.size shouldBe 1
            cs.head.coordinates.size shouldBe 1
        }
        val wy = TryUsing(StateR())(sr => Renderer.render(gs, FormatXML(), sr))
        wy.isSuccess shouldBe true
        wy.get shouldBe "<Point id=\"my point\">\n  <coordinates>\n    -71.097293,42.478238,0\n  </coordinates>\n</Point>"
      case Failure(x) => fail(x)
    }
  }
  it should "fail to extract coordinate-less Point as geometry" in {
    val xml: Elem = <xml>
      <Point id="my point">
      </Point>
    </xml>
    extractMulti[Seq[Geometry]](xml / "_") match {
      case Success(_) => fail("should not succeed")
      case Failure(_) =>
    }
  }

  it should "extract Polygon with inner boundary" in {
    val xml = <xml>
      <Polygon>
        <extrude>1</extrude>
        <altitudeMode>relativeToGround</altitudeMode>
        <outerBoundaryIs>
          <LinearRing>
            <coordinates>-77.05788457660967,38.87253259892824,100
              -77.05465973756702,38.87291016281703,100
              -77.05315536854791,38.87053267794386,100
              -77.05552622493516,38.868757801256,100
              -77.05844056290393,38.86996206506943,100
              -77.05788457660967,38.87253259892824,100</coordinates>
          </LinearRing>
        </outerBoundaryIs>
        <innerBoundaryIs>
          <LinearRing>
            <coordinates>-77.05668055019126,38.87154239798456,100
              -77.05542625960818,38.87167890344077,100
              -77.05485125901024,38.87076535397792,100
              -77.05577677433152,38.87008686581446,100
              -77.05691162017543,38.87054446963351,100
              -77.05668055019126,38.87154239798456,100</coordinates>
          </LinearRing>
        </innerBoundaryIs>
      </Polygon>
    </xml>
    extractAll[Seq[Geometry]](xml) match {
      case Success(gs) =>
        gs.size shouldBe 1
        val polygon = gs.head.asInstanceOf[Polygon]
        polygon.geometryData.maybeAltitudeMode shouldBe Some(AltitudeMode(AltitudeModeEnum.relativeToGround))
        val outerBoundary: OuterBoundaryIs = polygon.outerBoundaryIs
        val coordinates: Seq[Coordinates] = outerBoundary.LinearRing.coordinates
        coordinates.size shouldBe 1
        coordinates.head.coordinates.size shouldBe 6
        val innerBoundaries: Seq[InnerBoundaryIs] = polygon.innerBoundaryIs
        innerBoundaries.size shouldBe 1
      case Failure(x) => fail("could not extract Polygon", x)
    }
  }

  it should "extract Polygon without inner boundary" in {
    val xml = <xml>
      <Polygon>
        <extrude>1</extrude>
        <altitudeMode>relativeToGround</altitudeMode>
        <outerBoundaryIs>
          <LinearRing>
            <coordinates>-77.05788457660967,38.87253259892824,100
              -77.05465973756702,38.87291016281703,100
              -77.05315536854791,38.87053267794386,100
              -77.05552622493516,38.868757801256,100
              -77.05844056290393,38.86996206506943,100
              -77.05788457660967,38.87253259892824,100</coordinates>
          </LinearRing>
        </outerBoundaryIs>
      </Polygon>
    </xml>
    extractAll[Seq[Geometry]](xml) match {
      case Success(gs) =>
        gs.size shouldBe 1
        val polygon = gs.head.asInstanceOf[Polygon]
        val outerBoundary: OuterBoundaryIs = polygon.outerBoundaryIs
        val coordinates: Seq[Coordinates] = outerBoundary.LinearRing.coordinates
        coordinates.size shouldBe 1
        coordinates.head.coordinates.size shouldBe 6
        val innerBoundaries: Seq[InnerBoundaryIs] = polygon.innerBoundaryIs
        innerBoundaries.size shouldBe 0
      case Failure(x) => fail("could not extract Polygon", x)
    }
  }

  behavior of "FeatureData"

  it should "extract as String" in {
    val xml: Elem = <xml>
      <description>Hello</description>
    </xml>
    val result: Try[CharSequence] = Extractor.fieldExtractor[CharSequence]("description").extract(xml)
    result shouldBe Success("Hello")
  }
  it should "extract as Text" in {
    val xml: Elem = <xml>
      <description>Hello</description>
    </xml>
    val result: Try[Text] = Extractor.fieldExtractor[Text]("description").extract(xml)
    result shouldBe Success(Text("Hello"))
  }
  it should "extract as Option[Text]" in {
    val xml: Elem = <xml>
      <description>Hello</description>
    </xml>
    val result: Try[Option[Text]] = Extractor.fieldExtractor[Option[Text]]("description").extract(xml)
    result shouldBe Success(Some(Text("Hello")))
  }
  case class Element(maybeDescription: Option[Text])
  it should "extract using maybe Some" in {
    val xml: Elem = <element>
      <description>Hello</description>
    </element>
    implicit val extractorMyElement: Extractor[Element] = new Extractors {}.extractor10(Element.apply)
    val ey: Try[Element] = Extractor.extract[Element](xml)
    ey.isSuccess shouldBe true
    ey.get.maybeDescription shouldBe Some(Text("Hello"))
  }
  it should "extract using maybe None" in {
    val xml: Elem = <element>
    </element>
    implicit val extractorMyElement: Extractor[Element] = new Extractors {}.extractor10(Element.apply)
    val ey: Try[Element] = Extractor.extract[Element](xml)
    ey.isSuccess shouldBe true
    ey.get.maybeDescription shouldBe None
  }

  behavior of "Feature"

  it should "extract LookAt" in {
    val xml = <xml>
      <LookAt>
        <longitude>15.02468937557116</longitude>
        <latitude>37.67395167941667</latitude>
        <altitude>0</altitude>
        <heading>-16.5581842842829</heading>
        <tilt>58.31228652890705</tilt>
        <range>30350.36838438907</range>
      </LookAt>
    </xml>
    extractAll[Seq[AbstractView]](xml) match {
      case x =>
        println(x)
        x.isSuccess shouldBe true
    }
  }

  it should "extract Placemark 1" in {
    val xml: Elem = <xml>
      <Placemark>
        <name>Wakefield Branch of Eastern RR</name>
        <description>RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody.</description>
        <styleUrl>#line-006600-5000</styleUrl>
        <LineString>
          <tessellate>1</tessellate>
          <coordinates>
            -71.06992,42.49424,0
            -71.07018,42.49512,0
            -71.07021,42.49549,0
            -71.07008,42.49648,0
            -71.069849,42.497415,0
            -71.06954,42.49833,0
            -70.9257614,42.5264001,0
            -70.9254345,42.5262817,0
          </coordinates>
        </LineString>
      </Placemark>
    </xml>
    extractAll[Seq[Feature]](xml) match {
      case Success(ps) =>
        ps.size shouldBe 1
        val feature: Feature = ps.head
        feature match {
          case placemark: Placemark =>
            val featureData: FeatureData = placemark.featureData
            placemark.featureData.name shouldBe Text("Wakefield Branch of Eastern RR")
            val geometry: Seq[Geometry] = placemark.Geometry
            geometry.size shouldBe 1
            geometry.head match {
              case LineString(Tessellate(true), coordinates) =>
                coordinates.size shouldBe 1
                val coordinate = coordinates.head
                coordinate.coordinates.size shouldBe 8
              case _ => fail("first geometrys is not a LineString")

            }
            featureData match {
              case FeatureData(Text("Wakefield Branch of Eastern RR"), maybeDescription, _, _, _, _, Nil) =>
                println(s"maybeDescription: $maybeDescription")
              case _ => println(s"$featureData did not match the expected result")
            }
            val wy = TryUsing(StateR())(sr => Renderer.render[Placemark](placemark, FormatXML(), sr))
            wy.isSuccess shouldBe true
            wy.get shouldBe s"<Placemark>\n  <name>Wakefield Branch of Eastern RR</name>\n  <description>RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody.</description>\n  <styleUrl>#line-006600-5000</styleUrl>\n  <LineString>\n    <tessellate>1</tessellate>\n    <coordinates>\n      -71.06992,42.49424,0\n      -71.07018,42.49512,0\n      -71.07021,42.49549,0\n      -71.07008,42.49648,0\n      -71.069849,42.497415,0\n      -71.06954,42.49833,0\n      -70.9257614,42.5264001,0\n      -70.9254345,42.5262817,0\n    </coordinates>\n  </LineString>\n</Placemark>"
        }
      case Failure(x) => fail(x)
    }
  }
  it should "extract Placemark 2" in {
    val xml: Elem = <xml>
      <Placemark>
        <name>
          Wakefield Branch of Eastern RR
        </name>
        <description>RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody.</description>
        <styleUrl>#line-006600-5000</styleUrl>
        <LineString>
          <tessellate>1</tessellate>
          <coordinates>
            -71.06992,42.49424,0
            -71.07018,42.49512,0
            -71.07021,42.49549,0
            -71.07008,42.49648,0
            -71.069849,42.497415,0
            -71.06954,42.49833,0
            -70.9257614,42.5264001,0
            -70.9254345,42.5262817,0
          </coordinates>
        </LineString>
      </Placemark>
    </xml>
    extractAll[Seq[Feature]](xml) match {
      case Success(ps) =>
        ps.size shouldBe 1
        val feature: Feature = ps.head
        feature match {
          case placemark: Placemark =>
            val featureData: FeatureData = placemark.featureData
            placemark.featureData.name shouldBe Text(
              """
                |          Wakefield Branch of Eastern RR
                |        """.stripMargin)
            val geometry: Seq[Geometry] = placemark.Geometry
            geometry.size shouldBe 1
            geometry.head match {
              case LineString(Tessellate(true), coordinates) =>
                coordinates.size shouldBe 1
                val coordinate = coordinates.head
                coordinate.coordinates.size shouldBe 8
              case _ => fail("first geometrys is not a LineString")

            }
            featureData match {
              case FeatureData(Text("Wakefield Branch of Eastern RR"), maybeDescription, _, _, _, _, Nil) =>
                println(s"maybeDescription: $maybeDescription")
              case _ => println(s"$featureData did not match the expected result")
            }
            val wy = TryUsing(StateR())(sr => Renderer.render[Placemark](placemark, FormatXML(), sr))
            wy.isSuccess shouldBe true
            wy.get shouldBe
              s"""<Placemark>
                 |  <name>
                 |          Wakefield Branch of Eastern RR
                 |        </name>
                 |  <description>RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody.</description>
                 |  <styleUrl>#line-006600-5000</styleUrl>
                 |  <LineString>
                 |    <tessellate>1</tessellate>
                 |    <coordinates>
                 |      -71.06992,42.49424,0
                 |      -71.07018,42.49512,0
                 |      -71.07021,42.49549,0
                 |      -71.07008,42.49648,0
                 |      -71.069849,42.497415,0
                 |      -71.06954,42.49833,0
                 |      -70.9257614,42.5264001,0
                 |      -70.9254345,42.5262817,0
                 |    </coordinates>
                 |  </LineString>
                 |</Placemark>""".stripMargin
        }
      case Failure(x) => fail(x)
    }
  }

  behavior of "Container"

  it should "extract Folder" in {
    val xml: Elem = <xml>
      <Folder>
        <name>Untitled layer</name>
        <Placemark>
          <name>Wakefield Branch of Eastern RR</name>
          <description>RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody.</description>
          <styleUrl>#line-006600-5000</styleUrl>
          <LineString>
            <tessellate>1</tessellate>
            <coordinates>
              -71.06992,42.49424,0
              -71.07018,42.49512,0
              -71.07021,42.49549,0
              -71.07008,42.49648,0
              -71.069849,42.497415,0
              -71.06954,42.49833,0
              -70.9257614,42.5264001,0
              -70.9254345,42.5262817,0
            </coordinates>
          </LineString>
        </Placemark>
      </Folder>
    </xml>
    extractAll[Seq[Container]](xml) match {
      case Success(cs) =>
        cs.size shouldBe 1
        val container: Container = cs.head
        container match {
          case f@Folder(features) =>
            println(s"got Folder($features)(${f.containerData})")
            val containerData: ContainerData = f.containerData
            val featureData: FeatureData = containerData.featureData
            val name = featureData.name
            val untitledLayer = Text("Untitled layer")
            name shouldBe untitledLayer
            features.size shouldBe 1
            val feature = features.head
            feature match {
              case placemark: Placemark =>
                placemark.featureData.name shouldBe Text("Wakefield Branch of Eastern RR")
                placemark.featureData.maybeDescription shouldBe Some(Text("RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody."))
                val ls: scala.Seq[Geometry] = placemark.Geometry
                ls.size shouldBe 1
                val geometry: Geometry = ls.head
                val coordinates: scala.Seq[Coordinates] = geometry match {
                  case lineString: LineString => lineString.coordinates
                  case _ => fail("first geometrys is not a LineString")
                }
                coordinates.size shouldBe 1
                val coordinate = coordinates.head
                coordinate.coordinates.size shouldBe 8
                println(implicitly[Renderer[Folder]])
                val wy = TryUsing(StateR())(sr => Renderer.render[Folder](f, FormatXML(), sr))
                wy.isSuccess shouldBe true
                wy.get shouldBe s"<Folder>\n  <name>Untitled layer</name>\n  <Placemark>\n    <name>Wakefield Branch of Eastern RR</name>\n    <description>RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody.</description>\n    <styleUrl>#line-006600-5000</styleUrl>\n    <LineString>\n      <tessellate>1</tessellate>\n      <coordinates>\n        -71.06992,42.49424,0\n        -71.07018,42.49512,0\n        -71.07021,42.49549,0\n        -71.07008,42.49648,0\n        -71.069849,42.497415,0\n        -71.06954,42.49833,0\n        -70.9257614,42.5264001,0\n        -70.9254345,42.5262817,0\n      </coordinates>\n    </LineString>\n  </Placemark>\n</Folder>"
            }
        }
      case Failure(x) => fail(x)
    }
  }

  it should "extract Document" in {
    val xml = <xml>
      <Document>
        <name>MA - Boston NE: Historic New England Railroads</name>
        <description>See description of Historic New England Railroads (MA - Boston NW). Full index: https://www.rubecula.com/RRMaps/</description>
        <Style id="icon-22-nodesc-normal">
          <IconStyle>
            <scale>1.1</scale>
            <Icon>
              <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
            </Icon>
            <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
          </IconStyle>
          <LabelStyle>
            <scale>0</scale>
          </LabelStyle>
          <BalloonStyle>
            <text>
              <![CDATA[<h3>$[name]</h3>]]>
            </text>
          </BalloonStyle>
        </Style>
        <StyleMap id="line-006600-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-006600-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-006600-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Folder>
          <name>Untitled layer</name>
          <Placemark>
            <name>Stoneham Branch</name>
            <description>
              <![CDATA[K405<br>RDK1: 51B]]>
            </description>
            <styleUrl>#line-FF0000-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.126508,42.477003,0
                -71.126368,42.477585,0
                -71.126282,42.477779,0
                -71.126186,42.477949,0
                -71.126046,42.478139,0
                -71.125896,42.478305,0
                -71.125724,42.478471,0
                -71.125542,42.47861,0
                -71.125301,42.478776,0
                -71.125059,42.478902,0
                -71.124802,42.479013,0
                -71.124523,42.479108,0
                -71.124206,42.479175,0
                -71.1239,42.47923,0
                -71.1236,42.47925,0
                -71.1233,42.47925,0
                -71.122988,42.479223,0
                -71.12269,42.47918,0
                -71.121615,42.479005,0
                -71.120526,42.478823,0
                -71.118702,42.478534,0
                -71.118144,42.478447,0
                -71.117683,42.478396,0
                -71.117195,42.478368,0
                -71.116712,42.47838,0
                -71.116331,42.478416,0
                -71.115226,42.478534,0
                -71.114593,42.478602,0
                -71.114298,42.478633,0
                -71.114025,42.478645,0
                -71.113601,42.478645,0
                -71.112898,42.478625,0
                -71.112319,42.478625,0
                -71.111718,42.478641,0
                -71.111353,42.478673,0
                -71.110967,42.478728,0
                -71.110699,42.478784,0
                -71.11042,42.478855,0
                -71.110017,42.47897,0
                -71.109615,42.479108,0
                -71.10932,42.479231,0
                -71.109046,42.479365,0
                -71.108783,42.479512,0
                -71.108558,42.479666,0
                -71.107909,42.480125,0
                -71.105672,42.481691,0
                -71.10512,42.482095,0
                -71.104556,42.48253,0
                -71.104159,42.482803,0
                -71.103961,42.482957,0
                -71.103725,42.483167,0
                -71.10351,42.483397,0
                -71.103296,42.483638,0
                -71.103103,42.483875,0
                -71.102024,42.485106,0
                -71.101826,42.485295,0
                -71.101643,42.485462,0
                -71.101434,42.485604,0
                -71.101198,42.485758,0
                -71.100892,42.485901,0
                -71.100565,42.486023,0
                -71.100297,42.486103,0
                -71.100007,42.48617,0
                -71.099734,42.486221,0
                -71.09944,42.48627,0
                -71.0992403,42.4862899,0
                -71.0990166,42.4862938,0
                -71.0987616,42.4862828,0
                -71.0985082,42.4862587,0
                -71.0981516,42.4861877,0
                -71.0977833,42.4860848,0
                -71.097507,42.485972,0
                -71.097229,42.485849,0
                -71.096896,42.485683,0
                -71.096574,42.485462,0
                -71.096268,42.485197,0
                -71.09592,42.484852,0
                -71.095571,42.484453,0
                -71.095233,42.484053,0
                -71.095056,42.483788,0
                -71.094906,42.483531,0
                -71.09482,42.483353,0
                -71.094734,42.483151,0
                -71.094659,42.482918,0
                -71.094605,42.482704,0
                -71.094589,42.482459,0
                -71.094584,42.48217,0
                -71.0946,42.481949,0
                -71.094659,42.481707,0
                -71.094761,42.48145,0
                -71.094906,42.481205,0
                -71.095292,42.480651,0
                -71.095694,42.480145,0
                -71.09744,42.47812,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Passenger Depot</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.475409,42.590934,0
              </coordinates>
            </Point>
          </Placemark>
        </Folder>
      </Document>
    </xml>
    println("Begin extract Container/Document")
    extractAll[Seq[Container]](xml) match {
      case Success(containers) =>
        println(s"extract Container/Document: container(s): $containers")
        containers.size shouldBe 1
        containers.head match {
          case document@Document(fs) =>
            fs.size shouldBe 1
            document.containerData.featureData.name shouldBe Text("MA - Boston NE: Historic New England Railroads")
            val feature = fs.head
            feature match {
              case placemark: Placemark =>
                placemark.featureData.name shouldBe Text("Stoneham Branch")
                placemark.featureData.maybeDescription shouldBe Text(
                  """
    K405<br>RDK1: 51B
  """)
                val ls: scala.Seq[Geometry] = placemark.Geometry
                ls.size shouldBe 1
                val geometry: Geometry = ls.head
                val coordinates: scala.Seq[Coordinates] = geometry match {
                  case lineString: LineString => lineString.coordinates
                  case _ => fail("first geometrys is not a LineString")
                }
                coordinates.size shouldBe 1
                val coordinate = coordinates.head
                coordinate.coordinates.size shouldBe 94
                val wy = TryUsing(StateR())(sr => Renderer.render[Document](document, FormatXML(), sr))
                wy.isSuccess shouldBe true
                wy.get.startsWith("<Document><name>MA - Boston NE: Historic New England Railroads</name><description>See description of Historic New England Railroads (MA - Boston NW). Full index: https://www.rubecula.com/RRMaps/</description>\n    <Style id=\"icon-22-nodesc-normal\"><IconStyle><scale>1.1</scale><Icon>".stripMargin) shouldBe true
              case _: Folder =>
            }
        }
      case Failure(x) => fail(x)
    }
    println("End extract Container/Document")
  }

  behavior of "Overlay"

  it should "extract GroundOverlay" in {
    val xml = <xml><GroundOverlay>
      <name>Large-scale overlay on terrain</name>
      <visibility>0</visibility>
      <description>Overlay shows Mount Etna erupting on July 13th, 2001.</description>
      <LookAt>
        <longitude>15.02468937557116</longitude>
        <latitude>37.67395167941667</latitude>
        <altitude>0</altitude>
        <heading>-16.5581842842829</heading>
        <tilt>58.31228652890705</tilt>
        <range>30350.36838438907</range>
      </LookAt>
      <Icon>
        <href>https://developers.google.com/kml/documentation/images/etna.jpg</href>
      </Icon>
      <LatLonBox>
        <north>37.91904192681665</north>
        <south>37.46543388598137</south>
        <east>15.35832653742206</east>
        <west>14.60128369746704</west>
        <rotation>-0.1556640799496235</rotation>
      </LatLonBox>
    </GroundOverlay></xml>
    extractAll[Seq[Overlay]](xml) match {
      case Success(os) =>
        os.size shouldBe 1
        val overlay: Overlay = os.head
        overlay match {
          case g@GroundOverlay(maybeAltitude, maybeAltitudeMode, latLonBox) =>
            g.name shouldBe Text("Large-scale overlay on terrain")
            maybeAltitude shouldBe None
            maybeAltitudeMode shouldBe None
            latLonBox.north shouldBe Latitude(37.91904192681665)
            latLonBox.south shouldBe Latitude(37.46543388598137)
            latLonBox.east shouldBe Longitude(15.35832653742206)
            latLonBox.west shouldBe Longitude(14.60128369746704)
            latLonBox.rotation shouldBe Rotation(-0.1556640799496235)
            g.maybeDrawOrder shouldBe None
            g.maybeColor shouldBe None
            g.Icon shouldBe Icon(Text("https://developers.google.com/kml/documentation/images/etna.jpg"))
//            val overlayData: OverlayData = g.overlayData
//            val featureData: FeatureData = overlayData.featureData
        }
      case Failure(x) => fail(x)
    }
  }

  // TODO Issue #38
  it should "extract PhotoOverlay" in {
    val xml = <PhotoOverlay>
      <!-- inherited from Feature element -->
      <name>Test Photo Overlay</name> <!-- string -->
      <visibility>1</visibility> <!-- boolean -->
      <open>0</open> <!-- boolean -->
      <!-- xmlns:atom -->
      <!-- xmlns:atom -->
      <address>110 Huntingdon Avenue</address> <!-- string -->
      <!-- xmlns:xal -->
      <phoneNumber>123456789</phoneNumber> <!-- string -->
      <!-- <Snippet maxLines="2">...</Snippet> --> <!-- string -->
      <description>Fiction</description> <!-- string -->
      <LookAt>
        <longitude>15.02468937557116</longitude>
        <latitude>37.67395167941667</latitude>
        <altitude>0</altitude>
        <heading>-16.5581842842829</heading>
        <tilt>58.31228652890705</tilt>
        <range>30350.36838438907</range>
      </LookAt> <!-- Camera or LookAt -->
      <!-- <TimePrimitive>...</TimePrimitive> -->
      <styleUrl>#icon-22-nodesc-normal</styleUrl> <!-- anyURI -->
      <StyleMap id="icon-22-nodesc">
        <Pair>
          <key>normal</key>
          <styleUrl>#icon-22-nodesc-normal</styleUrl>
        </Pair>
        <Pair>
          <key>highlight</key>
          <styleUrl>#icon-22-nodesc-highlight</styleUrl>
        </Pair>
      </StyleMap>
      <!-- <Region>...</Region> -->
      <!-- <Metadata>...</Metadata> --> <!-- deprecated in KML 2.2 -->
      <!-- <ExtendedData>...</ExtendedData> --> <!-- new in KML 2.2 -->

      <!-- inherited from Overlay element -->
      <color>ffffffff</color> <!-- kml:color -->
      <drawOrder>0</drawOrder> <!-- int -->
      <Icon>
        <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href> <!-- anyURI -->
      </Icon>

      <!-- specific to PhotoOverlay -->
      <rotation>0</rotation> <!-- kml:angle180 -->
      <ViewVolume>
        <leftFov>0</leftFov> <!-- kml:angle180 -->
        <rightFov>0</rightFov> <!-- kml:angle180 -->
        <bottomFov>0</bottomFov> <!-- kml:angle90 -->
        <topFov>0</topFov> <!-- kml:angle90 -->
        <near>0</near> <!-- double -->
      </ViewVolume>
      <ImagePyramid>
        <tileSize>256</tileSize> <!-- int -->
        <maxWidth>10</maxWidth> <!-- int -->
        <maxHeight>99</maxHeight> <!-- int -->
        <gridOrigin>lowerLeft</gridOrigin> <!-- lowerLeft or upperLeft -->
      </ImagePyramid>
      <Point>
        <coordinates>
          -71.380341,42.571137,0
        </coordinates>
      </Point>
      <shape>rectangle</shape> <!-- kml:shape -->
    </PhotoOverlay>

    val po = extract[PhotoOverlay](xml)
    println(po)
    po.isSuccess shouldBe true
    po.get should matchPattern { case PhotoOverlay(_, _, _, _, _) => }
  }

  behavior of "enumerated types"

  it should "extract shape" in {
    val xml = <shape>rectangle</shape>
    val po = extract[Shape](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.$ shouldBe ShapeEnum.rectangle
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Shape](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<Shape>rectangle</Shape>"
  }

  it should "extract (optional) altitude mode" in {
    val xml = <altitudeMode>clampToGround</altitudeMode>
    val po = extract[Option[AltitudeMode]](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.isDefined shouldBe true
    p.get.$ shouldBe AltitudeModeEnum.clampToGround
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Option[AltitudeMode]](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<AltitudeMode>clampToGround</AltitudeMode>"
  }

  it should "extract (optional) color mode" in {
    val xml = <colorMode>random</colorMode>
    val po = extract[Option[ColorMode]](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.isDefined shouldBe true
    p.get.$ shouldBe ColorModeEnum.random
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Option[ColorMode]](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<ColorMode>random</ColorMode>"
  }

  it should "extract (optional) display mode" in {
    val xml = <displayMode>default</displayMode>
    val po = extract[Option[DisplayMode]](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.isDefined shouldBe true
    p.get.$ shouldBe DisplayModeEnum.default
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Option[DisplayMode]](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<DisplayMode>default</DisplayMode>"
  }

  it should "extract (optional) listItemType" in {
    val xml = <listItemType>checkOffOnly</listItemType>
    val po = extract[Option[ListItemType]](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.isDefined shouldBe true
    p.get.$ shouldBe ListItemTypeEnum.checkOffOnly
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Option[ListItemType]](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<ListItemType>checkOffOnly</ListItemType>"
  }

  it should "extract (optional) refresh mode" in {
    val xml = <refreshMode>onChange</refreshMode>
    val po = extract[Option[RefreshMode]](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.isDefined shouldBe true
    p.get.$ shouldBe RefreshModeEnum.onChange
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Option[RefreshMode]](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<RefreshMode>onChange</RefreshMode>"
  }

  it should "extract styleState" in {
    val xml = <Key>highlight</Key>
    val po = extract[Key](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.$ shouldBe StyleStateEnum.highlight
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Key](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<Key>highlight</Key>"
  }

  it should "extract state" in {
    val xml = <state>open</state>
    val po = extract[State](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.$ shouldBe ItemIconModeEnum.open
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[State](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<State>open</State>"
  }

  it should "extract (optional) view refresh mode" in {
    val xml = <viewRefreshMode>onRequest</viewRefreshMode>
    val po = extract[Option[ViewRefreshMode]](xml)
    po.isSuccess shouldBe true
    val p = po.get
    p.isDefined shouldBe true
    p.get.$ shouldBe ViewRefreshEnum.onRequest
    // CONSIDER how can we make the rendered string flat (no newline) and also with lower case tag "scale"?
    val triedString = Renderer.render[Option[ViewRefreshMode]](p, FormatXML(), StateR())
    triedString.isSuccess shouldBe true
    triedString.get shouldBe "<ViewRefreshMode>onRequest</ViewRefreshMode>"
  }

  behavior of "HotSpot"

  it should "extract HotSpot" in {
    val xml = <xml>
      <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
    </xml>
    val nodeSeq = xml / "hotSpot"
    nodeSeq.size shouldBe 1

    Extractor.extract[HotSpot](nodeSeq.head) match {
      case Success(hotSpot) =>
        hotSpot shouldBe HotSpot(16, UnitsEnum.pixels, 32, UnitsEnum.insetPixels)
        // XXX we test two versions of rendering here:
        // XXX the first is simply rendering a HotSpot object as is.
        val wy1 = TryUsing(StateR())(sr => Renderer.render[HotSpot](hotSpot, FormatXML(), sr))
        wy1.isSuccess shouldBe true
        wy1.get shouldBe """<HotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"></HotSpot>"""
        // XXX the second is rendering a HotSpot object as if it was in the context of its parent where the attribute name starts with lower case h.
        val wy2 = TryUsing(StateR())(sr => Renderer.render[HotSpot](hotSpot, FormatXML(), sr.setName("hotSpot")))
        wy2.isSuccess shouldBe true
        wy2.get shouldBe """<hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"></hotSpot>"""
      case Failure(x) => fail(x)
    }
  }

  behavior of "OverlayXY"

  it should "extract OverlayXY" in {
    val xml = <xml>
      <overlayXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"/>
    </xml>
    val nodeSeq = xml / "overlayXY"
    nodeSeq.size shouldBe 1

    Extractor.extract[OverlayXY](nodeSeq.head) match {
      case Success(overlayXY) =>
        overlayXY shouldBe OverlayXY(0.5, 0.5,UnitsEnum.fraction, UnitsEnum.fraction)
        // XXX we test two versions of rendering here:
        // XXX the first is simply rendering an OverlayXY object as is.
        val wy1 = TryUsing(StateR())(sr => Renderer.render[OverlayXY](overlayXY, FormatXML(), sr))
        wy1.isSuccess shouldBe true
        wy1.get shouldBe """<OverlayXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"></OverlayXY>"""
        // XXX the second is rendering an OverlayXY object as if it was in the context of its parent where the attribute name starts with lower case h.
        val wy2 = TryUsing(StateR())(sr => Renderer.render[OverlayXY](overlayXY, FormatXML(), sr.setName("overlayXY")))
        wy2.isSuccess shouldBe true
        wy2.get shouldBe """<overlayXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"></overlayXY>"""
      case Failure(x) => fail(x)
    }
  }

  behavior of "ScreenXY"

  it should "extract ScreenXY" in {
    val xml = <xml>
      <screenXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"/>
    </xml>
    val nodeSeq = xml / "screenXY"
    nodeSeq.size shouldBe 1

    Extractor.extract[ScreenXY](nodeSeq.head) match {
      case Success(screenXY) =>
        screenXY shouldBe ScreenXY(0.5, 0.5, UnitsEnum.fraction, UnitsEnum.fraction)
        // XXX we test two versions of rendering here:
        // XXX the first is simply rendering a ScreenXY object as is.
        val wy1 = TryUsing(StateR())(sr => Renderer.render[ScreenXY](screenXY, FormatXML(), sr))
        wy1.isSuccess shouldBe true
        wy1.get shouldBe """<ScreenXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"></ScreenXY>"""
        // XXX the second is rendering a ScreenXY object as if it was in the context of its parent where the attribute name starts with lower case h.
        val wy2 = TryUsing(StateR())(sr => Renderer.render[ScreenXY](screenXY, FormatXML(), sr.setName("screenXY")))
        wy2.isSuccess shouldBe true
        wy2.get shouldBe """<screenXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"></screenXY>"""
      case Failure(x) => fail(x)
    }
  }

  behavior of "RotationXY"

  it should "extract RotationXY" in {
    val xml = <xml>
      <rotationXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"/>
    </xml>
    val nodeSeq = xml / "rotationXY"
    nodeSeq.size shouldBe 1

    Extractor.extract[RotationXY](nodeSeq.head) match {
      case Success(rotationXY) =>
        rotationXY shouldBe RotationXY(0.5, 0.5,UnitsEnum.fraction, UnitsEnum.fraction)
        // XXX we test two versions of rendering here:
        // XXX the first is simply rendering a RotationXY object as is.
        val wy1 = TryUsing(StateR())(sr => Renderer.render[RotationXY](rotationXY, FormatXML(), sr))
        wy1.isSuccess shouldBe true
        wy1.get shouldBe """<RotationXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"></RotationXY>"""
        // XXX the second is rendering a RotationXY object as if it was in the context of its parent where the attribute name starts with lower case r.
        val wy2 = TryUsing(StateR())(sr => Renderer.render[RotationXY](rotationXY, FormatXML(), sr.setName("rotationXY")))
        wy2.isSuccess shouldBe true
        wy2.get shouldBe """<rotationXY x="0.5" y="0.5" xunits="fraction" yunits="fraction"></rotationXY>"""
      case Failure(x) => fail(x)
    }
  }

  behavior of "Size"

  it should "extract Size" in {
    val xml = <xml>
      <size x="0" y="0" xunits="pixels" yunits="pixels"/>
    </xml>
    val nodeSeq = xml / "size"
    nodeSeq.size shouldBe 1

    Extractor.extract[Size](nodeSeq.head) match {
      case Success(size) =>
        size shouldBe Size(0, 0,UnitsEnum.pixels, UnitsEnum.pixels)
        // XXX we test two versions of rendering here:
        // XXX the first is simply rendering a Size object as is.
        val wy1 = TryUsing(StateR())(sr => Renderer.render[Size](size, FormatXML(), sr))
        wy1.isSuccess shouldBe true
        wy1.get shouldBe """<Size x="0" y="0" xunits="pixels" yunits="pixels"></Size>"""
        // XXX the second is rendering a Size object as if it was in the context of its parent where the attribute name starts with lower case r.
        val wy2 = TryUsing(StateR())(sr => Renderer.render[Size](size, FormatXML(), sr.setName("size")))
        wy2.isSuccess shouldBe true
        wy2.get shouldBe """<size x="0" y="0" xunits="pixels" yunits="pixels"></size>"""
      case Failure(x) => fail(x)
    }
  }

  behavior of "ViewVolume"

  it should "extract ViewVolume" in {
    val xml = <ViewVolume>
      <leftFov>0</leftFov> <!-- kml:angle180 -->
      <rightFov>0</rightFov> <!-- kml:angle180 -->
      <bottomFov>0</bottomFov> <!-- kml:angle90 -->
      <topFov>0</topFov> <!-- kml:angle90 -->
      <near>0</near> <!-- double -->
    </ViewVolume>

    val po = extract[ViewVolume](xml)
    println(po)
    po.isSuccess shouldBe true
    po.get should matchPattern { case ViewVolume(_, _, _, _, _) => }
  }

  behavior of "ImagePyramid"
  it should "extract ImagePyramid" in {

    val xml =
      <ImagePyramid>
        <tileSize>256</tileSize> <!-- int -->
        <maxWidth>10</maxWidth> <!-- int -->
        <maxHeight>99</maxHeight> <!-- int -->
        <gridOrigin>lowerLeft</gridOrigin> <!-- lowerLeft or upperLeft -->
      </ImagePyramid>

    val po = extract[ImagePyramid](xml)
    println(po)
    po.isSuccess shouldBe true
    po.get should matchPattern { case ImagePyramid(_, _, _, _) => }
  }

  behavior of "Style"

  // TODO resolve spacing issues
  private val iconStyleText1 =
    """<IconStyle>
      |  <scale>1.1</scale>
      |  <Icon>
      |    <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
      |  </Icon>
      |  <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
      |</IconStyle>""".stripMargin
  private val iconStyleText = "<IconStyle>\n    <scale>1.1</scale>\n    <Icon>\n      <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>\n    </Icon>\n    <hotSpot x=\"16\" xunits=\"pixels\" y=\"32\" yunits=\"insetPixels\"/>\n  </IconStyle>"
  private val balloonStyleText = "<BalloonStyle>\n    <text>\n<![CDATA[<h3>$[name]</h3>]]>\n</text>\n  </BalloonStyle>"
  private val labelStyleText = "<LabelStyle>\n    <color>ff0000cc</color>\n    <colorMode>random</colorMode>\n    <scale>1.5</scale>\n  </LabelStyle>"
  private val stylesText = s"\n  $labelStyleText\n  $iconStyleText\n  $balloonStyleText\n"
  private val styleText = s"<Style id=\"icon-22-nodesc-normal\">$stylesText</Style>"

  // Issue #42
  it should "extract IconStyle1" in {
    val xml = <xml>
      <IconStyle>
        <scale>1.1</scale>
        <Icon>
          <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
        </Icon>
        <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
      </IconStyle>
    </xml>
    val nodeSeq = xml / "IconStyle"
    nodeSeq.size shouldBe 1
    val iconStyle = nodeSeq.head
    extract[IconStyle](iconStyle) match {
      case Success(is) =>
        is match {
          case x@IconStyle(maybeScale, icon, maybeHotSpot, maybeHeading) =>
            maybeScale shouldBe Some(Scale(1.1)(KmlData.nemo))
            icon shouldBe Icon(Text("https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png"))
            maybeHotSpot shouldBe Some(HotSpot(16, UnitsEnum.pixels, 32, UnitsEnum.insetPixels))
            maybeHeading shouldBe None
            x.colorStyleData match {
              case c@ColorStyleData(_, _) =>
                println(c)
            }
        }
        val wy = TryUsing(StateR())(sr => Renderer.render[IconStyle](is, FormatXML(), sr))
        wy.isSuccess shouldBe true
        wy shouldBe Success(
          s"""<IconStyle>
             |  <scale>1.1</scale>
             |  <Icon>
             |    <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
             |  </Icon>
             |  <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
             |</IconStyle>""".stripMargin)
      case Failure(x) => fail(x)
    }
  }
  it should "extract IconStyle2" in {
    val xml =
      <IconStyle>
        <scale>1.1</scale>
        <Icon>
          <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
        </Icon>
        <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
      </IconStyle>

    extract[IconStyle](xml) match {
      case Success(is) =>
        is match {
          case x@IconStyle(maybeScale, icon, maybeHotSpot, maybeHeading) =>
            maybeScale shouldBe Some(Scale(1.1)(KmlData.nemo))
            icon shouldBe Icon(Text("https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png"))
            maybeHotSpot shouldBe Some(HotSpot(16, UnitsEnum.pixels, 32, UnitsEnum.insetPixels))
            maybeHeading shouldBe None
            x.colorStyleData match {
              case c@ColorStyleData(_, _) =>
                println(c)
            }
        }
        val wy = TryUsing(StateR())(sr => Renderer.render[IconStyle](is, FormatXML(), sr))
        wy.isSuccess shouldBe true
        wy shouldBe Success(
          s"""<IconStyle>
             |  <scale>1.1</scale>
             |  <Icon>
             |    <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
             |  </Icon>
             |  <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
             |</IconStyle>""".stripMargin)
      case Failure(x) => fail(x)
    }
  }

  it should "extract and render Styles (type A)" in {
    val xml = <xml>
      <Style id="icon-22-nodesc-normal">
        <IconStyle>
          <scale>1.1</scale>
          <Icon>
            <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
          </Icon>
          <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
        </IconStyle>
        <LabelStyle>
          <scale>0</scale>
        </LabelStyle>
        <BalloonStyle>
          <text>
            <![CDATA[<h3>$[name]</h3>]]>
          </text>
          <displayMode>default</displayMode>
        </BalloonStyle>
      </Style>
      <Style id="icon-22-nodesc-highlight">
        <IconStyle>
          <scale>1.1</scale>
          <Icon>
            <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
          </Icon>
          <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
        </IconStyle>
        <LabelStyle>
          <scale>1.1</scale>
        </LabelStyle>
        <BalloonStyle>
          <text>
            <![CDATA[<h3>$[name]</h3>]]>
          </text>
        </BalloonStyle>
      </Style>
      <StyleMap id="icon-22-nodesc">
        <Pair>
          <key>normal</key>
          <styleUrl>#icon-22-nodesc-normal</styleUrl>
        </Pair>
        <Pair>
          <key>highlight</key>
          <styleUrl>#icon-22-nodesc-highlight</styleUrl>
        </Pair>
      </StyleMap>
    </xml>
    extractAll[Seq[StyleSelector]](xml) match {
      case Success(ss) =>
        ss.size shouldBe 3
        val styleType: StyleSelector = ss.head
        styleType match {
          case s@Style(styles) =>
            styles.size shouldBe 3
            println(styles)
            styles.head match {
              case x@LabelStyle(scale) =>
                scale shouldBe Scale(0)(KmlData.nemo)
                x.colorStyleData match {
                  case c@ColorStyleData(_, _) =>
                    println(c)
                }
            }
            styles(1) match {
              case x@IconStyle(maybeScale, icon, maybeHotSpot, maybeHeading) =>
                maybeScale shouldBe Some(Scale(1.1)(KmlData.nemo))
                icon shouldBe Icon(Text("https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png"))
                maybeHotSpot shouldBe Some(HotSpot(16, UnitsEnum.pixels, 32, UnitsEnum.insetPixels))
                maybeHeading shouldBe None
                x.colorStyleData match {
                  case c@ColorStyleData(_, _) =>
                    println(c)
                }
            }
            styles(2) match {
              case x@BalloonStyle(text, maybeBgColor, maybeTextColor, maybeDisplayMode) =>
                println(s"text: $text")
                val cdata = CDATA.wrapped("<h3>$[name]</h3>")
                text.$ shouldBe cdata
                maybeBgColor shouldBe None
                maybeTextColor shouldBe None
                maybeDisplayMode shouldBe Some(DisplayMode(DisplayModeEnum.default))
                x.colorStyleData match {
                  case c@ColorStyleData(_, _) =>
                    println(c)
                }
            }
            val cdata = "\n<![CDATA[<h3>$[name]</h3>]]>\n"
            val wy = TryUsing(StateR())(sr => Renderer.render[Style](s, FormatXML(), sr))
            wy.isSuccess shouldBe true
            val expected =
              s"""<Style id="icon-22-nodesc-normal">
                 |  <LabelStyle>
                 |    <scale>0</scale>
                 |  </LabelStyle>
                 |  <IconStyle>
                 |    <scale>1.1</scale>
                 |    <Icon>
                 |      <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
                 |    </Icon>
                 |    <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
                 |  </IconStyle>
                 |  <BalloonStyle>
                 |    <text>$cdata</text>
                 |    <displayMode>default</displayMode>
                 |  </BalloonStyle>
                 |</Style>""".stripMargin
            wy.get shouldBe expected
          case StyleMap(pairs) =>
            pairs.size shouldBe 2
            pairs.head match {
              case Pair(key, styleUrl) =>
                key.toString shouldBe "normal"
                styleUrl.toString shouldBe "#icon-22-nodesc-normal"
            }
        }
      case Failure(x) => fail(x)
    }
  }

  it should "extract and render Styles (type A) part 1" in {
    val xml =
      <IconStyle>
        <scale>1.1</scale>
        <Icon>
          <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
        </Icon>
        <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
      </IconStyle>
    val extractor = summon[Extractor[IconStyle]]
    extract[IconStyle](xml) match {
      case Success(ss) =>
        ss match {
          case x@IconStyle(maybeScale, icon, maybeHotSpot, maybeHeading) =>
            maybeScale shouldBe Some(Scale(1.1)(KmlData.nemo))
            icon shouldBe Icon(Text("https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png"))
            maybeHotSpot shouldBe Some(HotSpot(16, UnitsEnum.pixels, 32, UnitsEnum.insetPixels))
            maybeHeading shouldBe None
            x.colorStyleData match {
              case c@ColorStyleData(_, _) =>
                println(c)
            }
        }
      case scala.util.Failure(_) => fail("should have succeeded")
    }
  }

  it should "extract Styles (type B)" in {
    val xml = <xml>
      <Style id="icon-22-nodesc-normal">
        <IconStyle>
          <scale>1.1</scale>
          <Icon>
            <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
          </Icon>
          <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
        </IconStyle>
        <LabelStyle>
          <color>ff0000cc</color>
          <colorMode>random</colorMode>
          <scale>1.5</scale>
        </LabelStyle>
        <BalloonStyle>
          <text>
            <![CDATA[<h3>$[name]</h3>]]>
          </text>
        </BalloonStyle>
      </Style>
      <Style id="icon-22-nodesc-highlight">
        <IconStyle>
          <scale>1.1</scale>
          <Icon>
            <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
          </Icon>
          <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
        </IconStyle>
        <LabelStyle>
          <scale>1.1</scale>
        </LabelStyle>
        <BalloonStyle>
          <text>
            <![CDATA[<h3>$[name]</h3>]]>
          </text>
        </BalloonStyle>
      </Style>
      <StyleMap id="icon-22-nodesc">
        <Pair>
          <key>normal</key>
          <styleUrl>#icon-22-nodesc-normal</styleUrl>
        </Pair>
        <Pair>
          <key>highlight</key>
          <styleUrl>#icon-22-nodesc-highlight</styleUrl>
        </Pair>
      </StyleMap>
    </xml>
    extractAll[Seq[StyleSelector]](xml)
    match {
      case Success(ss) =>
        ss.size shouldBe 3
        val styleSelector: StyleSelector = ss.head
        styleSelector match {
          case Style(styles) =>
            styles.size shouldBe 3
            styles.head match {
              case LabelStyle(ls) =>
                ls.$ shouldBe 1.5
            }
            styles(1) match {
              case IconStyle(scale, Icon(Text(w)), hotSpot, maybeHeading) =>
                scale shouldBe Some(Scale(1.1)(KmlData(None)))
                w shouldBe "https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png"
                hotSpot shouldBe Some(HotSpot(16, UnitsEnum.pixels, 32, UnitsEnum.insetPixels))
                maybeHeading shouldBe None
                val wy = TryUsing(StateR())(sr => Renderer.render[SubStyle](styles(1), FormatXML(), sr))
                wy.isSuccess shouldBe true
                wy.get shouldBe iconStyleText1
            }
          case StyleMap(pairs) =>
            pairs.size shouldBe 2
            pairs.head shouldBe Pair(Key(StyleStateEnum.normal), StyleURL("#icon-22-nodesc-normal"))
        }
        val wy = TryUsing(StateR())(sr => Renderer.render[StyleSelector](styleSelector, FormatXML(), sr))
        wy.isSuccess shouldBe true
        wy.get shouldBe styleText
      case Failure(x) => fail(x)
    }
  }

  behavior of "StyleMap"

  it should "extract StyleMap" in {
    val xml = <xml>
      <StyleMap id="icon-22-nodesc">
        <Pair>
          <key>normal</key>
          <styleUrl>#icon-22-nodesc-normal</styleUrl>
        </Pair>
        <Pair>
          <key>highlight</key>
          <styleUrl>#icon-22-nodesc-highlight</styleUrl>
        </Pair>
      </StyleMap>
    </xml>
    extractMulti[Seq[StyleSelector]](xml / "StyleMap") match {
      case Success(ss) =>
        ss.size shouldBe 1
        val styleMap: StyleMap = ss.head.asInstanceOf[StyleMap] // use pattern-matching
        styleMap shouldBe StyleMap(List(Pair(Key(StyleStateEnum.normal), StyleURL("#icon-22-nodesc-normal")), Pair(Key(StyleStateEnum.highlight), StyleURL("#icon-22-nodesc-highlight"))))(StyleSelectorData(KmlData(Some("icon-22-nodesc"))))
        val wy = TryUsing(StateR())(sr => Renderer.render[StyleMap](styleMap, FormatXML(), sr))
        wy.isSuccess shouldBe true
        wy.get shouldBe "<StyleMap id=\"icon-22-nodesc\">\n  <Pair>\n    <key>normal</key>\n    <styleUrl>#icon-22-nodesc-normal</styleUrl>\n  </Pair>\n  <Pair>\n    <key>highlight</key>\n    <styleUrl>#icon-22-nodesc-highlight</styleUrl>\n  </Pair>\n</StyleMap>"
      case Failure(x) => fail(x)
    }
  }

  it should "extract StyleMap without Pairs" in {
    val xml = <xml>
      <StyleMap id="icon-22-nodesc">
      </StyleMap>
    </xml>
    extractMulti[Seq[StyleSelector]](xml / "StyleMap") match {
      case Failure(_: XmlException) =>
      case z => fail(s"should be at least one Pair: $z")
    }
  }

  behavior of "Document"

  it should "extract Document" in {
    val xml = <xml>
      <Document>
        <name>MA - Boston NE: Historic New England Railroads</name>
        <description>See description of Historic New England Railroads (MA - Boston NW). Full index: https://www.rubecula.com/RRMaps/</description>
        <Style id="icon-22-nodesc-normal">
          <IconStyle>
            <scale>1.1</scale>
            <Icon>
              <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
            </Icon>
            <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
          </IconStyle>
          <LabelStyle>
            <scale>0</scale>
          </LabelStyle>
          <BalloonStyle>
            <text>
              <![CDATA[<h3>$[name]</h3>]]>
            </text>
          </BalloonStyle>
        </Style>
        <Style id="icon-22-nodesc-highlight">
          <IconStyle>
            <scale>1.1</scale>
            <Icon>
              <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
            </Icon>
            <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
          </IconStyle>
          <LabelStyle>
            <scale>1.1</scale>
          </LabelStyle>
          <BalloonStyle>
            <text>
              <![CDATA[<h3>$[name]</h3>]]>
            </text>
          </BalloonStyle>
        </Style>
        <StyleMap id="icon-22-nodesc">
          <Pair>
            <key>normal</key>
            <styleUrl>#icon-22-nodesc-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#icon-22-nodesc-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-0000FF-5000-normal">
          <LineStyle>
            <color>ffff0000</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-0000FF-5000-highlight">
          <LineStyle>
            <color>ffff0000</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-0000FF-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-0000FF-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-0000FF-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-006600-5000-normal">
          <LineStyle>
            <color>ff006600</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-006600-5000-highlight">
          <LineStyle>
            <color>ff006600</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-006600-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-006600-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-006600-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-6600CC-5000-normal">
          <LineStyle>
            <color>ffcc0066</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-6600CC-5000-highlight">
          <LineStyle>
            <color>ffcc0066</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-6600CC-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-6600CC-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-6600CC-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-663366-5000-normal">
          <LineStyle>
            <color>ff663366</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-663366-5000-highlight">
          <LineStyle>
            <color>ff663366</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-663366-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-663366-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-663366-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-993399-5000-normal">
          <LineStyle>
            <color>ff993399</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-993399-5000-highlight">
          <LineStyle>
            <color>ff993399</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-993399-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-993399-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-993399-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-CC33CC-5000-normal">
          <LineStyle>
            <color>ffcc33cc</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-CC33CC-5000-highlight">
          <LineStyle>
            <color>ffcc33cc</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-CC33CC-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-CC33CC-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-CC33CC-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-FF0000-5000-normal">
          <LineStyle>
            <color>ff0000ff</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-FF0000-5000-highlight">
          <LineStyle>
            <color>ff0000ff</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-FF0000-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-FF0000-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-FF0000-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-FFCC00-5000-normal">
          <LineStyle>
            <color>ff00ccff</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-FFCC00-5000-highlight">
          <LineStyle>
            <color>ff00ccff</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-FFCC00-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-FFCC00-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-FFCC00-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-FFFF00-2000-normal">
          <LineStyle>
            <color>ff00ffff</color>
            <width>2</width>
          </LineStyle>
        </Style>
        <Style id="line-FFFF00-2000-highlight">
          <LineStyle>
            <color>ff00ffff</color>
            <width>3</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-FFFF00-2000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-FFFF00-2000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-FFFF00-2000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Folder>
          <name>Untitled layer</name>
          <Placemark>
            <name>Stoneham Branch</name>
            <description>
              <![CDATA[K405<br>RDK1: 51B]]>
            </description>
            <styleUrl>#line-FF0000-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.126508,42.477003,0
                -71.126368,42.477585,0
                -71.126282,42.477779,0
                -71.126186,42.477949,0
                -71.126046,42.478139,0
                -71.125896,42.478305,0
                -71.125724,42.478471,0
                -71.125542,42.47861,0
                -71.125301,42.478776,0
                -71.125059,42.478902,0
                -71.124802,42.479013,0
                -71.124523,42.479108,0
                -71.124206,42.479175,0
                -71.1239,42.47923,0
                -71.1236,42.47925,0
                -71.1233,42.47925,0
                -71.122988,42.479223,0
                -71.12269,42.47918,0
                -71.121615,42.479005,0
                -71.120526,42.478823,0
                -71.118702,42.478534,0
                -71.118144,42.478447,0
                -71.117683,42.478396,0
                -71.117195,42.478368,0
                -71.116712,42.47838,0
                -71.116331,42.478416,0
                -71.115226,42.478534,0
                -71.114593,42.478602,0
                -71.114298,42.478633,0
                -71.114025,42.478645,0
                -71.113601,42.478645,0
                -71.112898,42.478625,0
                -71.112319,42.478625,0
                -71.111718,42.478641,0
                -71.111353,42.478673,0
                -71.110967,42.478728,0
                -71.110699,42.478784,0
                -71.11042,42.478855,0
                -71.110017,42.47897,0
                -71.109615,42.479108,0
                -71.10932,42.479231,0
                -71.109046,42.479365,0
                -71.108783,42.479512,0
                -71.108558,42.479666,0
                -71.107909,42.480125,0
                -71.105672,42.481691,0
                -71.10512,42.482095,0
                -71.104556,42.48253,0
                -71.104159,42.482803,0
                -71.103961,42.482957,0
                -71.103725,42.483167,0
                -71.10351,42.483397,0
                -71.103296,42.483638,0
                -71.103103,42.483875,0
                -71.102024,42.485106,0
                -71.101826,42.485295,0
                -71.101643,42.485462,0
                -71.101434,42.485604,0
                -71.101198,42.485758,0
                -71.100892,42.485901,0
                -71.100565,42.486023,0
                -71.100297,42.486103,0
                -71.100007,42.48617,0
                -71.099734,42.486221,0
                -71.09944,42.48627,0
                -71.0992403,42.4862899,0
                -71.0990166,42.4862938,0
                -71.0987616,42.4862828,0
                -71.0985082,42.4862587,0
                -71.0981516,42.4861877,0
                -71.0977833,42.4860848,0
                -71.097507,42.485972,0
                -71.097229,42.485849,0
                -71.096896,42.485683,0
                -71.096574,42.485462,0
                -71.096268,42.485197,0
                -71.09592,42.484852,0
                -71.095571,42.484453,0
                -71.095233,42.484053,0
                -71.095056,42.483788,0
                -71.094906,42.483531,0
                -71.09482,42.483353,0
                -71.094734,42.483151,0
                -71.094659,42.482918,0
                -71.094605,42.482704,0
                -71.094589,42.482459,0
                -71.094584,42.48217,0
                -71.0946,42.481949,0
                -71.094659,42.481707,0
                -71.094761,42.48145,0
                -71.094906,42.481205,0
                -71.095292,42.480651,0
                -71.095694,42.480145,0
                -71.09744,42.47812,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Wakefield Branch of Eastern RR</name>
            <description>RDK55. Also known as the South Reading Branch. Wakefield (S. Reading) Jct. to Peabody.</description>
            <styleUrl>#line-006600-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.06992,42.49424,0
                -71.07018,42.49512,0
                -71.07021,42.49549,0
                -71.07008,42.49648,0
                -71.069849,42.497415,0
                -71.06954,42.49833,0
                -71.069173,42.49933,0
                -71.06879,42.50028,0
                -71.068121,42.501386,0
                -71.067713,42.501964,0
                -71.067327,42.502462,0
                -71.06634,42.503459,0
                -71.065825,42.503933,0
                -71.0653,42.504384,0
                -71.064742,42.504819,0
                -71.064205,42.505207,0
                -71.063637,42.505594,0
                -71.063036,42.505942,0
                -71.062199,42.506393,0
                -71.061373,42.506797,0
                -71.060514,42.507168,0
                -71.05963,42.50749,0
                -71.058744,42.507793,0
                -71.057918,42.508023,0
                -71.057081,42.508244,0
                -71.05618,42.50847,0
                -71.05124,42.50961,0
                -71.0479834,42.5103672,0
                -71.044797,42.511099,0
                -71.042415,42.511613,0
                -71.040623,42.512032,0
                -71.0387796,42.5124453,0
                -71.0379372,42.5126125,0
                -71.0373734,42.5127148,0
                -71.0367666,42.5128092,0
                -71.034797,42.513053,0
                -71.027534,42.51393,0
                -71.020335,42.514871,0
                -71.0161776,42.5154163,0
                -71.012138,42.515899,0
                -71.009864,42.516145,0
                -71.00848,42.516279,0
                -71.007063,42.51635,0
                -71.001517,42.516516,0
                -70.9998214,42.5166188,0
                -70.9972246,42.5168956,0
                -70.9961464,42.5169393,0
                -70.9950253,42.5169198,0
                -70.9934395,42.5168691,0
                -70.9913511,42.5168108,0
                -70.988399,42.5168949,0
                -70.9787091,42.5171527,0
                -70.971165,42.517315,0
                -70.964684,42.517449,0
                -70.960747,42.517552,0
                -70.960039,42.517592,0
                -70.9592559,42.5176868,0
                -70.95829,42.517821,0
                -70.956413,42.518169,0
                -70.954449,42.518564,0
                -70.946327,42.520225,0
                -70.943012,42.520873,0
                -70.941253,42.521229,0
                -70.9401574,42.52148,0
                -70.9395935,42.5216214,0
                -70.9391422,42.5218299,0
                -70.9387339,42.5220187,0
                -70.9382665,42.5221086,0
                -70.9376275,42.5221016,0
                -70.9373267,42.5221001,0
                -70.937026,42.522154,0
                -70.9349023,42.5226188,0
                -70.9327606,42.5230921,0
                -70.9324166,42.5231742,0
                -70.9320618,42.5232741,0
                -70.9317326,42.5233846,0
                -70.9314035,42.5235089,0
                -70.9308132,42.5237619,0
                -70.9302573,42.5240613,0
                -70.9297751,42.5243196,0
                -70.9295033,42.5244637,0
                -70.9292556,42.5246178,0
                -70.9290368,42.5247581,0
                -70.9288234,42.5249438,0
                -70.9284487,42.5252919,0
                -70.928067,42.525649,0
                -70.9276835,42.5259992,0
                -70.9274804,42.5261317,0
                -70.9272746,42.5262424,0
                -70.927016,42.526353,0
                -70.9267707,42.5264256,0
                -70.926436,42.5264831,0
                -70.9262606,42.5264913,0
                -70.926065,42.5264708,0
                -70.9257614,42.5264001,0
                -70.9254345,42.5262817,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Saugus Branch</name>
            <description>
              <![CDATA[RDK1: 60<br>Everett Jct. to West Lynn.]]>
            </description>
            <styleUrl>#line-0000FF-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.06664,42.39921,0
                -71.065139,42.400489,0
                -71.064189,42.401321,0
                -71.063873,42.401677,0
                -71.063626,42.402006,0
                -71.063481,42.402287,0
                -71.063368,42.402541,0
                -71.063272,42.402945,0
                -71.06324,42.403131,0
                -71.063223,42.403349,0
                -71.063229,42.403563,0
                -71.06325,42.40373,0
                -71.063288,42.403899,0
                -71.063347,42.40407,0
                -71.063443,42.404414,0
                -71.063524,42.404644,0
                -71.063658,42.404985,0
                -71.0641,42.40622,0
                -71.064243,42.406573,0
                -71.064371,42.406843,0
                -71.064565,42.407183,0
                -71.064726,42.407441,0
                -71.067697,42.410875,0
                -71.070659,42.414285,0
                -71.07105,42.414796,0
                -71.071356,42.415314,0
                -71.071581,42.415754,0
                -71.071715,42.416134,0
                -71.07178,42.416435,0
                -71.071844,42.416811,0
                -71.07201,42.418059,0
                -71.07208,42.419192,0
                -71.07208,42.420027,0
                -71.072059,42.420419,0
                -71.072,42.420669,0
                -71.07192,42.42091,0
                -71.071646,42.421322,0
                -71.071308,42.42175,0
                -71.070964,42.422158,0
                -71.070771,42.422308,0
                -71.070449,42.422506,0
                -71.069999,42.422736,0
                -71.069435,42.423005,0
                -71.06611,42.424577,0
                -71.063519,42.426003,0
                -71.062043,42.426878,0
                -71.059232,42.428371,0
                -71.058589,42.428731,0
                -71.058197,42.428929,0
                -71.05789,42.42907,0
                -71.057537,42.42921,0
                -71.0568,42.42947,0
                -71.056083,42.429669,0
                -71.055703,42.429756,0
                -71.05531,42.42983,0
                -71.054839,42.429907,0
                -71.05422,42.42998,0
                -71.053299,42.430057,0
                -71.052876,42.430085,0
                -71.047082,42.430469,0
                -71.03952,42.43095,0
                -71.034851,42.431269,0
                -71.032743,42.431419,0
                -71.030243,42.431586,0
                -71.029513,42.431661,0
                -71.028725,42.431752,0
                -71.028414,42.431796,0
                -71.027942,42.431875,0
                -71.027582,42.431942,0
                -71.027158,42.432033,0
                -71.026515,42.432191,0
                -71.026037,42.432318,0
                -71.025308,42.432548,0
                -71.024997,42.432647,0
                -71.0246,42.43279,0
                -71.024192,42.432948,0
                -71.023779,42.433122,0
                -71.023387,42.433296,0
                -71.023023,42.43347,0
                -71.02229,42.43385,0
                -71.021966,42.434017,0
                -71.021059,42.434535,0
                -71.01901,42.435775,0
                -71.01702,42.436962,0
                -71.016666,42.43718,0
                -71.016344,42.437406,0
                -71.016097,42.437596,0
                -71.01588,42.43779,0
                -71.015663,42.437992,0
                -71.015475,42.438197,0
                -71.015341,42.43838,0
                -71.015174,42.438617,0
                -71.015056,42.438823,0
                -71.01496,42.43902,0
                -71.01482,42.439417,0
                -71.014777,42.439623,0
                -71.014756,42.439825,0
                -71.014735,42.440046,0
                -71.014729,42.440224,0
                -71.014713,42.440533,0
                -71.014751,42.443253,0
                -71.014718,42.445806,0
                -71.014708,42.447635,0
                -71.014724,42.448035,0
                -71.01474,42.44831,0
                -71.01479,42.44879,0
                -71.01521,42.45071,0
                -71.01647,42.45494,0
                -71.01662,42.45559,0
                -71.01665,42.45676,0
                -71.01657,42.45724,0
                -71.01629,42.45814,0
                -71.01577,42.45911,0
                -71.01529,42.45978,0
                -71.01449,42.46063,0
                -71.01421,42.46088,0
                -71.01357,42.46129,0
                -71.01242,42.46174,0
                -71.01126,42.46205,0
                -71.01007,42.46224,0
                -71.00853,42.46223,0
                -70.98884,42.46016,0
                -70.98103,42.4593,0
                -70.97989,42.4592,0
                -70.97902,42.45918,0
                -70.9778,42.45925,0
                -70.97626,42.45956,0
                -70.97505,42.45993,0
                -70.96862,42.4621,0
                -70.96824,42.46215,0
                -70.96778,42.46214,0
                -70.96738,42.46203,0
                -70.96693,42.46183,0
                -70.96661,42.46155,0
                -70.96632,42.46106,0
                -70.96556,42.45924,0
                -70.96482,42.45764,0
                -70.96442,42.45712,0
                -70.96385,42.45665,0
                -70.96309,42.45624,0
                -70.96238,42.45605,0
                -70.96165,42.45598,0
                -70.96062,42.45603,0
                -70.9601,42.45618,0
                -70.9594,42.45648,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>
              <![CDATA[
            Saugus B&M connector
            ]]>
            </name>
            <description>
              <![CDATA[Saugus Branch connection with Boston & Maine (1853-55).]]>
            </description>
            <styleUrl>#line-006600-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.07677,42.41874,0
                -71.07668,42.41925,0
                -71.07658,42.41941,0
                -71.07613,42.41978,0
                -71.07578,42.41998,0
                -71.074778,42.420487,0
                -71.073893,42.42091,0
                -71.073352,42.421164,0
                -71.071056,42.422272,0
                -71.070852,42.422379,0
                -71.070712,42.42247,0
                -71.070546,42.422597,0
                -71.070278,42.422807,0
                -71.070154,42.422918,0
                -71.069967,42.422993,0
                -71.069747,42.423072,0
                -71.06827,42.42363,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Medford Branch (#1)</name>
            <description>
              <![CDATA[K218A<br>RDK1: 52A: Medford Jct to Gleenwood]]>
            </description>
            <styleUrl>#line-006600-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.076833,42.409787,0
                -71.076844,42.410318,0
                -71.076844,42.410674,0
                -71.076897,42.410999,0
                -71.077069,42.411427,0
                -71.07723,42.411807,0
                -71.077455,42.41214,0
                -71.077809,42.412441,0
                -71.078206,42.412758,0
                -71.078635,42.412963,0
                -71.079097,42.41313,0
                -71.079601,42.413272,0
                -71.080331,42.413423,0
                -71.081189,42.413581,0
                -71.08268,42.413835,0
                -71.084021,42.414065,0
                -71.085212,42.41427,0
                -71.086478,42.414492,0
                -71.087036,42.414556,0
                -71.087487,42.414556,0
                -71.087927,42.4145,0
                -71.088367,42.41435,0
                -71.088678,42.414128,0
                -71.08886,42.413914,0
                -71.089042,42.413676,0
                -71.089225,42.413304,0
                -71.089257,42.412868,0
                -71.0893,42.41248,0
                -71.0893,42.412171,0
                -71.089311,42.411916,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Medford Branch (#2)</name>
            <description>
              <![CDATA[K218A<br>RDK1 - 52A: Medford to Gleenwood.  Very approximate (practically impossible to see the Western end of this line)]]>
            </description>
            <styleUrl>#line-006600-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.086478,42.414484,0
                -71.087256,42.414639,0
                -71.08798,42.41479,0
                -71.08923,42.415023,0
                -71.090236,42.41519,0
                -71.091349,42.41539,0
                -71.09269,42.415569,0
                -71.09357,42.415704,0
                -71.09475,42.415878,0
                -71.095887,42.416061,0
                -71.097239,42.416322,0
                -71.098602,42.416552,0
                -71.099685,42.416845,0
                -71.100807,42.417083,0
                -71.101788,42.417241,0
                -71.102668,42.417344,0
                -71.103612,42.417506,0
                -71.104092,42.41759,0
                -71.104736,42.417679,0
                -71.105334,42.417782,0
                -71.106198,42.417851,0
                -71.106992,42.417898,0
                -71.107657,42.417914,0
                -71.108226,42.417954,0
                -71.108387,42.417938,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>
              <![CDATA[Salem & Lowell RR (#1)]]>
            </name>
            <description>
              <![CDATA[K235A<br>K374<br>K147<br>RDK1: 56 - Peabody to Wilmington Jct.]]>
            </description>
            <styleUrl>#line-6600CC-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.8931562,42.5287745,0
                -70.8935907,42.5281648,0
                -70.8938509,42.5278416,0
                -70.8939669,42.5277146,0
                -70.8941097,42.5275816,0
                -70.8943873,42.5273197,0
                -70.8944913,42.5272021,0
                -70.8946301,42.5270074,0
                -70.8948755,42.5266437,0
                -70.8950395,42.5263835,0
                -70.8952115,42.526157,0
                -70.8955662,42.5257473,0
                -70.8957063,42.5256099,0
                -70.895884,42.5254686,0
                -70.8960543,42.5253732,0
                -70.8962542,42.5252778,0
                -70.8976274,42.5247134,0
                -70.8986051,42.5243428,0
                -70.8997142,42.5239198,0
                -70.9008702,42.5234819,0
                -70.9020062,42.5230707,0
                -70.903995,42.5222948,0
                -70.9051665,42.5218071,0
                -70.9060885,42.5214616,0
                -70.906669,42.5212532,0
                -70.906929,42.5211811,0
                -70.9072937,42.521115,0
                -70.907713,42.5210872,0
                -70.908143,42.5211008,0
                -70.9084884,42.5211448,0
                -70.9088243,42.5212165,0
                -70.9091073,42.5213025,0
                -70.9103545,42.5217917,0
                -70.9112059,42.5221314,0
                -70.912009,42.5224483,0
                -70.9123799,42.5226022,0
                -70.9127026,42.5227244,0
                -70.9130212,42.5228437,0
                -70.9132808,42.5229314,0
                -70.9141349,42.5231663,0
                -70.9151392,42.5234289,0
                -70.9160847,42.5236945,0
                -70.9169859,42.5239364,0
                -70.9187454,42.5244142,0
                -70.9196227,42.5246503,0
                -70.920622,42.5249161,0
                -70.921538,42.5251523,0
                -70.9224915,42.5253984,0
                -70.9232989,42.5256089,0
                -70.9243557,42.5258871,0
                -70.92475,42.52602,0
                -70.93033,42.52819,0
                -70.93159,42.52887,0
                -70.93412,42.53068,0
                -70.93614,42.53197,0
                -70.93725,42.53253,0
                -70.938399,42.533018,0
                -70.939536,42.533429,0
                -70.94018,42.533587,0
                -70.94081,42.53373,0
                -70.95578,42.5367,0
                -70.957797,42.537271,0
                -70.95962,42.53786,0
                -70.9609,42.53821,0
                -70.961895,42.53841,0
                -70.96344,42.5386,0
                -70.96585,42.53875,0
                -70.96703,42.5389,0
                -70.986378,42.54271,0
                -70.9904,42.54347,0
                -70.99112,42.54358,0
                -70.991786,42.543643,0
                -70.992708,42.543675,0
                -70.994275,42.543738,0
                -70.995069,42.543785,0
                -70.99571,42.54388,0
                -70.99676,42.54408,0
                -70.99752,42.54431,0
                -70.99829,42.54466,0
                -70.99897,42.54506,0
                -70.99946,42.54541,0
                -70.9999,42.54582,0
                -71.000648,42.546726,0
                -71.00157,42.548496,0
                -71.002386,42.550219,0
                -71.002879,42.551183,0
                -71.003158,42.551895,0
                -71.00333,42.55251,0
                -71.00462,42.55993,0
                -71.0048,42.56048,0
                -71.00527,42.56133,0
                -71.005754,42.561931,0
                -71.006591,42.562738,0
                -71.007278,42.563227,0
                -71.00799,42.56361,0
                -71.008501,42.563844,0
                -71.009402,42.564223,0
                -71.014166,42.566041,0
                -71.01614,42.566878,0
                -71.01781,42.56749,0
                -71.018393,42.567668,0
                -71.019037,42.567858,0
                -71.02281,42.56874,0
                -71.0238,42.569012,0
                -71.024637,42.569154,0
                -71.02568,42.56923,0
                -71.027706,42.569233,0
                -71.029165,42.56917,0
                -71.030431,42.569154,0
                -71.031075,42.569185,0
                -71.031654,42.569249,0
                -71.035409,42.569628,0
                -71.04939,42.57094,0
                -71.055686,42.571572,0
                -71.060386,42.571951,0
                -71.061308,42.57203,0
                -71.062188,42.572061,0
                -71.064312,42.572061,0
                -71.06663,42.572046,0
                -71.072402,42.572046,0
                -71.084268,42.571967,0
                -71.086929,42.571935,0
                -71.090598,42.571967,0
                -71.096649,42.572156,0
                -71.099074,42.572219,0
                -71.099696,42.572235,0
                -71.100276,42.572283,0
                -71.10126,42.57244,0
                -71.11686,42.57547,0
                -71.12643,42.57723,0
                -71.13037,42.57801,0
                -71.13306,42.57861,0
                -71.15044,42.58268,0
                -71.15116,42.58295,0
                -71.15335,42.584,0
                -71.15467,42.58472,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>
              <![CDATA[Salem & Lowell RR (#2)]]>
            </name>
            <description>
              <![CDATA[K45<br>RDK1: 56: Wilmington Jct. to Tewksbury Jct.]]>
            </description>
            <styleUrl>#line-993399-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.154392,42.584563,0
                -71.17272,42.5936,0
                -71.182786,42.598702,0
                -71.190312,42.602425,0
                -71.20756,42.61094,0
                -71.207843,42.61112,0
                -71.209012,42.612099,0
                -71.21204,42.61479,0
                -71.212424,42.615004,0
                -71.21282,42.61517,0
                -71.213325,42.615241,0
                -71.213722,42.615289,0
                -71.214162,42.615352,0
                -71.2146,42.61539,0
                -71.215342,42.61536,0
                -71.216587,42.615178,0
                -71.223979,42.613342,0
                -71.225084,42.613102,0
                -71.225438,42.613041,0
                -71.225717,42.613007,0
                -71.226039,42.612991,0
                -71.227369,42.612983,0
                -71.231843,42.613094,0
                -71.232283,42.613109,0
                -71.232723,42.613173,0
                -71.233131,42.613228,0
                -71.233528,42.613315,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Woburn Loop</name>
            <description>K346. RDK1: 51A</description>
            <styleUrl>#line-006600-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.13692,42.45347,0
                -71.13677,42.45391,0
                -71.1367,42.45436,0
                -71.136678,42.454653,0
                -71.1367,42.454946,0
                -71.136721,42.455215,0
                -71.136786,42.455524,0
                -71.136893,42.455959,0
                -71.137065,42.456458,0
                -71.13729,42.456909,0
                -71.137419,42.457122,0
                -71.13759,42.457344,0
                -71.137902,42.457708,0
                -71.138535,42.458444,0
                -71.139629,42.459711,0
                -71.140755,42.461033,0
                -71.142118,42.462513,0
                -71.143116,42.4637,0
                -71.143867,42.464626,0
                -71.144253,42.465204,0
                -71.144521,42.46575,0
                -71.144789,42.466296,0
                -71.145347,42.467451,0
                -71.145905,42.468615,0
                -71.14647,42.46978,0
                -71.14785,42.47263,0
                -71.148791,42.474503,0
                -71.149489,42.47603,0
                -71.1498,42.4766,0
                -71.15009,42.47704,0
                -71.15034,42.47737,0
                -71.15054,42.477533,0
                -71.150798,42.477707,0
                -71.151377,42.477969,0
                -71.15219,42.47823,0
                -71.152428,42.478348,0
                -71.15263,42.47847,0
                -71.15404,42.47978,0
                -71.1550112,42.4808651,0
                -71.1555129,42.4813871,0
                -71.1557678,42.4816163,0
                -71.156151,42.481949,0
                -71.1563481,42.482133,0
                -71.156513,42.4823209,0
                -71.1566826,42.4825979,0
                -71.1567784,42.48288,0
                -71.1568485,42.4831988,0
                -71.156878,42.4835033,0
                -71.15687,42.483792,0
                -71.1568327,42.4840414,0
                -71.1567793,42.484271,0
                -71.156527,42.485129,0
                -71.156226,42.486158,0
                -71.156162,42.486435,0
                -71.156087,42.486783,0
                -71.15607,42.48714,0
                -71.156119,42.487479,0
                -71.156205,42.488025,0
                -71.156237,42.488492,0
                -71.156216,42.488737,0
                -71.156162,42.489046,0
                -71.155658,42.49191,0
                -71.155658,42.492424,0
                -71.155765,42.492993,0
                -71.155958,42.493784,0
                -71.15616,42.49442,0
                -71.1563547,42.4948563,0
                -71.156548,42.495177,0
                -71.157224,42.496031,0
                -71.1576,42.49667,0
                -71.1577446,42.4969816,0
                -71.1580093,42.4977756,0
                -71.15827,42.49872,0
                -71.15828,42.4994,0
                -71.15802,42.50103,0
                -71.157793,42.501758,0
                -71.15762,42.50245,0
                -71.157181,42.504709,0
                -71.157063,42.505302,0
                -71.156988,42.50595,0
                -71.156988,42.506211,0
                -71.15703,42.50647,0
                -71.157085,42.506749,0
                -71.157203,42.507018,0
                -71.1575,42.50753,0
                -71.158319,42.508877,0
                -71.158533,42.509304,0
                -71.158705,42.509652,0
                -71.158812,42.510063,0
                -71.158866,42.510435,0
                -71.15886,42.51092,0
                -71.158791,42.511194,0
                -71.158683,42.511463,0
                -71.157224,42.514049,0
                -71.156452,42.515085,0
                -71.155636,42.516042,0
                -71.15525,42.516619,0
                -71.155089,42.516943,0
                -71.15498,42.51726,0
                -71.154864,42.517568,0
                -71.154778,42.5179,0
                -71.154113,42.520375,0
                -71.153866,42.521245,0
                -71.153769,42.521727,0
                -71.153716,42.522281,0
                -71.153716,42.523151,0
                -71.153877,42.523736,0
                -71.15417,42.52438,0
                -71.15507,42.5268,0
                -71.15603,42.52957,0
                -71.15633,42.5312,0
                -71.15652,42.53173,0
                -71.15699,42.53244,0
                -71.15808,42.53342,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Horn Pond Branch</name>
            <description>
              <![CDATA[K26B<br>RDK1: 51E.  This branch was abandoned in 1919 so the tracks are very hard to see now.]]>
            </description>
            <styleUrl>#line-006600-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.142054,42.462419,0
                -71.142354,42.462712,0
                -71.14259,42.462958,0
                -71.142901,42.463116,0
                -71.143223,42.46325,0
                -71.14362,42.463298,0
                -71.144049,42.46333,0
                -71.144714,42.463242,0
                -71.145326,42.463132,0
                -71.146088,42.463005,0
                -71.146753,42.462965,0
                -71.147515,42.462981,0
                -71.148126,42.463116,0
                -71.148716,42.463337,0
                -71.149145,42.463496,0
                -71.149735,42.464018,0
                -71.15054,42.46481,0
                -71.150862,42.465182,0
                -71.151077,42.465546,0
                -71.151141,42.465981,0
                -71.151216,42.466392,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>
              <![CDATA[Salem & Lawrence RR (#1)]]>
            </name>
            <description>
              <![CDATA[K40<br>Danvers to Middleton]]>
            </description>
            <styleUrl>#line-CC33CC-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.939944,42.564591,0
                -70.940545,42.56514,0
                -70.940942,42.565552,0
                -70.941328,42.5659,0
                -70.941854,42.566287,0
                -70.942508,42.56685,0
                -70.943146,42.567443,0
                -70.943903,42.568119,0
                -70.944761,42.568881,0
                -70.945512,42.569582,0
                -70.946451,42.57043,0
                -70.947438,42.571287,0
                -70.948409,42.572181,0
                -70.948924,42.572663,0
                -70.949138,42.572853,0
                -70.949342,42.572995,0
                -70.949653,42.573177,0
                -70.950576,42.573698,0
                -70.95166,42.574321,0
                -70.952636,42.574947,0
                -70.953709,42.575573,0
                -70.954798,42.576229,0
                -70.955898,42.576898,0
                -70.956954,42.577501,0
                -70.958194,42.578162,0
                -70.95917,42.578715,0
                -70.960806,42.57965,0
                -70.962238,42.580485,0
                -70.963526,42.581322,0
                -70.96595,42.582752,0
                -70.967731,42.583858,0
                -70.968692,42.584544,0
                -70.969191,42.584832,0
                -70.969985,42.585193,0
                -70.970923,42.585685,0
                -70.971642,42.586005,0
                -70.973321,42.586686,0
                -70.975049,42.587326,0
                -70.976025,42.587737,0
                -70.976867,42.588122,0
                -70.97772,42.588479,0
                -70.979404,42.589157,0
                -70.980692,42.589664,0
                -70.983111,42.590681,0
                -70.984683,42.591394,0
                -70.985895,42.592065,0
                -70.98684,42.592594,0
                -70.988342,42.593384,0
                -70.989254,42.593834,0
                -70.990144,42.594253,0
                -70.991217,42.594623,0
                -70.991657,42.594782,0
                -70.991887,42.59486,0
                -70.99214,42.594915,0
                -70.993094,42.595098,0
                -70.994586,42.595341,0
                -70.995959,42.595525,0
                -70.998116,42.595833,0
                -70.999489,42.596038,0
                -71.000218,42.596159,0
                -71.000948,42.596243,0
                -71.002268,42.596417,0
                -71.003807,42.596613,0
                -71.004279,42.596676,0
                -71.004746,42.596717,0
                -71.005315,42.596741,0
                -71.00643,42.596743,0
                -71.007847,42.596757,0
                -71.009359,42.596757,0
                -71.01099,42.59682,0
                -71.011945,42.596843,0
                -71.0129,42.596946,0
                -71.013415,42.59708,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>
              <![CDATA[Salem & Lawrence RR (#2)]]>
            </name>
            <description>
              <![CDATA[RDK57. Also known as the Essex R/R originally from South Danvers (Peabody) to what became Lawrence.<br>K40<br>K207A<br>Middleton to Lawrence. Very approx in parts esp. between Middleton and Andover.]]>
            </description>
            <styleUrl>#line-CC33CC-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.01342,42.59707,0
                -71.01461,42.59765,0
                -71.01991,42.60081,0
                -71.02206,42.60188,0
                -71.0235373,42.6024579,0
                -71.02532,42.60365,0
                -71.02635,42.60517,0
                -71.02738,42.60725,0
                -71.0285,42.60959,0
                -71.02927,42.61174,0
                -71.03056,42.61463,0
                -71.03189,42.617,0
                -71.03854,42.62614,0
                -71.04,42.62841,0
                -71.04206,42.63106,0
                -71.04558,42.63491,0
                -71.046,42.63592,0
                -71.04721,42.63681,0
                -71.05021,42.63851,0
                -71.05296,42.64072,0
                -71.05785,42.64495,0
                -71.06085,42.64792,0
                -71.06506,42.65099,0
                -71.0683,42.65413,0
                -71.07105,42.65746,0
                -71.0712,42.66042,0
                -71.07126,42.66318,0
                -71.07169,42.66405,0
                -71.07242,42.66489,0
                -71.07437,42.66661,0
                -71.07549,42.66733,0
                -71.07688,42.66806,0
                -71.08611,42.67224,0
                -71.08961,42.67403,0
                -71.09051,42.67456,0
                -71.09133,42.67511,0
                -71.09207,42.67573,0
                -71.092988,42.676774,0
                -71.093712,42.677579,0
                -71.093978,42.677865,0
                -71.094241,42.678141,0
                -71.094509,42.678407,0
                -71.09479,42.678665,0
                -71.09597,42.67965,0
                -71.098567,42.681631,0
                -71.099409,42.682307,0
                -71.100823,42.683421,0
                -71.102161,42.684553,0
                -71.102499,42.68488,0
                -71.102805,42.685192,0
                -71.103111,42.685509,0
                -71.103379,42.685811,0
                -71.10387,42.686491,0
                -71.104245,42.687173,0
                -71.104918,42.688526,0
                -71.105289,42.689218,0
                -71.105629,42.689904,0
                -71.105814,42.690267,0
                -71.105997,42.690604,0
                -71.106091,42.690762,0
                -71.106192,42.6909,0
                -71.106305,42.691053,0
                -71.106423,42.691193,0
                -71.10667,42.691481,0
                -71.106914,42.691763,0
                -71.106984,42.691842,0
                -71.107048,42.691911,0
                -71.10719,42.692045,0
                -71.107324,42.692171,0
                -71.107483,42.692307,0
                -71.107839,42.692605,0
                -71.108016,42.692747,0
                -71.108185,42.692873,0
                -71.109207,42.693498,0
                -71.11021,42.69412,0
                -71.113233,42.696015,0
                -71.1163,42.6979,0
                -71.120301,42.700393,0
                -71.121205,42.700953,0
                -71.121639,42.701213,0
                -71.122077,42.701462,0
                -71.123782,42.702398,0
                -71.125773,42.703474,0
                -71.128007,42.704689,0
                -71.128417,42.704911,0
                -71.128672,42.705035,0
                -71.128975,42.705169,0
                -71.129305,42.705294,0
                -71.129627,42.705408,0
                -71.129844,42.705479,0
                -71.129903,42.705493,0
                -71.129994,42.705514,0
                -71.130112,42.705536,0
                -71.130367,42.705581,0
                -71.130877,42.70565,0
                -71.131778,42.705759,0
                -71.13222,42.70582,0
                -71.132564,42.705851,0
                -71.132733,42.705863,0
                -71.13289,42.70587,0
                -71.133175,42.705861,0
                -71.133462,42.705842,0
                -71.133704,42.705812,0
                -71.133953,42.705769,0
                -71.134082,42.705743,0
                -71.134214,42.705712,0
                -71.134356,42.705672,0
                -71.134511,42.705623,0
                -71.134683,42.705558,0
                -71.134849,42.705489,0
                -71.135018,42.705412,0
                -71.135198,42.705321,0
                -71.135367,42.705227,0
                -71.135533,42.705122,0
                -71.135847,42.704903,0
                -71.13617,42.70468,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Asbury Grove Branch</name>
            <description>
              <![CDATA[K14<br>This line is VERY approximate]]>
            </description>
            <styleUrl>#line-CC33CC-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.874627,42.609994,0
                -70.874412,42.610562,0
                -70.874283,42.611052,0
                -70.874498,42.611494,0
                -70.874841,42.611857,0
                -70.875335,42.612252,0
                -70.876193,42.612757,0
                -70.876966,42.61331,0
                -70.877931,42.613989,0
                -70.878897,42.614636,0
                -70.879562,42.615079,0
                -70.880313,42.61571,0
                -70.881343,42.617005,0
                -70.882158,42.617889,0
                -70.882995,42.618774,0
                -70.883446,42.619595,0
                -70.883929,42.620361,0
                -70.884025,42.621284,0
                -70.88424,42.621932,0
                -70.88424,42.622563,0
                -70.88424,42.623005,0
                -70.884218,42.623558,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Parts of two different R/Rs</name>
            <description>
              <![CDATA[K333<br>K167<br>North of Danvers Jct, this should be RDK54 (Newyburyport R/R). East of Danvers, it should be part of RDK 57 (Salem and Lowell).]]>
            </description>
            <styleUrl>#line-FF0000-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.92724,42.52699,0
                -70.92859,42.52754,0
                -70.9288,42.52769,0
                -70.92974,42.52856,0
                -70.93021,42.52928,0
                -70.93034,42.53007,0
                -70.9303452,42.535148,0
                -70.9305526,42.5365283,0
                -70.9307974,42.5379599,0
                -70.9308108,42.5385205,0
                -70.9307737,42.538934,0
                -70.93071,42.5392883,0
                -70.9305368,42.5400528,0
                -70.9303668,42.540562,0
                -70.9301109,42.5412312,0
                -70.9296728,42.5424293,0
                -70.9288089,42.5446646,0
                -70.9285097,42.5454652,0
                -70.9280013,42.5468704,0
                -70.926308,42.5514231,0
                -70.9262633,42.5515755,0
                -70.92624,42.55172,0
                -70.9262306,42.5519731,0
                -70.9262588,42.552236,0
                -70.9263252,42.5524458,0
                -70.9263902,42.5526122,0
                -70.9266007,42.5530209,0
                -70.9269673,42.5536996,0
                -70.9271407,42.553992,0
                -70.9272715,42.5541455,0
                -70.9273888,42.5542754,0
                -70.9276476,42.5545311,0
                -70.9282,42.55509,0
                -70.9296549,42.5564677,0
                -70.9308177,42.5575908,0
                -70.932018,42.5587148,0
                -70.9324501,42.5591222,0
                -70.9329748,42.559499,0
                -70.9349982,42.5608741,0
                -70.9359456,42.5614944,0
                -70.9365896,42.5619231,0
                -70.93718,42.56234,0
                -70.9382045,42.5631152,0
                -70.9391593,42.5639457,0
                -70.9399,42.56459,0
                -70.9401882,42.5648562,0
                -70.9403222,42.5650021,0
                -70.9404455,42.565146,0
                -70.9405292,42.5652791,0
                -70.94057,42.56542,0
                -70.9406061,42.5656049,0
                -70.9406107,42.5657038,0
                -70.940602,42.5657869,0
                -70.9405389,42.5659619,0
                -70.9404584,42.5661141,0
                -70.9402559,42.566386,0
                -70.9400612,42.5665359,0
                -70.93904,42.56718,0
                -70.9381739,42.5676422,0
                -70.9377128,42.5678911,0
                -70.9374796,42.5680195,0
                -70.9373,42.56814,0
                -70.9369866,42.5683801,0
                -70.9367,42.56864,0
                -70.9364589,42.5688581,0
                -70.93625,42.5691,0
                -70.9359316,42.5694841,0
                -70.93564,42.56988,0
                -70.9353,42.57043,0
                -70.935131,42.5707849,0
                -70.9349834,42.5711358,0
                -70.9347006,42.5723552,0
                -70.93445,42.57363,0
                -70.93379,42.57742,0
                -70.9337362,42.5780428,0
                -70.93372,42.57848,0
                -70.93373,42.57916,0
                -70.9339188,42.5805759,0
                -70.93404,42.58124,0
                -70.934155,42.5816574,0
                -70.93427,42.58198,0
                -70.9346774,42.582779,0
                -70.9353853,42.5840514,0
                -70.93553,42.58436,0
                -70.9363589,42.586598,0
                -70.9365025,42.5869745,0
                -70.9366112,42.5874438,0
                -70.93666,42.58826,0
                -70.9363677,42.591895,0
                -70.9361559,42.5944321,0
                -70.9361039,42.5946299,0
                -70.9360412,42.594812,0
                -70.9358999,42.5951007,0
                -70.9357532,42.59535,0
                -70.93536,42.59582,0
                -70.9341288,42.597038,0
                -70.9337015,42.5975961,0
                -70.9335093,42.597889,0
                -70.93336,42.59817,0
                -70.93285,42.59915,0
                -70.9311971,42.6031567,0
                -70.9303168,42.6059537,0
                -70.9291629,42.6098741,0
                -70.9281441,42.6133608,0
                -70.9278036,42.6144962,0
                -70.9276039,42.6151823,0
                -70.92749,42.6156,0
                -70.9274302,42.6159257,0
                -70.927408,42.6162118,0
                -70.9274207,42.6164362,0
                -70.9274893,42.6169316,0
                -70.92759,42.61735,0
                -70.9277039,42.617695,0
                -70.92785,42.61804,0
                -70.9280377,42.6184196,0
                -70.92822,42.61874,0
                -70.928428,42.6190376,0
                -70.92862,42.61928,0
                -70.9290189,42.6197366,0
                -70.92945,42.62013,0
                -70.934234,42.6240286,0
                -70.9455,42.6332,0
                -70.95095,42.63759,0
                -70.9514656,42.6380933,0
                -70.9518954,42.6385413,0
                -70.952218,42.638979,0
                -70.9531485,42.6406616,0
                -70.954342,42.643019,0
                -70.956112,42.6464165,0
                -70.959105,42.652095,0
                -70.96221,42.65798,0
                -70.96481,42.66284,0
                -70.9651408,42.6634921,0
                -70.9654145,42.6639454,0
                -70.9656554,42.6642968,0
                -70.9658696,42.6645683,0
                -70.9661708,42.6649589,0
                -70.9673932,42.6664387,0
                -70.9677073,42.666793,0
                -70.968,42.6671,0
                -70.9683675,42.6674084,0
                -70.9687136,42.6676773,0
                -70.9691474,42.667985,0
                -70.970306,42.668789,0
                -70.973589,42.67099,0
                -70.97676,42.67316,0
                -70.977902,42.673901,0
                -70.978954,42.674603,0
                -70.979705,42.675132,0
                -70.980273,42.675573,0
                -70.980756,42.675991,0
                -70.9811,42.67641,0
                -70.981325,42.676709,0
                -70.9815,42.67701,0
                -70.98172,42.67759,0
                -70.9818671,42.6780064,0
                -70.9819958,42.6784624,0
                -70.982231,42.6794818,0
                -70.9823599,42.6799348,0
                -70.9824376,42.6801797,0
                -70.982532,42.680436,0
                -70.982698,42.680771,0
                -70.98373,42.68288,0
                -70.985928,42.687648,0
                -70.988653,42.693405,0
                -70.991254,42.698956,0
                -70.991458,42.699457,0
                -70.991625,42.699942,0
                -70.991716,42.700301,0
                -70.99178,42.700652,0
                -70.991834,42.701046,0
                -70.991845,42.701432,0
                -70.991845,42.701704,0
                -70.991828,42.701945,0
                -70.99167,42.70364,0
                -70.9913515,42.7069809,0
                -70.991348,42.707401,0
                -70.991375,42.707775,0
                -70.991413,42.708045,0
                -70.991434,42.708195,0
                -70.991488,42.708493,0
                -70.991609,42.709068,0
                -70.991839,42.709941,0
                -70.992593,42.71246,0
                -70.993355,42.714967,0
                -70.993534,42.715595,0
                -70.993714,42.7162,0
                -70.993829,42.716541,0
                -70.993923,42.716876,0
                -70.994012,42.717198,0
                -70.99407,42.71749,0
                -70.994122,42.717875,0
                -70.994138,42.718057,0
                -70.9941728,42.7183835,0
                -70.9941621,42.7186676,0
                -70.9941247,42.7190554,0
                -70.9940817,42.7193946,0
                -70.994012,42.719742,0
                -70.99395,42.720136,0
                -70.993904,42.72051,0
                -70.993886,42.720855,0
                -70.993883,42.721109,0
                -70.993904,42.721328,0
                -70.993958,42.721698,0
                -70.99399,42.721866,0
                -70.994036,42.722049,0
                -70.994073,42.722203,0
                -70.994127,42.722368,0
                -70.994259,42.722751,0
                -70.994473,42.723393,0
                -70.994639,42.723891,0
                -70.994814,42.724412,0
                -70.994865,42.724542,0
                -70.994889,42.72464,0
                -70.994908,42.724725,0
                -70.994905,42.724816,0
                -70.994902,42.724904,0
                -70.994892,42.724981,0
                -70.99486,42.72544,0
                -70.994833,42.725584,0
                -70.994798,42.725692,0
                -70.994744,42.725832,0
                -70.994682,42.725988,0
                -70.994642,42.726085,0
                -70.994594,42.726181,0
                -70.994532,42.726276,0
                -70.994462,42.726362,0
                -70.994387,42.726453,0
                -70.994304,42.726536,0
                -70.994218,42.726613,0
                -70.994122,42.726683,0
                -70.994017,42.726739,0
                -70.993896,42.726794,0
                -70.99306,42.727152,0
                -70.9926253,42.7273081,0
                -70.9923005,42.7274286,0
                -70.9920774,42.7275185,0
                -70.9918818,42.7276036,0
                -70.991692,42.72769,0
                -70.991509,42.727791,0
                -70.99132,42.72791,0
                -70.991064,42.728097,0
                -70.990753,42.728337,0
                -70.990469,42.728625,0
                -70.990203,42.728924,0
                -70.989925,42.729287,0
                -70.989356,42.730036,0
                -70.988809,42.730761,0
                -70.988334,42.731367,0
                -70.988095,42.731631,0
                -70.987755,42.731978,0
                -70.98735,42.732315,0
                -70.986829,42.732695,0
                -70.985928,42.733302,0
                -70.985062,42.733897,0
                -70.984236,42.734455,0
                -70.983482,42.734979,0
                -70.982629,42.73556,0
                -70.981403,42.736385,0
                -70.980035,42.737325,0
                -70.9786,42.7383,0
                -70.977369,42.739137,0
                -70.976141,42.739979,0
                -70.974915,42.740802,0
                -70.973665,42.741639,0
                -70.972428,42.742472,0
                -70.9713929,42.7431896,0
                -70.970315,42.74395,0
                -70.9689254,42.7448795,0
                -70.967869,42.745589,0
                -70.966672,42.746394,0
                -70.965707,42.747048,0
                -70.964484,42.74787,0
                -70.963628,42.748443,0
                -70.962223,42.749402,0
                -70.960852,42.75042,0
                -70.959618,42.751366,0
                -70.958481,42.752248,0
                -70.957263,42.753189,0
                -70.95608,42.754095,0
                -70.954562,42.755287,0
                -70.953224,42.756311,0
                -70.952269,42.757006,0
                -70.9514533,42.7575596,0
                -70.9506518,42.7580463,0
                -70.949396,42.758771,0
                -70.947733,42.759767,0
                -70.9460384,42.7608425,0
                -70.944984,42.761545,0
                -70.944324,42.761996,0
                -70.942895,42.762999,0
                -70.940948,42.764396,0
                -70.938807,42.765892,0
                -70.93634,42.76766,0
                -70.9355677,42.7681285,0
                -70.9347784,42.7684994,0
                -70.9337109,42.7689167,0
                -70.9325361,42.7693733,0
                -70.9295751,42.7704409,0
                -70.9264264,42.7715151,0
                -70.9233581,42.7725824,0
                -70.9221077,42.7729373,0
                -70.9190981,42.7736337,0
                -70.916202,42.774311,0
                -70.9146886,42.7747014,0
                -70.9133051,42.7751654,0
                -70.912441,42.7755123,0
                -70.9099043,42.776875,0
                -70.9070022,42.7784535,0
                -70.903257,42.780505,0
                -70.9002107,42.7823001,0
                -70.897319,42.78423,0
                -70.895061,42.785852,0
                -70.894379,42.786328,0
                -70.8927757,42.7871862,0
                -70.8895402,42.7888751,0
                -70.8868909,42.7902764,0
                -70.883672,42.79232,0
                -70.8804483,42.7944181,0
                -70.878967,42.795355,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Line 15</name>
            <description>
              <![CDATA[K162<br>To be extended]]>
            </description>
            <styleUrl>#line-0000FF-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.991348,42.727927,0
                -70.991635,42.727755,0
                -70.99194,42.7276,0
                -70.992314,42.727456,0
                -70.99269,42.72733,0
                -70.993017,42.727237,0
                -70.99334,42.72716,0
                -70.99408,42.72703,0
                -70.99489,42.72696,0
                -70.99596,42.72703,0
                -70.99658,42.72715,0
                -70.99704,42.72729,0
                -70.99771,42.72761,0
                -70.99869,42.72834,0
                -71.00162,42.73082,0
                -71.00368,42.73238,0
                -71.00417,42.73268,0
                -71.00913,42.73509,0
                -71.00959,42.73538,0
                -71.02228,42.74485,0
                -71.02358,42.74554,0
                -71.02587,42.74653,0
                -71.0273,42.74734,0
                -71.02825,42.74803,0
                -71.03147,42.75063,0
                -71.03225,42.7512,0
                -71.03314,42.7517,0
                -71.03426,42.75222,0
                -71.03517,42.75252,0
                -71.0362,42.75278,0
                -71.0453049,42.75461,0
                -71.04663,42.75495,0
                -71.04811,42.75546,0
                -71.05727,42.759,0
                -71.05907,42.75998,0
                -71.06077,42.76137,0
                -71.06199,42.76265,0
                -71.06663,42.76793,0
                -71.06906,42.77055,0
                -71.07046,42.77162,0
                -71.07143,42.77214,0
                -71.07244,42.77255,0
                -71.07352,42.77284,0
                -71.07468,42.77293,0
                -71.07563,42.77286,0
                -71.07759,42.7726,0
                -71.07837,42.77246,0
                -71.07914,42.77224,0
                -71.08245,42.77104,0
                -71.08394,42.77043,0
                -71.08481,42.76997,0
                -71.08536,42.76958,0
                -71.08578,42.76919,0
                -71.08712,42.76777,0
                -71.08839,42.7668,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Line 3</name>
            <description>
              <![CDATA[K167<br>Northernmost portion (within Newburyport) is very vague.]]>
            </description>
            <styleUrl>#line-0000FF-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.99134,42.72792,0
                -70.990678,42.728449,0
                -70.990356,42.72879,0
                -70.990214,42.728969,0
                -70.989594,42.729777,0
                -70.988836,42.730777,0
                -70.988591,42.731095,0
                -70.988358,42.731387,0
                -70.988089,42.73168,0
                -70.987808,42.73197,0
                -70.987526,42.73222,0
                -70.987212,42.732468,0
                -70.982159,42.73592,0
                -70.977412,42.739143,0
                -70.974802,42.740918,0
                -70.972179,42.74271,0
                -70.967056,42.746181,0
                -70.9619,42.749687,0
                -70.957292,42.753207,0
                -70.955002,42.754965,0
                -70.952714,42.756744,0
                -70.95192,42.75729,0
                -70.950037,42.758416,0
                -70.947548,42.759914,0
                -70.946558,42.760525,0
                -70.94559,42.761159,0
                -70.943474,42.76263,0
                -70.941371,42.764121,0
                -70.938962,42.76582,0
                -70.936207,42.767775,0
                -70.935942,42.767949,0
                -70.935652,42.768118,0
                -70.93529,42.768293,0
                -70.934901,42.768466,0
                -70.934464,42.76864,0
                -70.932501,42.769392,0
                -70.928464,42.770825,0
                -70.924679,42.772123,0
                -70.92391,42.772394,0
                -70.923164,42.772648,0
                -70.922719,42.77278,0
                -70.92226,42.77291,0
                -70.919414,42.773578,0
                -70.915992,42.774387,0
                -70.915168,42.774602,0
                -70.914677,42.774728,0
                -70.914189,42.774875,0
                -70.913583,42.775092,0
                -70.912921,42.775344,0
                -70.912489,42.775527,0
                -70.91206,42.77573,0
                -70.911228,42.776155,0
                -70.908135,42.77785,0
                -70.906126,42.778951,0
                -70.9046,42.77979,0
                -70.903447,42.780431,0
                -70.90216,42.781138,0
                -70.901264,42.781655,0
                -70.899719,42.782642,0
                -70.898321,42.783581,0
                -70.897074,42.784431,0
                -70.895913,42.785246,0
                -70.895057,42.7859,0
                -70.894714,42.786146,0
                -70.894566,42.786242,0
                -70.894311,42.786394,0
                -70.894032,42.786549,0
                -70.893383,42.786884,0
                -70.891307,42.787972,0
                -70.88961,42.78886,0
                -70.88833,42.789535,0
                -70.887673,42.789888,0
                -70.887013,42.79025,0
                -70.886157,42.790756,0
                -70.882791,42.792929,0
                -70.881118,42.793997,0
                -70.880249,42.79455,0
                -70.87933,42.79516,0
                -70.87875,42.79572,0
                -70.87827,42.79639,0
                -70.87523,42.80168,0
                -70.87454,42.80297,0
                -70.87406,42.80473,0
                -70.87396,42.80717,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Line 4</name>
            <description>K286</description>
            <styleUrl>#line-0000FF-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.879586,42.794966,0
                -70.878755,42.795499,0
                -70.878264,42.795818,0
                -70.877746,42.796147,0
                -70.877282,42.796432,0
                -70.876807,42.796688,0
                -70.876292,42.796908,0
                -70.875767,42.797111,0
                -70.873758,42.797786,0
                -70.87151,42.79855,0
                -70.870292,42.798989,0
                -70.869077,42.799447,0
                -70.868828,42.799542,0
                -70.868578,42.799654,0
                -70.868342,42.79978,0
                -70.868082,42.799921,0
                -70.867629,42.800173,0
                -70.867398,42.800311,0
                -70.867184,42.800455,0
                -70.865145,42.801653,0
                -70.863082,42.802828,0
                -70.861929,42.803544,0
                -70.861532,42.803871,0
                -70.861355,42.804028,0
                -70.86118,42.80422,0
                -70.860856,42.804607,0
                -70.860711,42.8048,0
                -70.860593,42.804981,0
                -70.860497,42.805162,0
                -70.860411,42.805351,0
                -70.860373,42.805485,0
                -70.860357,42.805575,0
                -70.860309,42.8058,0
                -70.860234,42.806512,0
                -70.860143,42.807382,0
                -70.860132,42.807693,0
                -70.860148,42.80785,0
                -70.860175,42.808003,0
                -70.86032,42.808429,0
                -70.860448,42.808696,0
                -70.860631,42.809015,0
                -70.860824,42.809251,0
                -70.861044,42.809475,0
                -70.861296,42.809692,0
                -70.861575,42.809893,0
                -70.861892,42.810085,0
                -70.862267,42.810274,0
                -70.863753,42.8109,0
                -70.864418,42.811156,0
                -70.864751,42.811278,0
                -70.86525,42.811435,0
                -70.865512,42.811537,0
                -70.86576,42.81164,0
                -70.86678,42.81211,0
                -70.867181,42.812222,0
                -70.86739,42.812258,0
                -70.86761,42.812285,0
                -70.86783,42.812289,0
                -70.868028,42.812277,0
                -70.86845,42.81223,0
                -70.86924,42.81209,0
                -70.86982,42.81195,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Essex Branch</name>
            <description>
              <![CDATA[K173<br>K53<br>Very approximate]]>
            </description>
            <styleUrl>#line-FFFF00-2000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.781307,42.632011,0
                -70.79298,42.625377,0
                -70.815554,42.619345,0
                -70.817742,42.616566,0
                -70.822806,42.613313,0
                -70.827312,42.612176,0
                -70.83847,42.613187,0
                -70.845895,42.615934,0
                -70.850873,42.615934,0
                -70.873747,42.60987,0
                -70.874391,42.60946,0
                -70.874863,42.60867,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Line 19</name>
            <description>K345</description>
            <styleUrl>#line-FFFF00-2000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.877137,42.795281,0
                -70.877995,42.798241,0
                -70.879111,42.80161,0
                -70.879369,42.803909,0
                -70.87924,42.806081,0
                -70.878811,42.808474,0
                -70.877438,42.810709,0
                -70.875549,42.813197,0
                -70.872073,42.817635,0
                -70.869327,42.821287,0
                -70.867953,42.823333,0
                -70.867224,42.825756,0
                -70.866923,42.829219,0
                -70.866537,42.834537,0
                -70.866451,42.837433,0
                -70.865893,42.842814,0
                -70.865507,42.846999,0
                -70.865207,42.851183,0
                -70.864949,42.8565,0
                -70.864391,42.862194,0
                -70.863833,42.867699,0
                -70.863705,42.872637,0
                -70.863018,42.877071,0
                -70.862761,42.880122,0
                -70.862632,42.884178,0
                -70.862246,42.887417,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Amesbury Branch</name>
            <description>K344</description>
            <styleUrl>#line-FFFF00-2000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.86615,42.84023,0
                -70.86636,42.84231,0
                -70.86739,42.84385,0
                -70.86898,42.84485,0
                -70.87104,42.84514,0
                -70.87503,42.84536,0
                -70.88237,42.84583,0
                -70.88893,42.84611,0
                -70.90044,42.84677,0
                -70.905933,42.847142,0
                -70.91472,42.847582,0
                -70.9164,42.84769,0
                -70.917327,42.847842,0
                -70.918121,42.848015,0
                -70.91884,42.848204,0
                -70.91949,42.84844,0
                -70.920374,42.848762,0
                -70.921125,42.849092,0
                -70.92193,42.849619,0
                -70.922209,42.849879,0
                -70.922467,42.85017,0
                -70.922831,42.850603,0
                -70.923132,42.851035,0
                -70.923282,42.851594,0
                -70.923389,42.85216,0
                -70.923314,42.853175,0
                -70.923325,42.853576,0
                -70.92335,42.85407,0
                -70.923636,42.854921,0
                -70.923904,42.855322,0
                -70.92417,42.85565,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>West Amesbury Branch RR</name>
            <description>
              <![CDATA[K293<br>Newton Jct (NH) to Merrimac (formerly W. Amesbury)]]>
            </description>
            <styleUrl>#line-FFFF00-2000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.066093,42.870328,0
                -71.063004,42.871712,0
                -71.058025,42.87146,0
                -71.040001,42.866302,0
                -71.032448,42.863911,0
                -71.030045,42.862276,0
                -71.02129,42.860011,0
                -71.016827,42.857368,0
                -71.014767,42.852964,0
                -71.014767,42.849566,0
                -71.01099,42.846168,0
                -71.006355,42.841511,0
                -71.00275,42.836476,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>
              <![CDATA[Andover & Wilmington RR]]>
            </name>
            <description>
              <![CDATA[K1A<br>Abandoned when line to (new) Lawrence built.  Not even shown on early US topo maps.  Follows Waverly Rd mostly.  Approximate.]]>
            </description>
            <styleUrl>#line-FFFF00-2000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.132698,42.708096,0
                -71.133106,42.706921,0
                -71.133213,42.704304,0
                -71.133664,42.703184,0
                -71.134361,42.701521,0
                -71.135488,42.698911,0
                -71.135659,42.697357,0
                -71.135488,42.695299,0
                -71.135155,42.690915,0
                -71.134973,42.688833,0
                -71.134801,42.687216,0
                -71.13435,42.685742,0
                -71.133213,42.68254,0
                -71.13332,42.68026,0
                -71.133696,42.678864,0
                -71.134447,42.67649,0
                -71.135069,42.674723,0
                -71.135434,42.673674,0
                -71.136174,42.67227,0
                -71.137086,42.671008,0
                -71.138223,42.669288,0
                -71.139382,42.667726,0
                -71.140326,42.666409,0
                -71.140788,42.665272,0
                -71.141453,42.663781,0
                -71.142257,42.662369,0
                -71.143169,42.660594,0
                -71.144221,42.658369,0
                -71.145197,42.656097,0
                -71.146238,42.654353,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Eastern RR Swampscott Branch</name>
            <description>k226</description>
            <styleUrl>#line-663366-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.92131,42.47447,0
                -70.92053,42.47498,0
                -70.920329,42.475088,0
                -70.920208,42.47514,0
                -70.920112,42.475175,0
                -70.919948,42.475227,0
                -70.919618,42.475322,0
                -70.919449,42.475365,0
                -70.919272,42.475407,0
                -70.919052,42.47545,0
                -70.918827,42.475488,0
                -70.918601,42.475512,0
                -70.918376,42.475527,0
                -70.918183,42.475537,0
                -70.917987,42.475541,0
                -70.917786,42.475541,0
                -70.917582,42.475539,0
                -70.917349,42.475527,0
                -70.917107,42.47551,0
                -70.916845,42.475488,0
                -70.916617,42.475456,0
                -70.916322,42.475407,0
                -70.915951,42.475341,0
                -70.912572,42.47474,0
                -70.911891,42.474635,0
                -70.911182,42.474538,0
                -70.910241,42.474394,0
                -70.91001,42.47436,0
                -70.9098,42.47433,0
                -70.909595,42.474313,0
                -70.90938,42.4743,0
                -70.90919,42.474299,0
                -70.908996,42.474305,0
                -70.908806,42.474321,0
                -70.908605,42.474344,0
                -70.908412,42.474376,0
                -70.908189,42.474422,0
                -70.907889,42.474503,0
                -70.907551,42.474604,0
                -70.907213,42.474714,0
                -70.907063,42.474768,0
                -70.906915,42.474809,0
                -70.906717,42.474849,0
                -70.906523,42.474878,0
                -70.90633,42.4749,0
                -70.90613,42.47492,0
                -70.90588,42.474928,0
                -70.90561,42.47493,0
                -70.905161,42.474912,0
                -70.904541,42.474882,0
                -70.90428,42.47487,0
                -70.903919,42.474871,0
                -70.903527,42.47488,0
                -70.903101,42.4749,0
                -70.90275,42.47491,0
                -70.902312,42.474908,0
                -70.90186,42.47489,0
                -70.901572,42.474863,0
                -70.901256,42.474817,0
                -70.899858,42.474609,0
                -70.899504,42.474554,0
                -70.89915,42.47451,0
                -70.89904,42.474499,0
                -70.898919,42.474493,0
                -70.898815,42.474487,0
                -70.898686,42.474489,0
                -70.898431,42.474501,0
                -70.898155,42.474518,0
                -70.897109,42.474602,0
                -70.89607,42.47469,0
                -70.895561,42.474744,0
                -70.895293,42.474776,0
                -70.89504,42.47481,0
                -70.894934,42.474827,0
                -70.894816,42.474853,0
                -70.894601,42.474914,0
                -70.894126,42.475051,0
                -70.893587,42.475207,0
                -70.893177,42.475345,0
                -70.892994,42.475415,0
                -70.892584,42.475587,0
                -70.891989,42.475854,0
                -70.89079,42.4764,0
                -70.890543,42.476524,0
                -70.890291,42.476661,0
                -70.890041,42.476801,0
                -70.8898,42.47695,0
                -70.889634,42.477065,0
                -70.889481,42.477177,0
                -70.88916,42.47742,0
                -70.888883,42.47766,0
                -70.88862,42.4779,0
                -70.888432,42.478107,0
                -70.88825,42.478333,0
                -70.887853,42.478851,0
                -70.887681,42.479045,0
                -70.887507,42.479227,0
                -70.887324,42.479399,0
                -70.887177,42.479519,0
                -70.887013,42.479642,0
                -70.88668,42.47988,0
                -70.886375,42.480077,0
                -70.886047,42.480279,0
                -70.88579,42.480394,0
                -70.885565,42.480495,0
                -70.885227,42.480631,0
                -70.88492,42.48075,0
                -70.884752,42.480811,0
                -70.884645,42.480849,0
                -70.884508,42.480888,0
                -70.884358,42.480924,0
                -70.884111,42.480969,0
                -70.883843,42.481017,0
                -70.883601,42.481058,0
                -70.883336,42.481096,0
                -70.88094,42.48144,0
                -70.880466,42.481537,0
                -70.879996,42.481654,0
                -70.879787,42.481721,0
                -70.879575,42.481798,0
                -70.879406,42.481862,0
                -70.879232,42.481933,0
                -70.87905,42.48201,0
                -70.87887,42.48209,0
                -70.878685,42.482186,0
                -70.878478,42.482313,0
                -70.878264,42.482447,0
                -70.87805,42.48259,0
                -70.877861,42.482732,0
                -70.877671,42.482886,0
                -70.877497,42.483037,0
                -70.87731,42.48321,0
                -70.877135,42.483387,0
                -70.87696,42.483585,0
                -70.876609,42.483984,0
                -70.87589,42.484801,0
                -70.874645,42.486186,0
                -70.873731,42.487216,0
                -70.873656,42.487299,0
                -70.873581,42.487376,0
                -70.873452,42.487495,0
                -70.873323,42.487608,0
                -70.87306,42.48783,0
                -70.8725,42.48824,0
                -70.872197,42.488433,0
                -70.87188,42.48861,0
                -70.871682,42.488715,0
                -70.871427,42.488834,0
                -70.870112,42.489455,0
                -70.869246,42.489859,0
                -70.868369,42.49028,0
                -70.867693,42.49061,0
                -70.867548,42.490681,0
                -70.8674,42.49076,0
                -70.867275,42.490842,0
                -70.867127,42.49095,0
                -70.86685,42.49116,0
                -70.866666,42.49134,0
                -70.86651,42.49151,0
                -70.866368,42.491708,0
                -70.866245,42.49191,0
                -70.86534,42.49373,0
                -70.86515,42.49415,0
                -70.865073,42.494342,0
                -70.86502,42.49453,0
                -70.864989,42.494661,0
                -70.86497,42.49479,0
                -70.864957,42.494904,0
                -70.864944,42.495159,0
                -70.864882,42.497089,0
                -70.86482,42.498191,0
                -70.864807,42.498315,0
                -70.86478,42.498446,0
                -70.864751,42.498559,0
                -70.864716,42.498667,0
                -70.86467,42.498786,0
                -70.864619,42.498913,0
                -70.864574,42.499011,0
                -70.864512,42.499128,0
                -70.864448,42.499235,0
                -70.864391,42.49931,0
                -70.864338,42.499377,0
                -70.864271,42.499458,0
                -70.864241,42.499496,0
                -70.864204,42.499535,0
                -70.86412,42.49962,0
                -70.864016,42.499721,0
                -70.863935,42.499795,0
                -70.863855,42.49986,0
                -70.863764,42.499925,0
                -70.863675,42.49998,0
                -70.863587,42.500034,0
                -70.863485,42.500091,0
                -70.8633,42.500204,0
                -70.863101,42.500307,0
                -70.862696,42.500477,0
                -70.862259,42.500623,0
                -70.861811,42.50076,0
                -70.861138,42.500959,0
                -70.86049,42.50118,0
                -70.85642,42.50234,0
                -70.85573,42.50288,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Eastern RR Marblehead Branch</name>
            <description>K235</description>
            <styleUrl>#line-6600CC-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.89692,42.50945,0
                -70.8982,42.50748,0
                -70.89769,42.50508,0
                -70.8964,42.50261,0
                -70.89503,42.50204,0
                -70.89209,42.49939,0
                -70.89119,42.49852,0
                -70.88992,42.4979,0
                -70.88934,42.4977,0
                -70.88801,42.49744,0
                -70.88722,42.49736,0
                -70.88606,42.49748,0
                -70.88533,42.49772,0
                -70.88441,42.49813,0
                -70.88378,42.49829,0
                -70.88323,42.49834,0
                -70.88245,42.4983,0
                -70.88191,42.4982,0
                -70.88151,42.49804,0
                -70.88101,42.4977,0
                -70.88009,42.49695,0
                -70.87957,42.49667,0
                -70.87915,42.49653,0
                -70.87886,42.49648,0
                -70.87791,42.49646,0
                -70.87759,42.4965,0
                -70.87712,42.49663,0
                -70.87012,42.49921,0
                -70.86945,42.49942,0
                -70.86906,42.49951,0
                -70.86834,42.49957,0
                -70.86611,42.49963,0
                -70.86537,42.4997,0
                -70.86451,42.49992,0
                -70.861999,42.500704,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>Lowell and Andover R/R</name>
            <description>
              <![CDATA[Taken from J.B. Beers & Co. 1875 Atlas of Massachusetts]]>
            </description>
            <styleUrl>#line-FFCC00-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.294535,42.625801,0
                -71.295115,42.625833,0
                -71.295565,42.626022,0
                -71.296209,42.626338,0
                -71.29666,42.626622,0
                -71.297067,42.626907,0
                -71.297303,42.627128,0
                -71.297582,42.627538,0
                -71.297668,42.628343,0
                -71.297604,42.62888,0
                -71.297561,42.629385,0
                -71.297454,42.629985,0
                -71.297539,42.630459,0
                -71.297647,42.630901,0
                -71.297883,42.631327,0
                -71.298076,42.631706,0
                -71.298398,42.632085,0
                -71.29887,42.63259,0
                -71.299278,42.633127,0
                -71.299707,42.633664,0
                -71.300136,42.634153,0
                -71.300286,42.634753,0
                -71.300479,42.635321,0
                -71.300629,42.635937,0
                -71.301552,42.638084,0
                -71.302196,42.639615,0
                -71.302389,42.640293,0
                -71.302603,42.640562,0
                -71.302968,42.64083,0
                -71.30344,42.641114,0
                -71.303998,42.641367,0
                -71.304556,42.641604,0
                -71.305157,42.641761,0
                -71.305715,42.641903,0
                -71.306359,42.642014,0
                -71.306916,42.642046,0
                -71.307603,42.642093,0
                -71.308419,42.64214,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>South Chelmsford</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.380341,42.571137,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>Graniteville</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.46909,42.592885,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>Passenger Depot</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.475409,42.590934,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>East Pepperell</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.573583,42.666482,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>Billerica Station</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.237175,42.580025,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>Stoneham</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.097293,42.478238,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>Greenwood</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.067907,42.484204,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>Wakefield Jct</name>
            <styleUrl>#icon-22-nodesc</styleUrl>
            <Point>
              <coordinates>
                -71.070262,42.495477,0
              </coordinates>
            </Point>
          </Placemark>
          <Placemark>
            <name>Newburyport RR</name>
            <description>RDK1 54 Section 1: Wakefield Junction Jct to Danvers Jct. The remaining section(s) of this railroad have been mistakenly mapped as part of another line. Need to fix!</description>
            <styleUrl>#line-FF0000-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -70.938829,42.567314,0
                -70.939997,42.566606,0
                -70.94076,42.566148,0
                -70.941167,42.565871,0
                -70.941559,42.565591,0
                -70.941821,42.565385,0
                -70.942069,42.565192,0
                -70.94232,42.564994,0
                -70.942552,42.564797,0
                -70.942739,42.56463,0
                -70.942906,42.564461,0
                -70.943077,42.564274,0
                -70.943222,42.56411,0
                -70.94355,42.563699,0
                -70.944161,42.562909,0
                -70.944767,42.562122,0
                -70.945255,42.561514,0
                -70.945829,42.560945,0
                -70.946221,42.560617,0
                -70.94651,42.560406,0
                -70.946826,42.560229,0
                -70.947116,42.560074,0
                -70.947368,42.559956,0
                -70.947781,42.559778,0
                -70.948087,42.55966,0
                -70.948371,42.559577,0
                -70.94857,42.559517,0
                -70.948725,42.559482,0
                -70.948929,42.559434,0
                -70.949102,42.559408,0
                -70.949321,42.559371,0
                -70.94953,42.559343,0
                -70.949852,42.559308,0
                -70.950218,42.559273,0
                -70.95275,42.559052,0
                -70.956011,42.558791,0
                -70.95894,42.558554,0
                -70.960146,42.55845,0
                -70.960597,42.558407,0
                -70.960995,42.558361,0
                -70.961434,42.558296,0
                -70.961847,42.558233,0
                -70.962281,42.55815,0
                -70.962625,42.55808,0
                -70.963209,42.557945,0
                -70.963795,42.557792,0
                -70.964416,42.557589,0
                -70.965056,42.557365,0
                -70.96557,42.557162,0
                -70.966043,42.556962,0
                -70.966482,42.556751,0
                -70.966927,42.556518,0
                -70.96821,42.55584,0
                -70.969814,42.555002,0
                -70.971895,42.553899,0
                -70.974615,42.552469,0
                -70.975907,42.551764,0
                -70.976529,42.551421,0
                -70.977135,42.551081,0
                -70.977532,42.55084,0
                -70.977919,42.5506,0
                -70.978235,42.550369,0
                -70.978895,42.549828,0
                -70.980049,42.548916,0
                -70.982661,42.546848,0
                -70.982989,42.546608,0
                -70.983358,42.54639,0
                -70.983825,42.546133,0
                -70.984259,42.545904,0
                -70.984731,42.545694,0
                -70.985155,42.545528,0
                -70.986014,42.545217,0
                -70.989211,42.544193,0
                -70.993734,42.542763,0
                -70.997209,42.541611,0
                -70.997687,42.541462,0
                -70.997906,42.541406,0
                -70.998094,42.541366,0
                -70.998411,42.541303,0
                -70.998754,42.541244,0
                -70.999639,42.541105,0
                -71.000546,42.540967,0
                -71.002429,42.540663,0
                -71.00289,42.540584,0
                -71.003469,42.540457,0
                -71.003996,42.54032,0
                -71.004413,42.540185,0
                -71.005911,42.539648,0
                -71.009006,42.538462,0
                -71.012214,42.537213,0
                -71.015507,42.535951,0
                -71.015888,42.535805,0
                -71.016237,42.535684,0
                -71.016945,42.535516,0
                -71.017321,42.535439,0
                -71.017733,42.535346,0
                -71.017942,42.535315,0
                -71.018119,42.535291,0
                -71.018366,42.535267,0
                -71.018592,42.53526,0
                -71.019026,42.53526,0
                -71.019498,42.535267,0
                -71.019943,42.535287,0
                -71.02041,42.535319,0
                -71.020835,42.535352,0
                -71.024171,42.535628,0
                -71.027889,42.535933,0
                -71.031875,42.536261,0
                -71.036215,42.536632,0
                -71.039819,42.536897,0
                -71.040398,42.536924,0
                -71.04094,42.536947,0
                -71.041481,42.536947,0
                -71.04203,42.536937,0
                -71.042404,42.536904,0
                -71.042758,42.536868,0
                -71.043118,42.536825,0
                -71.043456,42.536773,0
                -71.04381,42.536714,0
                -71.044169,42.536643,0
                -71.044857,42.53649,0
                -71.045564,42.536299,0
                -71.046251,42.536091,0
                -71.046894,42.535864,0
                -71.047544,42.535601,0
                -71.048117,42.535327,0
                -71.048687,42.535043,0
                -71.04933,42.534655,0
                -71.049952,42.534259,0
                -71.050327,42.533971,0
                -71.050783,42.533603,0
                -71.051529,42.532923,0
                -71.051781,42.532662,0
                -71.052006,42.532398,0
                -71.052882,42.531229,0
                -71.053627,42.530089,0
                -71.054368,42.528995,0
                -71.055874,42.526677,0
                -71.057167,42.524788,0
                -71.058595,42.522662,0
                -71.059683,42.520901,0
                -71.060891,42.519103,0
                -71.062339,42.516988,0
                -71.062805,42.516251,0
                -71.063229,42.515512,0
                -71.063352,42.515263,0
                -71.063486,42.514966,0
                -71.063572,42.514721,0
                -71.063669,42.514456,0
                -71.063712,42.514298,0
                -71.06376,42.514128,0
                -71.063808,42.513942,0
                -71.063835,42.513804,0
                -71.063915,42.513472,0
                -71.063986,42.513156,0
                -71.064404,42.510573,0
                -71.06478,42.508471,0
                -71.065172,42.50645,0
                -71.065381,42.505588,0
                -71.065482,42.505266,0
                -71.065584,42.505005,0
                -71.065804,42.504546,0
                -71.066025,42.50412,0
                -71.066303,42.503728,0
                -71.066561,42.503365,0
                -71.067371,42.502467,0
                -71.06794,42.501716,0
                -71.068363,42.500999,0
                -71.068766,42.500304,0
              </coordinates>
            </LineString>
          </Placemark>
        </Folder>
      </Document>
    </xml>
    extractAll[Seq[Container]](xml) match {
      case Success(containers) =>
        println(containers)
        containers.size shouldBe 1
        containers.head match {
          case document@Document(fs) =>
            fs.size shouldBe 1
            document.containerData.featureData.name shouldBe Text("MA - Boston NE: Historic New England Railroads")
            val feature = fs.head
            feature match {
              case placemark: Placemark =>
                placemark.featureData.name shouldBe Text("Stoneham Branch")
                placemark.featureData.maybeDescription shouldBe Text(
                  """
      K405<br>RDK1: 51B
    """)
                val ls: scala.Seq[Geometry] = placemark.Geometry
                ls.size shouldBe 1
                val geometry: Geometry = ls.head
                val coordinates: scala.Seq[Coordinates] = geometry match {
                  case lineString: LineString => lineString.coordinates
                  case _ => fail("first geometrys is not a LineString")
                }
                coordinates.size shouldBe 1
                val coordinate = coordinates.head
                coordinate.coordinates.size shouldBe 94
                val wy = TryUsing(StateR())(sr => Renderer.render[Document](document, FormatXML(), sr))
                wy.isSuccess shouldBe true
                wy.get.startsWith("<Document><name>MA - Boston NE: Historic New England Railroads</name><description>See description of Historic New England Railroads (MA - Boston NW). Full index: https://www.rubecula.com/RRMaps/</description>\n    <Style id=\"icon-22-nodesc-normal\"><IconStyle><scale>1.1</scale><Icon>".stripMargin) shouldBe true
              case _: Folder =>
            }
        }
      case Failure(x) => fail(x)
    }
  }

  behavior of "KML"

  it should "extract KML" in {
    val xml = <kml xmlns="http://www.opengis.net/kml/2.2">
      <Document>
        <name>MA - Boston NE: Historic New England Railroads</name>
        <description>See description of Historic New England Railroads (MA - Boston NW). Full index: https://www.rubecula.com/RRMaps/</description>
        <Style id="icon-22-nodesc-normal">
          <IconStyle>
            <scale>1.1</scale>
            <Icon>
              <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
            </Icon>
            <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
          </IconStyle>
          <LabelStyle>
            <scale>0</scale>
          </LabelStyle>
          <BalloonStyle>
            <text>
              <![CDATA[<h3>$[name]</h3>]]>
            </text>
          </BalloonStyle>
        </Style>
        <Style id="icon-22-nodesc-highlight">
          <IconStyle>
            <scale>1.1</scale>
            <Icon>
              <href>https://www.gstatic.com/mapspro/images/stock/22-blue-dot.png</href>
            </Icon>
            <hotSpot x="16" xunits="pixels" y="32" yunits="insetPixels"/>
          </IconStyle>
          <LabelStyle>
            <scale>1.1</scale>
          </LabelStyle>
          <BalloonStyle>
            <text>
              <![CDATA[<h3>$[name]</h3>]]>
            </text>
          </BalloonStyle>
        </Style>
        <StyleMap id="icon-22-nodesc">
          <Pair>
            <key>normal</key>
            <styleUrl>#icon-22-nodesc-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#icon-22-nodesc-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-0000FF-5000-normal">
          <LineStyle>
            <color>ffff0000</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-0000FF-5000-highlight">
          <LineStyle>
            <color>ffff0000</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-0000FF-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-0000FF-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-0000FF-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-006600-5000-normal">
          <LineStyle>
            <color>ff006600</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-006600-5000-highlight">
          <LineStyle>
            <color>ff006600</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-006600-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-006600-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-006600-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-6600CC-5000-normal">
          <LineStyle>
            <color>ffcc0066</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-6600CC-5000-highlight">
          <LineStyle>
            <color>ffcc0066</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-6600CC-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-6600CC-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-6600CC-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-663366-5000-normal">
          <LineStyle>
            <color>ff663366</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-663366-5000-highlight">
          <LineStyle>
            <color>ff663366</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-663366-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-663366-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-663366-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-993399-5000-normal">
          <LineStyle>
            <color>ff993399</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-993399-5000-highlight">
          <LineStyle>
            <color>ff993399</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-993399-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-993399-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-993399-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-CC33CC-5000-normal">
          <LineStyle>
            <color>ffcc33cc</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-CC33CC-5000-highlight">
          <LineStyle>
            <color>ffcc33cc</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-CC33CC-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-CC33CC-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-CC33CC-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-FF0000-5000-normal">
          <LineStyle>
            <color>ff0000ff</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-FF0000-5000-highlight">
          <LineStyle>
            <color>ff0000ff</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-FF0000-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-FF0000-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-FF0000-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-FFCC00-5000-normal">
          <LineStyle>
            <color>ff00ccff</color>
            <width>5</width>
          </LineStyle>
        </Style>
        <Style id="line-FFCC00-5000-highlight">
          <LineStyle>
            <color>ff00ccff</color>
            <width>7.5</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-FFCC00-5000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-FFCC00-5000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-FFCC00-5000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Style id="line-FFFF00-2000-normal">
          <LineStyle>
            <color>ff00ffff</color>
            <width>2</width>
          </LineStyle>
        </Style>
        <Style id="line-FFFF00-2000-highlight">
          <LineStyle>
            <color>ff00ffff</color>
            <width>3</width>
          </LineStyle>
        </Style>
        <StyleMap id="line-FFFF00-2000">
          <Pair>
            <key>normal</key>
            <styleUrl>#line-FFFF00-2000-normal</styleUrl>
          </Pair>
          <Pair>
            <key>highlight</key>
            <styleUrl>#line-FFFF00-2000-highlight</styleUrl>
          </Pair>
        </StyleMap>
        <Folder>
          <name>Untitled layer</name>
          <Placemark>
            <name>Stoneham Branch</name>
            <description>
              <![CDATA[K405<br>RDK1: 51B]]>
            </description>
            <styleUrl>#line-FF0000-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.126508,42.477003,0
                -71.126368,42.477585,0
                -71.126282,42.477779,0
                -71.126186,42.477949,0
                -71.126046,42.478139,0
                -71.125896,42.478305,0
                -71.125724,42.478471,0
                -71.125542,42.47861,0
                -71.125301,42.478776,0
                -71.125059,42.478902,0
                -71.124802,42.479013,0
                -71.124523,42.479108,0
                -71.124206,42.479175,0
                -71.1239,42.47923,0
                -71.1236,42.47925,0
                -71.1233,42.47925,0
                -71.122988,42.479223,0
                -71.12269,42.47918,0
                -71.121615,42.479005,0
                -71.120526,42.478823,0
                -71.118702,42.478534,0
                -71.118144,42.478447,0
                -71.117683,42.478396,0
                -71.117195,42.478368,0
                -71.116712,42.47838,0
                -71.116331,42.478416,0
                -71.115226,42.478534,0
                -71.114593,42.478602,0
                -71.114298,42.478633,0
                -71.114025,42.478645,0
                -71.113601,42.478645,0
                -71.112898,42.478625,0
                -71.112319,42.478625,0
                -71.111718,42.478641,0
                -71.111353,42.478673,0
                -71.110967,42.478728,0
                -71.110699,42.478784,0
                -71.11042,42.478855,0
                -71.110017,42.47897,0
                -71.109615,42.479108,0
                -71.10932,42.479231,0
                -71.109046,42.479365,0
                -71.108783,42.479512,0
                -71.108558,42.479666,0
                -71.107909,42.480125,0
                -71.105672,42.481691,0
                -71.10512,42.482095,0
                -71.104556,42.48253,0
                -71.104159,42.482803,0
                -71.103961,42.482957,0
                -71.103725,42.483167,0
                -71.10351,42.483397,0
                -71.103296,42.483638,0
                -71.103103,42.483875,0
                -71.102024,42.485106,0
                -71.101826,42.485295,0
                -71.101643,42.485462,0
                -71.101434,42.485604,0
                -71.101198,42.485758,0
                -71.100892,42.485901,0
                -71.100565,42.486023,0
                -71.100297,42.486103,0
                -71.100007,42.48617,0
                -71.099734,42.486221,0
                -71.09944,42.48627,0
                -71.0992403,42.4862899,0
                -71.0990166,42.4862938,0
                -71.0987616,42.4862828,0
                -71.0985082,42.4862587,0
                -71.0981516,42.4861877,0
                -71.0977833,42.4860848,0
                -71.097507,42.485972,0
                -71.097229,42.485849,0
                -71.096896,42.485683,0
                -71.096574,42.485462,0
                -71.096268,42.485197,0
                -71.09592,42.484852,0
                -71.095571,42.484453,0
                -71.095233,42.484053,0
                -71.095056,42.483788,0
                -71.094906,42.483531,0
                -71.09482,42.483353,0
                -71.094734,42.483151,0
                -71.094659,42.482918,0
                -71.094605,42.482704,0
                -71.094589,42.482459,0
                -71.094584,42.48217,0
                -71.0946,42.481949,0
                -71.094659,42.481707,0
                -71.094761,42.48145,0
                -71.094906,42.481205,0
                -71.095292,42.480651,0
                -71.095694,42.480145,0
                -71.09744,42.47812,0
              </coordinates>
            </LineString>
          </Placemark>
          <Placemark>
            <name>
              <![CDATA[Saugus B&M connector]]>
            </name>
            <description>
              <![CDATA[Saugus Branch connection with Boston & Maine (1853-55).]]>
            </description>
            <styleUrl>#line-006600-5000</styleUrl>
            <LineString>
              <tessellate>1</tessellate>
              <coordinates>
                -71.07677,42.41874,0
                -71.07668,42.41925,0
                -71.07658,42.41941,0
                -71.07613,42.41978,0
                -71.07578,42.41998,0
                -71.074778,42.420487,0
                -71.073893,42.42091,0
                -71.073352,42.421164,0
                -71.071056,42.422272,0
                -71.070852,42.422379,0
                -71.070712,42.42247,0
                -71.070546,42.422597,0
                -71.070278,42.422807,0
                -71.070154,42.422918,0
                -71.069967,42.422993,0
                -71.069747,42.423072,0
                -71.06827,42.42363,0
              </coordinates>
            </LineString>
          </Placemark>
        </Folder>
      </Document>
    </kml>
    extractAll[Seq[Container]](xml) match {
      case Success(containers) =>
        containers.size shouldBe 1
        containers.head match {
          case document@Document(features) =>
            document.containerData.featureData match {
              case FeatureData(name, maybeDescription, maybeStyleUrl, maybeOpen, _, styleSelectors, _) =>
                name shouldBe Text("MA - Boston NE: Historic New England Railroads")
                maybeDescription shouldBe Some(Text("See description of Historic New England Railroads (MA - Boston NW). Full index: https://www.rubecula.com/RRMaps/"))
                maybeStyleUrl shouldBe None
                maybeOpen shouldBe None
                styleSelectors.size shouldBe 30
            }
            features.size shouldBe 1
            features.head match {
              case folder@Folder(features) =>
                folder.containerData.featureData.name shouldBe Text("Untitled layer")
                println(features)
                features.size shouldBe 2
                features.head match {
                  case placemark@Placemark(geometry) =>
                    placemark.featureData.name shouldBe Text("Stoneham Branch")
                    geometry.size shouldBe 1
                    geometry.head match {
                      case Point(cs) =>
                        cs.size shouldBe 1
                        cs.head.coordinates.size shouldBe 1
                      case LineString(tessellate, coordinates) =>
                        tessellate shouldBe Tessellate(true)
                        coordinates.size shouldBe 1
                        coordinates.head.coordinates.size shouldBe 94
                    }
                  case _ => fail("should be placemark")
                }
                features.last match {
                  case placemark@Placemark(geometry) =>
                    val expected = Text(CDATA.wrapped("""Saugus B&M connector"""))
                    placemark.featureData.name shouldBe expected
                    geometry.size shouldBe 1
                    geometry.head match {
                      case Point(cs) =>
                        cs.size shouldBe 1
                        cs.head.coordinates.size shouldBe 1
                      case LineString(tessellate, coordinates) =>
                        tessellate shouldBe Tessellate(true)
                        coordinates.size shouldBe 1
                        coordinates.head.coordinates.size shouldBe 17
                    }
                  case _ => fail("should be placemark")
                }
            }
        }
      case Failure(x) => fail(x)
    }
  }

  it should "extract KmlFromFile" in {
    val url = KML.getClass.getResource("sample.kml")
    val xml = XML.loadFile(url.getFile)
    extractAll[Seq[Container]](xml) match {
      case Success(containers) =>
        containers.size shouldBe 1
        containers.head match {
          case document@Document(features) =>
            document.containerData.featureData match {
              case FeatureData(name, maybeDescription, maybeStyleUrl, maybeOpen, _, styleSelectors, _) =>
                name shouldBe Text("MA - Boston NE: Historic New England Railroads")
                maybeDescription shouldBe Some(Text("See description of Historic New England Railroads (MA - Boston NW).  Full index: https://www.rubecula.com/RRMaps/"))
                maybeStyleUrl shouldBe None
                maybeOpen shouldBe None
                styleSelectors.size shouldBe 30
            }
            features.size shouldBe 1
            features.head match {
              case folder@Folder(features) =>
                folder.containerData.featureData.name shouldBe Text("MA-Boston NE")
                println(features)
                features.size shouldBe 34
                features.head match {
                  case placemark@Placemark(geometry) =>
                    placemark.featureData.name shouldBe Text("Stoneham Branch")
                    geometry.size shouldBe 1
                    geometry.head match {
                      case Point(cs) =>
                        cs.size shouldBe 1
                        cs.head.coordinates.size shouldBe 1
                      case LineString(tessellate, coordinates) =>
                        tessellate shouldBe Tessellate(true)
                        coordinates.size shouldBe 1
                        coordinates.head.coordinates.size shouldBe 94
                    }
                  case _ => fail("should be placemark")
                }
                features.last match {
                  case placemark@Placemark(geometry) =>
                    placemark.featureData.name shouldBe Text("Newburyport RR")
                    geometry.size shouldBe 1
                    geometry.head match {
                      case Point(cs) =>
                        cs.size shouldBe 1
                        cs.head.coordinates.size shouldBe 1
                      case LineString(tessellate, coordinates) =>
                        tessellate shouldBe Tessellate(true)
                        coordinates.size shouldBe 1
                        coordinates.head.coordinates.size shouldBe 169
                    }
                  case _ => fail("should be placemark")
                }
            }
        }
      case Failure(x) => fail(x)
    }
  }

  it should "extract and get debug version of sample Kml from file" in {
    val url = KML.getClass.getResource("sample.kml")
    val xml = XML.loadFile(url.getFile)
    extractMulti[Seq[KML]](xml) match {
      case Success(ks) =>
        ks.size shouldBe 1
        val kml: KML = ks.head
        val w = kml.toString
        println(w)
        w.length shouldBe 88
      case Failure(x) => fail(x)
    }
  }

  it should "extract and get debug string from mini sample Kml from file" in {
    val url = KML.getClass.getResource("minisample.kml")
    val xml = XML.loadFile(url.getFile)
    extractMulti[Seq[KML]](xml) match {
      case Success(ks) =>
        ks.size shouldBe 1
        val kml: KML = ks.head
        val w = kml.toString
        println(w)
        w.length shouldBe 88
      case Failure(x) => fail(x)
    }
  }

  it should "extract and render mini sample kml as XML from file" in {
    val url = KML.getClass.getResource("minisample.kml")
    val xml: Elem = XML.loadFile(url.getFile)
    extractMulti[Seq[KML]](xml) match {
      case Success(ks) =>
        ks.size shouldBe 1
        val kml = KML_Binding(ks.head, xml.scope)
        val ksy: Try[Seq[KML]] = TryUsing(new FileWriter("miniSampleOutput.kml")) {
          fw =>
            fw.write(
              """<?xml version="1.0" encoding="UTF-8"?>
                |""".stripMargin)
            for {w <- Renderer.render(kml, FormatXML(0), StateR().setName("kml"))
                 _ = fw.write(w)
                 ks <- extractMulti[Seq[KML]](parseUnparsed(w))
                 } yield ks
        }
        ksy should matchPattern { case Success(_ :: Nil) => }
      case Failure(x) => fail(x)
    }
  }

  it should "extract and render sample kml as XML from file" in {
    val url = KML.getClass.getResource("sample.kml")
    val xml: Elem = XML.loadFile(url.getFile)
    extractMulti[Seq[KML]](xml) match {
      case Success(ks) =>
        ks.size shouldBe 1
        val kml = KML_Binding(ks.head, xml.scope)
        val ksy: Try[Seq[KML]] = TryUsing(new FileWriter("xmlOutput.kml")) {
          fw =>
            fw.write(
              """<?xml version="1.0" encoding="UTF-8"?>
                |""".stripMargin)
            for {w <- Renderer.render(kml, FormatXML(), StateR().setName("kml"))
                 _ = fw.write(w)
                 ks <- extractMulti[Seq[KML]](parseUnparsed(w))
                 } yield ks
        }
        ksy should matchPattern { case Success(_ :: Nil) => }
      case Failure(x) => fail(x)
    }
  }

  it should "extract and render sample kml as XML from Google sample" in {
    val url = KML.getClass.getResource("/KML_Samples.kml")
    val xml: Elem = XML.loadFile(url.getFile)
    extractMulti[Seq[KML]](xml) match {
      case Success(ks) =>
        ks.size shouldBe 1
        val kml = KML_Binding(ks.head, xml.scope)
        val filename = "KML_Samples_output.kml"
        val fw = new FileWriter(filename)
        fw.write(
          """<?xml version="1.0" encoding="UTF-8"?>
            |""".stripMargin)
        Renderer.render(kml, FormatXML(), StateR().setName("kml")) match {
          case Success(w) =>
            fw.write(w)
            fw.close()
            val copy: Elem = parseUnparsed(w)
            val ksy: Try[scala.Seq[KML]] = extractMulti[Seq[KML]](copy)
            ksy should matchPattern { case Success(_ :: Nil) => }
          case Failure(x) =>
            x.printStackTrace()
            fail("see exception above")
        }
      case Failure(x) => fail(x)
    }
  }

  it should "extract and render placemarks without descriptor" in {
    val url = KML.getClass.getResource("/emptyDescriptor.kml")
    val xml: Elem = XML.loadFile(url.getFile)
    extractMulti[Seq[KML]](xml) match {
      case Success(ks) =>
        ks.size shouldBe 0
      //        val kml = KML_Binding(ks.head, xml.scope)
      //        val filename = "emptyDescriptor_out.kml"
      //        val fw = new FileWriter(filename)
      //        fw.write(
      //          """<?xml version="1.0" encoding="UTF-8"?>
      //            |""".stripMargin)
      //        Renderer.render(kml, FormatXML(), StateR().setName("kml")) match {
      //          case Success(w) =>
      //            fw.write(w)
      //            fw.close()
      //            val copy: Elem = parseUnparsed(w)
      //            val ksy: Try[scala.Seq[KML]] = extractMulti[Seq[KML]](copy)
      //            ksy should matchPattern { case Success(_ :: Nil) => }
      //          case Failure(x) =>
      //            x.printStackTrace()
      //            fail("see exception above")
      //        }
      case Failure(x) => fail(x)
    }
  }

  behavior of "merge"

  val kd: KmlData = KmlData(None)
  val gd: GeometryData = GeometryData(None, None)(kd)
  val fd1: FeatureData = FeatureData(Text("junk"), None, None, None, None, Nil, Nil)(kd)
  val fd2: FeatureData = FeatureData(Text("junk junk"), None, None, None, None, Nil, Nil)(kd)
  val cs1: Seq[Coordinate] = Seq(Coordinate("1", "0", "0"), Coordinate("1", "1", "0"))
  val cs2: Seq[Coordinate] = Seq(Coordinate("1", "1", "0"), Coordinate("1", "2", "0"))
  val cs2a: Seq[Coordinate] = cs2.reverse
  val coordinates1: Seq[Coordinates] = Seq(Coordinates(cs1))
  val coordinates2: Seq[Coordinates] = Seq(Coordinates(cs2))
  val coordinates2a: Seq[Coordinates] = Seq(Coordinates(cs2a))
  val tessellate: Tessellate = Tessellate(true)
  val p1: Placemark = Placemark(Seq(LineString(tessellate, coordinates1)(gd)))(fd1)
  val p2: Placemark = Placemark(Seq(LineString(tessellate, coordinates2)(gd)))(fd1)
  val p2a: Placemark = Placemark(Seq(LineString(tessellate, coordinates2a)(gd)))(fd1)
  val p12: Placemark = Placemark(Seq(LineString(tessellate, Seq(Coordinates(cs1 ++ cs2)))(gd)))(fd2)
  val p12a: Placemark = Placemark(Seq(LineString(tessellate, Seq(Coordinates(cs2a ++ cs1.reverse)))(gd)))(fd2)

  it should "merge Placemarks 1" in {
    val maybePlacemark = p1 merge p2
    maybePlacemark.isDefined shouldBe true
    val pz = maybePlacemark.get
    pz shouldBe p12
  }

  it should "merge Placemarks 2" in {
    val maybePlacemark = p2 merge p1
    maybePlacemark.isDefined shouldBe true
    val pz = maybePlacemark.get
    pz.Geometry.head match {
      case LineString(_, cs) => cs shouldBe Seq(Coordinates(cs1 ++ cs2))
    }
    pz shouldBe p12
  }

//  it should "merge Placemarks 3" in {
//    val maybePlacemark = p1 merge p2a
//    maybePlacemark.isDefined shouldBe true
//    val pz = maybePlacemark.get
//    pz shouldBe p12
//  }
//
//  it should "merge Placemarks 4" in {
//    val maybePlacemark = p2a merge p1
//    maybePlacemark.isDefined shouldBe true
//    val pz = maybePlacemark.get
//    pz shouldBe p12a
//  }
}

