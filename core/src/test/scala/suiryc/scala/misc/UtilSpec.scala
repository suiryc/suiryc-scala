package suiryc.scala.misc

import org.scalatest.{Matchers, WordSpec}

// scalastyle:off magic.number
class UtilSpec extends WordSpec with Matchers {

  "indexOf" should {

    "find present element" in {
      val arr = "ABCDEF".toArray
      arr.zipWithIndex.foreach {
        case (c, idx) =>
          Util.indexOf(arr, c, 0, arr.length) shouldBe idx
          Util.indexOf(arr, c, idx, 1) shouldBe idx
      }
    }

    "find first occurrence only" in {
      val arr = "ABCCCC".toArray
      Util.indexOf(arr, 'C', 0, arr.length) shouldBe 2
      Util.indexOf(arr, 'C', 3, arr.length - 3) shouldBe 3
    }

    "not find missing element" in {
      val arr = "ABCDEF".toArray
      Util.indexOf(arr, 'G', 0, arr.length) shouldBe -1
    }

    "not find element outside range" in {
      val arr = "ABCDEF".toArray
      Util.indexOf(arr, 'B', 2, 2) shouldBe -1
      Util.indexOf(arr, 'E', 2, 2) shouldBe -1
    }

    "handle empty length" in {
      val arr = "ABCDEF".toArray
      Util.indexOf(arr, 'A', 0, 0) shouldBe -1
    }

    "handle out of range length" in {
      val arr = "ABCDEF".toArray
      Util.indexOf(arr, 'G', 0, arr.length + 1024) shouldBe -1
    }

    "handle negative length" in {
      val arr = "ABCDEF".toArray
      Util.indexOf(arr, 'A', 0, -1024) shouldBe -1
    }

    "handle out of range offset" in {
      val arr = "ABCDEF".toArray
      Util.indexOf(arr, 'G', 1024, arr.length) shouldBe -1
    }

    "handle negative offset" in {
      val arr = "ABCDEF".toArray
      Util.indexOf(arr, 'C', -1024, arr.length) shouldBe -1
      Util.indexOf(arr, 'C', -1024, 1024 + arr.length) shouldBe 2
      Util.indexOf(arr, 'C', -1024, 1024 + 2) shouldBe -1
    }

  }

}
// scalastyle:on magic.number
