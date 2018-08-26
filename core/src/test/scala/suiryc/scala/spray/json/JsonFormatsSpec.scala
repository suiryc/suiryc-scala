package suiryc.scala.spray.json

import java.net.URI
import java.nio.file.{Path, Paths}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.{Date, UUID}
import org.scalatest.{Matchers, WordSpec}
import spray.json._

class JsonFormatsSpec extends WordSpec with Matchers with JsonFormats {

  "JsonFormats" should {

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
