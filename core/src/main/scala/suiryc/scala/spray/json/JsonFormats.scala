package suiryc.scala.spray.json

import java.net.URI
import java.nio.file.{Path, Paths}
import java.time.{Instant, LocalDate}
import java.time.format.DateTimeFormatter
import java.util.{Date, UUID}
import spray.json._

/**
 * spray-json formats.
 *
 * Some standard classes are not present in spray-json.
 * UUID: https://github.com/spray/spray-json/issues/24
 * URI: https://groups.google.com/forum/#!topic/spray-user/dcWSeR7iuu4
 * Path
 * LocalDateFormat
 * Date: https://gist.github.com/owainlewis/ba6e6ed3eb64fd5d83e7
 * ...
 */
trait JsonFormats {

  implicit object UUIDFormat extends JsonFormat[UUID] {

    def write(uuid: UUID): JsValue = JsString(uuid.toString)

    def read(value: JsValue): UUID = value match {
      case JsString(uuid) =>
        try {
          UUID.fromString(uuid)
        } catch {
          case ex: Exception => deserializationError(s"Invalid UUID format: $uuid", ex)
        }

      case _ => deserializationError(s"Expected UUID as JsString. Got $value")
    }

  }

  implicit object URIFormat extends JsonFormat[URI] {

    def write(uri: URI): JsValue =
      JsString(uri.toString)

    def read(value: JsValue): URI = value match {
      case JsString(uri) =>
        try {
          new URI(uri)
        } catch {
          case ex: Exception => deserializationError(s"Invalid URI format: $uri", ex)
        }

      case _ => deserializationError(s"Expected URI as JsString. Got: $value")
    }

  }

  implicit object PathFormat extends JsonFormat[Path] {

    def write(path: Path): JsValue =
      JsString(path.toString)

    def read(value: JsValue): Path = value match {
      case JsString(path) =>
        try {
          Paths.get(path)
        } catch {
          case ex: Exception => deserializationError(s"Invalid Path format: $path", ex)
        }

      case _ => deserializationError(s"Expected URI as JsString. Got: $value")
    }

  }

  implicit object LocalDateFormat extends JsonFormat[LocalDate] {

    // Note: DateTimeFormatter.ISO_LOCAL_DATE is the default format used in
    // both 'toString' and 'parse', which is fine.
    def write(date: LocalDate): JsValue = JsString(date.toString)

    def read(value: JsValue): LocalDate = value match {
      case JsString(dateS) =>
        try {
          LocalDate.parse(dateS)
        } catch {
          case ex: Exception => deserializationError(s"Invalid LocalDate format: $dateS", ex)
        }

      case _ => deserializationError(s"Expected LocalDate as JsString. Got $value")
    }

  }

  implicit object DateFormat extends JsonFormat[Date] {

    // Notes:
    // There are at least 2 ways to format/parse a Date.
    // We can use a SimpleDateFormat, which requires to define ourself the
    // format (including the fact that the used timezone is the local one), and
    // take care of concurrent accesses (e.g. through a LocalThread value).
    // But actually a Date does not hold a timezone. So an alternative is to
    // convert the Date to/from an Instant (which holds equivalent information)
    // and use DateTimeFormatter.ISO_INSTANT (which is thread-safe).
    // The latter is slightly faster.

    def write(date: Date): JsValue = JsString(formatter.format(date.toInstant))

    def read(value: JsValue): Date = value match {
      case JsString(dateS) =>
        try {
          Date.from(Instant.from(formatter.parse(dateS)))
        } catch {
          case ex: Exception => deserializationError(s"Invalid Date format: $dateS", ex)
        }

      case _ => deserializationError(s"Expected Date as JsString. Got $value")
    }

    // ISO date formatter.
    private val formatter = DateTimeFormatter.ISO_INSTANT

  }

}
