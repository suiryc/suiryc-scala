package suiryc.scala.spray.json

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

import java.net.URI
import java.nio.file.{Path, Paths}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.{Date, UUID}
import scala.concurrent.duration._

class JsonFormatsSpec extends AnyWordSpec with Matchers with JsonFormats {

  "JsonFormats" should {

    "handle FiniteDuration format" in {
      List(1.millis, 1.second, 15.seconds).foreach { duration =>
        duration.toJson.compactPrint.parseJson.convertTo[FiniteDuration] shouldBe duration
      }
    }

    "handle UUID format" in {
      val uuid = UUID.randomUUID()
      uuid.toJson.compactPrint.parseJson.convertTo[UUID] shouldBe uuid
    }

    "handle URI format" in {
      val uri = new URI("http://username:password@server:1234/path/subpath/file.ext?param1=value1&param2=value2#fragment")
      uri.toJson.compactPrint.parseJson.convertTo[URI] shouldBe uri
    }

    "handle Path format" in {
      val path = Paths.get("first", "second", "file.ext")
      path.toJson.compactPrint.parseJson.convertTo[Path] shouldBe path
    }

    "handle LocalDate format" in {
      val date = LocalDate.now()
      date.toJson.compactPrint shouldBe s""""${DateTimeFormatter.ISO_LOCAL_DATE.format(date)}""""
      date.toJson.compactPrint.parseJson.convertTo[LocalDate] shouldBe date
    }

    "handle Date format" in {
      val date = new Date()
      date.toJson.compactPrint.parseJson.convertTo[Date] shouldBe date
    }

  }

}
