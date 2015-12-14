package suiryc.scala.util

import java.io._
import java.nio.ByteBuffer
import java.nio.charset.Charset
import suiryc.scala.io.FilesEx
import suiryc.scala.misc.EnumerationEx

/**
 * Hexadecimal data dumper.
 *
 * Offers a way to represent data by their hexadecimal (and ASCII) form, for
 * example:
 * {{{
 * 00: 0001 0203 0405 0607 0809 0A0B 0C0D 0E0F  |................|
 * 10: 1011 1213 1415 1617 1819 1A1B 1C1D 1E1F  |................|
 * 20: 2021 2223 2425 2627 2829 2A2B 2C2D 2E2F  | !"#$%&'()*+,-./|
 * 30: 3031 3233 3435 3637 3839 3A3B 3C3D 3E3F  |0123456789:;<=>?|
 * 40: 4041 4243 4445 4647 4849 4A4B 4C4D 4E4F  |@ABCDEFGHIJKLMNO|
 * 50: 5051 5253 5455 5657 5859 5A5B 5C5D 5E5F  |PQRSTUVWXYZ[\]^_|
 * 60: 6061 6263 6465 6667 6869 6A6B 6C6D 6E6F  |`abcdefghijklmno|
 * 70: 7071 7273 7475 7677 7879 7A7B 7C7D 7E7F  |pqrstuvwxyz{|}~.|
 * 80: 8081 8283 8485 8687 8889 8A8B 8C8D 8E8F  |................|
 * 90: 9091 9293 9495 9697 9899 9A9B 9C9D 9E9F  |................|
 * A0: A0A1 A2A3 A4A5 A6A7 A8A9 AAAB ACAD AEAF  | ¡¢£¤¥¦§¨©ª«¬­®¯|
 * B0: B0B1 B2B3 B4B5 B6B7 B8B9 BABB BCBD BEBF  |°±²³´µ¶·¸¹º»¼½¾¿|
 * C0: C0C1 C2C3 C4C5 C6C7 C8C9 CACB CCCD CECF  |ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏ|
 * D0: D0D1 D2D3 D4D5 D6D7 D8D9 DADB DCDD DEDF  |ÐÑÒÓÔÕÖ×ØÙÚÛÜÝÞß|
 * E0: E0E1 E2E3 E4E5 E6E7 E8E9 EAEB ECED EEEF  |àáâãäåæçèéêëìíîï|
 * F0: F0F1 F2F3 F4F5 F6F7 F8F9 FAFB FCFD FEFF  |ðñòóôõö÷øùúûüýþÿ|
 * }}}
 *
 * Hexadecimal and ASCII views can be changed:<ul>
 *   <li>(default) compact hexadecimal view: one space every 2 bytes, one more space every 16 bytes</li>
 *   <li>large hexadecimal view: one space every byte, one more space every 8 bytes</li>
 *   <li>(default) divided ASCII view: '|' separation every 16 chars</li>
 *   <li>undivided ASCII view: no separation</li>
 * </ul>
 *
 * Compact hexadecimal and divided ASCII view with more than 16 bytes:
 * {{{
 * 40: 4041 4243 4445 4647 4849 4A4B 4C4D 4E4F  5051 5253 5455 5657 5859 5A5B 5C5D 5E5F  |@ABCDEFGHIJKLMNO|PQRSTUVWXYZ[\]^_|
 * }}}
 *
 * Large hexadecimal view:
 * {{{
 * 40: 40 41 42 43 44 45 46 47  48 49 4A 4B 4C 4D 4E 4F  |@ABCDEFGHIJKLMNO|
 * }}}
 *
 * Undivided ASCII view:
 * {{{
 * 40: 4041 4243 4445 4647 4849 4A4B 4C4D 4E4F  5051 5253 5455 5657 5859 5A5B 5C5D 5E5F  |@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_|
 * }}}
 *
 * @param settings dumper parameters
 */
class HexDumper(settings: HexDumper.Settings) {

  import HexDumper._

  protected type BytesView = scala.collection.IterableView[Byte, Array[Byte]]

  protected val decoder = settings.charset.newDecoder().replaceWith(".")

  /** How many bytes per section in hexadecimal view. */
  protected val hexSectionBytes = HexDumper.hexSectionBytes(settings.hexViewMode)

  /** How many bytes in the last hexadecimal section. */
  protected val hexLastSectionBytes =
    ((settings.viewBytes - 1) % hexSectionBytes) + 1

  /** How many hexadecimal sections. */
  protected val hexSectionCount =
    (settings.viewBytes + hexSectionBytes - 1) / hexSectionBytes

  protected def computeHexSectionSize(bytes: Int): Int =
    settings.hexViewMode match {
      case HexadecimalViewMode.Large =>
        // Each byte is represented as 2 hexadecimal characters.
        // There is one space between each represented byte, so the total number
        // of spaces is one less than the number of represented bytes.
        bytes * 3 - 1
      case HexadecimalViewMode.Compact =>
        // Each byte is represented as 2 hexadecimal characters.
        // There is one space between each pair of represented bytes.
        bytes * 2 + (bytes + 1) / 2 - 1
    }

  /** Size of each hexadecimal section. */
  protected val hexSectionSize: Int = computeHexSectionSize(hexSectionBytes)

  /** Size of last hexadecimal section. */
  protected val hexLastSectionSize: Int = computeHexSectionSize(hexLastSectionBytes)

  /** Current offset (to display). */
  protected var offset: Long = 0L

  /** Offset format. */
  protected val offsetFormat = s"%0${settings.offsetSize * 2}X: "

  /** Current buffer. */
  protected val buffer = new Array[Byte](settings.viewBytes)
  /** Current buffer length. */
  protected var bufferLength = 0

  /** Padding. */
  protected def ensureLength(str: String, len: Int) =
    if (str.length >= len) str
    else str + " " * (len - str.length)

  /** Produces the hexadecimal representation. */
  protected def processHexSection(section: BytesView) = {
    val bytes = section.map("%02X".format(_))
    settings.hexViewMode match {
      case HexadecimalViewMode.Large   => bytes.mkString(" ")
      case HexadecimalViewMode.Compact => bytes.grouped(2).map(_.mkString("")).mkString(" ")
    }
  }

  /** Processes offset. */
  protected def processOffset(): Unit = {
    if (offset > 0) settings.output.append('\n')
    settings.output.append(offsetFormat.format(offset))
  }

  /** Processes hexadecimal view. */
  protected def processHexadecimal(data: Array[Byte], dataOffset: Int, dataLength: Int): Unit = {
    // Note: trying to group views directly does not work, e.g. data.view(...).grouped(...)
    // fails later when trying to use its elements:
    // java.lang.ClassCastException: scala.collection.SeqViewLike$$anon$1 cannot be cast to scala.collection.mutable.IndexedSeqView
    val dataEnd = dataOffset + dataLength
    val sectionsWithSize = (0 until hexSectionCount).map { index =>
      val from = math.min(dataEnd, dataOffset + hexSectionBytes * index)
      val until = math.min(dataEnd, dataOffset + hexSectionBytes * (index + 1))
      val sectionSize =
        if (index == hexSectionCount - 1) hexLastSectionSize
        else hexSectionSize
      (data.view(from, until), sectionSize)
    }

    sectionsWithSize.foldLeft(0) { case (index, (section, sectionSize)) =>
      if (index > 0) settings.output.append("  ")
      settings.output.append(ensureLength(processHexSection(section), sectionSize))
      index + 1
    }
    ()
  }

  /** Processes ASCII view. */
  protected def processAscii(data: Array[Byte], dataOffset: Int, dataLength: Int): Unit = {
    // Filter ISO-8859 non-visible characters
    val filtered = data.view(dataOffset, dataOffset + dataLength).map { c =>
      if (((c >= 0x00) && (c <= 0x1F)) || (c == 0x7F) || ((c >= 0x80.toByte) && (c <= 0x9F.toByte))) 0x2E.asInstanceOf[Byte]
      else c
    }.force
    // Decode to ASCII
    val asciiSectionsWithSize = settings.asciiViewMode match {
      case AsciiViewMode.Undivided => List((filtered, settings.viewBytes))
      case AsciiViewMode.Divided   =>
        // How many chars in each ASCII section
        val asciiSectionSize = 16
        // How many chars in last ASCII section
        val asciiLastSectionSize =
          ((settings.viewBytes - 1) % asciiSectionSize) + 1
        // How many ASCII sections
        val asciiSectionCount =
          (settings.viewBytes + asciiSectionSize - 1) / asciiSectionSize
        filtered.grouped(asciiViewSectionBytes).zipWithIndex.map { case (section, index) =>
          val sectionSize =
            if (index == asciiSectionCount - 1) asciiLastSectionSize
            else asciiSectionSize
          (section, sectionSize)
        }
    }
    val ascii = asciiSectionsWithSize.map { case (section, sectionSize) =>
      ensureLength(decoder.decode(ByteBuffer.wrap(section)).toString, sectionSize)
    }.mkString("|", "|", "|")

    settings.output.append("  ")
    settings.output.append(ascii)
  }

  /** Processes one line to display. */
  protected def process(data: Array[Byte], dataOffset: Int, dataLength: Int): Unit = {
    processOffset()
    processHexadecimal(data, dataOffset, dataLength)
    processAscii(data, dataOffset, dataLength)

    offset += dataLength
  }

  /**
   * Dumps data.
   *
   * @param data data to dump
   * @param dataOffset data offset
   * @param dataLength data length, negative value means whole array
   * @param end whether there is no more data after this (flushes)
   */
  def dump(data: Array[Byte], dataOffset: Int = 0, dataLength: Int = -1, end: Boolean = false): Unit = {
    @annotation.tailrec
    def loop(dataOffset: Int, dataLength: Int): Unit = if (dataLength > 0) {
      if (bufferLength > 0) {
        // First try to fill current buffer
        val filling = math.min(settings.viewBytes - bufferLength, dataLength)
        Array.copy(data, dataOffset, buffer, bufferLength, filling)
        bufferLength += filling
        // Flush buffer if full
        if (bufferLength == settings.viewBytes) flush()
        // And keep on with remaining data
        loop(dataOffset + filling, dataLength - filling)
      }
      else if ((dataLength < settings.viewBytes) && !end) {
        // Not enough data to display for now, so use buffer
        Array.copy(data, dataOffset, buffer, bufferLength, dataLength)
        bufferLength += dataLength
      }
      else {
        // Process one line (may be less than viewBytes if flushing)
        val actualLength = math.min(dataLength, settings.viewBytes)
        process(data, dataOffset, actualLength)
        // And keep on with remaining data
        loop(dataOffset + actualLength, dataLength - actualLength)
      }
    }

    loop(dataOffset, if (dataLength < 0) data.length - dataOffset else dataLength)
    if (end) done()
  }

  /** Flushes pending data to output. */
  protected def flush(): Unit =
    if (bufferLength > 0) {
      process(buffer, 0, bufferLength)
      bufferLength = 0
    }

  /** Ends dump. */
  def done(): Unit = {
    flush()
    if (settings.endWithEOL && (offset > 0)) settings.output.append('\n')
  }

}

/**
 * Hexadecimal data dumper companion object.
 *
 * Offers ways to represent data by their hexadecimal (and ASCII) form.
 */
object HexDumper {

  /** Default charset: ISO-8859-1. */
  private val defaultCharset = Charset.forName("ISO-8859-1")

  /** Default number of bytes displayed per line: 16. */
  private val defaultViewBytes = 16

  /** Default hexadecimal view mode: compact. */
  private val defaultHexViewMode = HexadecimalViewMode.Compact

  /** Default ASCII view mode: divided. */
  private val defaultAsciiViewMode = AsciiViewMode.Divided

  /** How many bytes per section in hexadecimal view. */
  private val hexSectionBytes = Map(
    HexadecimalViewMode.Large   -> 8,
    HexadecimalViewMode.Compact -> 16
  )

  /** How many bytes per section in ascii view. */
  private val asciiViewSectionBytes = 16

  /** Maximum offset size expected: 4 bytes (4GiB of data). */
  private val maxOffsetSize = 4

  def main(args: Array[String]): Unit = {
    dump(parseParams(args))
  }

  protected def parseParams(args: Array[String]): Params = {
    val parser = new scopt.OptionParser[Params](getClass.getSimpleName) {
      note("Displays hexadecimal representation of input")
      opt[File]("input").valueName("<file>").text("Input file (standard input by default)").action { (v, c) =>
        c.copy(input = Some(v))
      }
      opt[File]("output").valueName("<file>").text("Output file (standard output by default)").action { (v, c) =>
        c.copy(output = Some(v))
      }
      opt[Long]("offset").text("Offset from which to start").action { (v, c) =>
        c.copy(offset = v)
      }
      opt[Long]("length").text("Number of bytes to process").action { (v, c) =>
        c.copy(offset = v)
      }
      opt[String]("charset").text("Output charset").action { (v, c) =>
        c.copy(charset = Charset.forName(v))
      }
      opt[String]("view-hex-mode").text("Hexadecimal view mode").action { (v, c) =>
        c.copy(hexViewMode = HexadecimalViewMode(v))
      }
      opt[String]("view-ascii-mode").text("ASCII view mode").action { (v, c) =>
        c.copy(asciiViewMode = AsciiViewMode(v))
      }
      opt[Int]("view-bytes").text("How many bytes to display per line").action { (v, c) =>
        c.copy(viewBytes = v)
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

  protected def dump(params: Params): Unit = {
    // Note: create settings when necessary (first try to access the input)
    lazy val (output, onDone) = params.output match {
      case Some(file) =>
        val writer = new BufferedWriter(
          new OutputStreamWriter(
            new FileOutputStream(file),
            params.charset
          )
        )
        (Output.apply(writer), { () => writer.close() })

      case None =>
        (Output.stdout, { () => System.out.flush() })
    }

    lazy val settings = Settings(params).copy(output = output)
    params.input match {
      case Some(file) =>
        dump(FilesEx.map(file, params.offset, params.length), settings)

      case None =>
        dump(System.in, settings)
    }
    onDone()
  }

  /**
   * Dumps data.
   *
   * @param data data to dump
   * @param settings dumper settings
   */
  def dump(data: Array[Byte], settings: Settings): Unit = {
    val maxLength = data.length - settings.offset
    val actualLength =
      if (settings.length >= 0) math.min(settings.length, maxLength)
      else maxLength
    val actualSettings = guessOffsetSize(settings.copy(length = actualLength))
    val dumper = new HexDumper(actualSettings)
    dumper.dump(data, actualSettings.offset.toInt, actualSettings.length.toInt, end = true)
  }

  /**
   * Dumps data.
   *
   * @param input where to get data to dump
   * @param settings dumper settings
   */
  def dump(input: InputStream, settings: Settings): Unit = {
    val buffer = new Array[Byte](16 * 1024)
    val actualSettings = guessOffsetSize(settings)
    val dumper = new HexDumper(actualSettings)

    @annotation.tailrec
    def loop(offset: Long, length: Long): Unit = {
      if (offset > 0) {
        val skipped = input.skip(offset)
        if (skipped > 0) loop(offset - skipped, length)
        // else: nothing skipped, assume end of stream
      }
      else if (length != 0) {
        val request =
          if (length < 0) buffer.length.toLong
          else math.min(length, buffer.length.toLong)
        val read = input.read(buffer, 0, request.toInt)
        if (read > 0) {
          dumper.dump(buffer, 0, read)
          loop(offset, if (length > 0) length - read else length)
        }
        // else: nothing read, assume end of stream
      }
    }

    loop(actualSettings.offset, actualSettings.length)
    dumper.done()
  }

  /**
   * Dumps data.
   *
   * @param data where to get data to dump
   * @param settings dumper settings
   */
  def dump(data: ByteBuffer, settings: Settings): Unit = {
    // Note: does not work with large buffers (> 2GiB)
    val buffer = new Array[Byte](16 * 1024)
    val actualLength =
      if (settings.length >= 0) settings.length
      else data.limit - settings.offset
    val actualSettings = guessOffsetSize(settings.copy(length = actualLength))
    val dumper = new HexDumper(actualSettings)

    def loop(data: ByteBuffer): Unit = {
      val remaining = data.remaining
      if (remaining > 0) {
        val read = math.min(remaining, buffer.length)
        data.get(buffer, 0, read)
        dumper.dump(buffer, 0, read)
        loop(data)
      }
    }

    val actualData =
      data.duplicate()
        .position(actualSettings.offset.toInt)
        .limit((actualSettings.offset + actualSettings.length).toInt)
        .asInstanceOf[ByteBuffer]
    loop(actualData)
    dumper.done()
  }

  protected def guessOffsetSize(length: Long): Int =
    if (length < 0) maxOffsetSize
    else (1 to (maxOffsetSize - 1)).find { bytes =>
      length <= (1 << (bytes * 2 * 4))
    }.getOrElse(maxOffsetSize)

  protected def guessOffsetSize(settings: Settings): Settings =
    if (settings.offsetSize >= 0) settings
    else settings.copy(offsetSize = guessOffsetSize(settings.length))

  /**
   * Dumper settings.
   *
   * Defaults are:<ul>
   *   <li>output: standard output</li>
   *   <li>offset: 0</li>
   *   <li>length: -1 (meaning whole data)</li>
   *   <li>viewBytes: 16</li>
   *   <li>hexViewMode: compact</li>
   *   <li>asciiViewMode: divided</li>
   *   <li>charset: ISO-8859-1</li>
   *   <li>endWithEOL: true</li>
   * </ul>
   *
   * @param output where to dump the hexadecimal representation of data
   * @param offset data offset
   * @param length data length, negative value means whole data
   * @param offsetSize size in bytes of offset (negative value to guess based on data length)
   * @param viewBytes number of bytes per line
   * @param hexViewMode hexadecimal view mode
   * @param asciiViewMode ASCII view mode
   * @param charset ASCII representation charset to use
   * @param endWithEOL whether to end with an end of line
   */
  case class Settings(
    output: Output = Output.stdout,
    offset: Long = 0,
    length: Long = -1,
    offsetSize: Int = -1,
    viewBytes: Int = defaultViewBytes,
    hexViewMode: HexadecimalViewMode.Value = defaultHexViewMode,
    asciiViewMode: AsciiViewMode.Value = defaultAsciiViewMode,
    charset: Charset = defaultCharset,
    endWithEOL: Boolean = true
  )

  object Settings {
    def apply(params: Params): Settings =
      Settings(
        offset = params.offset,
        length = params.length,
        offsetSize = params.offsetSize,
        viewBytes = params.viewBytes,
        hexViewMode = params.hexViewMode,
        asciiViewMode = params.asciiViewMode,
        charset = params.charset
      )
  }

  /** Dumper output. */
  trait Output {
    def append(data: Char): Unit
    def append(data: CharSequence): Unit
  }

  object Output {

    /** Standard output. */
    lazy val stdout = apply(System.out)

    /** Output based on Appendable. */
    def apply(out: Appendable): AppendableOutput = new AppendableOutput(out)

    /** Output based on (scala) StringBuilder. */
    def apply(out: StringBuilder): ScalaStringBuilderOutput = new ScalaStringBuilderOutput(out)

  }

  /** Output based on Appendable. */
  class AppendableOutput(out: Appendable) extends Output {
    override def append(data: Char): Unit = { out.append(data); () }
    override def append(data: CharSequence): Unit = { out.append(data); () }
  }

  /** Output based on (scala) StringBuilder. */
  class ScalaStringBuilderOutput(out: StringBuilder) extends Output {
    override def append(data: Char): Unit = { out.append(data); () }
    override def append(data: CharSequence): Unit = { out.append(data); () }
  }

  /** CLI parameters. */
  protected case class Params(
    input: Option[File] = None,
    output: Option[File] = None,
    offset: Long = 0,
    length: Long = -1,
    offsetSize: Int = -1,
    viewBytes: Int = defaultViewBytes,
    hexViewMode: HexadecimalViewMode.Value = defaultHexViewMode,
    asciiViewMode: AsciiViewMode.Value = defaultAsciiViewMode,
    charset: Charset = defaultCharset
  )

  /** Hexadecimal view modes. */
  object HexadecimalViewMode extends EnumerationEx {
    /** Large: one space every byte, one more space every 8 bytes. */
    val Large = Value
    /** Compact: one space every 2 bytes, one more space every 16 bytes. */
    val Compact = Value
  }

  /** ASCII view modes. */
  object AsciiViewMode extends EnumerationEx {
    /** Undivided: whole sequence shown. */
    val Undivided = Value
    /** Divided: '|' separation every 16 bytes. */
    val Divided = Value
  }

}
