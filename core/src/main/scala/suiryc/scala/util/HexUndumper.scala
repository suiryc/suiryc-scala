package suiryc.scala.util

import java.io._
import scala.io.Source

/**
 * Hexadecimal data undumper.
 *
 * Offers a method to rebuild binary data from an hexadecimal representation
 * written by HexDumper.
 *
 * @param settings dumper parameters
 */
class HexUndumper(settings: HexUndumper.Settings) {

  import HexUndumper._

  /** How many bytes to skip. */
  protected var skip: Long = settings.offset

  /** How many bytes to undump. */
  protected var remaining: Long = settings.length

  /** Processes a dump. */
  def process(dump: String): Unit =
    if (remaining != 0) {
      val data = for {
        lineFormat(data) <- lineFormat findAllIn dump
        hexFormat(hex) <- hexFormat findAllIn data
      } yield hex

      @annotation.tailrec
      def skipLoop(): Unit =
        if (data.nonEmpty) {
          skip -= 1
          data.next()
          if (skip > 0) skipLoop()
        }

      @annotation.tailrec
      def writeLoop(): Unit =
        if (data.nonEmpty) {
          val request = math.min(remaining, Int.MaxValue).toInt
          val actualData = (data.take(request).mkString: Hash).bytes
          settings.output.write(actualData)
          remaining -= actualData.length
          if (remaining > 0) writeLoop()
        }

      if (skip > 0) skipLoop()
      if (remaining > 0) writeLoop()
      else settings.output.write((data.mkString: Hash).bytes)
    }

}

/**
 * Hexadecimal data undumper companion object.
 *
 * Offers ways to rebuild binary data from an hexadecimal representation
 * written by HexDumper.
 */
object HexUndumper {

  /** Use ASCII as input charset, since we don't care about binary. */
  private val inputCharset = "ASCII"
  private val inputCodec = scala.io.Codec.apply("ASCII").decodingReplaceWith(".").onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE)

  /** Regular expression extracting hexadecimal representation. */
  private val lineFormat = """(?m)^(?:[^:]*:)?([\s-0-9A-Fa-f]+)\|?.*$""".r
  /** Regular expression extracting hexadecimal data from a representation. */
  private val hexFormat = """([0-9A-Fa-f]{2})""".r

  def main(args: Array[String]): Unit = {
    undump(parseParams(args))
  }

  protected def parseParams(args: Array[String]): Params = {
    val parser = new scopt.OptionParser[Params](getClass.getSimpleName) {
      note("Converts hexadecimal representation to binary data")
      opt[File]("input").valueName("<file>").text("Input file (standard input by default)").action { (v, c) =>
        c.copy(input = Some(v))
      }
      opt[File]("output").valueName("<file>").text("Output file (standard output by default)").action { (v, c) =>
        c.copy(output = Some(v))
      }
      opt[Long]("offset").text("Offset in converted output from which to start").action { (v, c) =>
        c.copy(offset = v)
      }
      opt[Long]("length").text("Number of bytes in converted output to process").action { (v, c) =>
        c.copy(offset = v)
      }
      checkConfig { c =>
        val inputEqualsOutputOpt = for {
          input <- c.input.map(_.getCanonicalPath)
          output <- c.output.map(_.getCanonicalPath)
        } yield {
          input == output
        }
        if (inputEqualsOutputOpt.getOrElse(false)) failure("Input and output cannot be the same")
        else success
      }
    }

    parser.parse(args, Params()).getOrElse(sys.exit(1))
  }

  protected def undump(params: Params): Unit = {
    val (output, onDone) = params.output match {
      case Some(file) =>
        val out = new BufferedOutputStream(
          new FileOutputStream(file)
        )
        (Output.apply(out), { () => out.close() })

      case None =>
        (Output.stdout, { () => System.out.flush() })
    }

    val settings = Settings(params).copy(output = output)
    params.input match {
      case Some(file) =>
        undump(file, settings)

      case None =>
        undump(System.in, settings)
    }
    onDone()
  }

  /**
   * Undumps data.
   *
   * @param dump where to get data to undump
   * @param settings undumper settings
   */
  def undump(dump: String, settings: Settings): Unit = {
    val undumper = new HexUndumper(settings)
    undumper.process(dump)
  }

  /**
   * Undumps data.
   *
   * @param source where to get data to undump
   * @param settings undumper settings
   */
  def undump(source: Source, settings: Settings): Unit = {
    val undumper = new HexUndumper(settings)
    for (line <- source.getLines) {
      undumper.process(line)
    }
  }

  /**
   * Undumps data.
   *
   * @param input where to get data to undump
   * @param settings undumper settings
   */
  def undump(input: InputStream, settings: Settings): Unit =
    undump(Source.fromInputStream(input)(inputCodec), settings)

  /**
   * Undumps data.
   *
   * @param file where to get data to undump
   * @param settings undumper settings
   */
  def undump(file: File, settings: Settings): Unit =
    undump(Source.fromFile(file)(inputCodec), settings)

  /**
   * Undumps data.
   *
   * @param dump hexadecimal representation of data to rebuild
   * @return original data corresponding to the given hexadecimal representation
   */
  def undump(dump: String): Array[Byte] = {
    // Re-use common function to undump
    var result: Option[Array[Byte]] = None
    val output = new Output {
      override def write(data: Array[Byte]): Unit = result = Some(data)
    }
    val settings = Settings(output)
    undump(dump, settings)
    result.get
  }

  /**
   * Undumper settings.
   *
   * Defaults are:<ul>
   *   <li>output: standard output</li>
   *   <li>offset: 0</li>
   *   <li>length: -1 (meaning whole data)</li>
   * </ul>
   *
   * @param output where to dump the hexadecimal representation of data
   * @param offset data offset
   * @param length data length, negative value means whole data
   */
  case class Settings(
    output: Output = Output.stdout,
    offset: Long = 0,
    length: Long = -1
  )

  object Settings {
    def apply(params: Params): Settings =
      Settings(
        offset = params.offset,
        length = params.length
      )
  }

  /** Undumper output. */
  trait Output {
    def write(data: Array[Byte]): Unit
  }

  object Output {

    /** Standard output. */
    lazy val stdout = apply(System.out)

    /** Output based on OutputStream. */
    def apply(out: OutputStream) = new StreamOutput(out)

  }

  /** Output based on OutputStream. */
  class StreamOutput(out: OutputStream) extends Output {
    override def write(data: Array[Byte]): Unit = out.write(data)
  }

  /** CLI parameters. */
  protected case class Params(
    input: Option[File] = None,
    output: Option[File] = None,
    offset: Long = 0,
    length: Long = -1
  )

}
