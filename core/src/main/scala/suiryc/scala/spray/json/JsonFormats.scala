package suiryc.scala.spray.json

import java.time.LocalDate
import java.util.UUID
import spray.json._

/** spray-json formats. */
trait JsonFormats {

  // UUID format is not present in spray-json
  // See: https://github.com/spray/spray-json/issues/24
  implicit object UUIDFormat extends JsonFormat[UUID] {

    def write(uuid: UUID): JsValue =
      JsString(uuid.toString)

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

  implicit object LocalDateFormat extends JsonFormat[LocalDate] {

    def write(date: LocalDate): JsValue =
      JsString(date.toString)

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

}
