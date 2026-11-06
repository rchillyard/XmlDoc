package com.phasmidsoftware.xmldoc.core

import com.phasmidsoftware.xmldoc.core.Mergeable.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should

class MergeableSpec extends AnyFlatSpec with should.Matchers {

  behavior of "Mergeable"

  it should "mergeSequence" in {
    mergeSequence(Seq(Text("a"), Text("b"), Text("c")))(Text("failed")) shouldBe Seq(Text("a b c"))
    mergeSequence(Seq(Text("a")))(Text("failed")) shouldBe Seq(Text("a"))
  }

  it should "mergeOptions" in {
    val add: (Int, Int) => Option[Int] = (a, b) => Some(a + b)
    mergeOptions(Some(1), Some(2))(add) shouldBe Some(3)
    mergeOptions(None, Some(2))(add) shouldBe Some(2)
    mergeOptions(Some(1), None)(add) shouldBe Some(1)
    mergeOptions[Int](None, None)(add) shouldBe None
    mergeOptions(Some(1), Some(2))((_, _) => None) shouldBe None
  }

  it should "mergeOptionsBiased" in {
    mergeOptionsBiased(Some(1), Some(2)) shouldBe Some(1)
    mergeOptionsBiased(None, Some(2)) shouldBe Some(2)
  }

  it should "mergeStrings" in {
    mergeStrings(Some("a"), Some("b"))((a, b) => Some(a + b)) shouldBe Some("ab")
    mergeStrings(None, Some("b"))((a, b) => Some(a + b)) shouldBe Some("b")
  }

  it should "mergeStringsDelimited" in {
    mergeStringsDelimited(Some("a"), Some("b"))(", ") shouldBe Some("a, b")
    mergeStringsDelimited(Some("a"), None)(", ") shouldBe Some("a")
  }

  behavior of "Mergeable.joinOrdered"

  private val distance: (Int, Int) => Option[Double] = (a, b) => Some(math.abs(a - b).toDouble)

  it should "append the second sequence as it is when its head is nearer" in {
    joinOrdered(Seq(1, 2, 3), Seq(4, 9))(distance) shouldBe Seq(1, 2, 3, 4, 9)
  }

  it should "reverse the second sequence when its last element is nearer" in {
    joinOrdered(Seq(1, 2, 3), Seq(9, 4))(distance) shouldBe Seq(1, 2, 3, 4, 9)
  }

  it should "append the second sequence as it is when the distance is undefined" in {
    joinOrdered(Seq(1, 2, 3), Seq(9, 4))((_, _) => None) shouldBe Seq(1, 2, 3, 9, 4)
  }

  it should "append the second sequence as it is when either sequence is empty" in {
    joinOrdered(Nil, Seq(9, 4))(distance) shouldBe Seq(9, 4)
    joinOrdered(Seq(1), Nil)(distance) shouldBe Seq(1)
  }
}
