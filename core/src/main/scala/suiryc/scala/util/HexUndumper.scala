package suiryc.scala.util

import java.io._
import java.nio.charset.Charset
import suiryc.scala.io.FilesEx
import suiryc.scala.util.HexDumper._

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

  /** Processes a dump. */
  def process(dump: String): Unit = {
    val data = for {
      lineFormat(data) <- lineFormat findAllIn dump
      hexFormat(hex) <- hexFormat findAllIn data
    } yield hex
    settings.output.write((data.mkString: Hash).bytes)
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
  private val lineFormat = """(?m)^[^:]*:?([\s-0-9A-Fa-f]+)\|?.*$""".r
  /** Regular expression extracting hexadecimal data from a representation. */
  private val hexFormat = """([0-9A-Fa-f]{2})""".r

  def main(args: Array[String]): Unit = {
    undump(parseParams(args))
  }

  protected def parseParams(args: Array[String]): Params = {
    val parser = new scopt.OptionParser[Params](getClass.getSimpleName) {
      note("Converts hexadecimal representation to binary data")
      // TODO: offset and length too ?
      opt[File]("input").valueName("<file>").text("Input file (standard input by default)").action { (v, c) =>
        c.copy(input = Some(v))
      }
      opt[File]("output").valueName("<file>").text("Output file (standard output by default)").action { (v, c) =>
        c.copy(output = Some(v))
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

    val settings = Settings(output = output)
    params.input match {
      case Some(file) =>
        undump(file, settings)

      case None =>
        undump(System.in, settings)
    }
    onDone()
  }

  def undump(dump: String, settings: Settings): Unit = {
    val undumper = new HexUndumper(settings)
    undumper.process(dump)
  }

  def undump(source: Source, settings: Settings): Unit = {
    val undumper = new HexUndumper(settings)
    for (line <- source.getLines) {
      undumper.process(line)
    }
  }

  def undump(input: InputStream, settings: Settings): Unit =
    undump(Source.fromInputStream(input)(inputCodec), settings)

  def undump(file: File, settings: Settings): Unit =
    undump(Source.fromFile(file)(inputCodec), settings)

  /**
   * Undumps data.
   *
   * @param dump hexadecimal representation of data to rebuild
   * @return original data corresponding to the given hexadecimal representation
   */
  def undump(dump: String): Array[Byte] = {
    Source.fromString(dump).getLines
    val data = for {
      lineFormat(data) <- lineFormat findAllIn dump
      hexFormat(hex) <- hexFormat findAllIn data
    } yield hex
    (data.mkString: Hash).bytes
  }

  /**
   * Undumper settings.
   *
   * Defaults are:<ul>
   *   <li>output: standard output</li>
   * </ul>
   *
   * @param output where to dump the hexadecimal representation of data
   */
  case class Settings(
    output: Output = Output.stdout
  )

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
    output: Option[File] = None
  )

}
