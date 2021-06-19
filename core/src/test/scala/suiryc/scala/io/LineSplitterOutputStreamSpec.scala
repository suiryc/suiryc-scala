package suiryc.scala.io

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.PrintStream
import java.nio.charset.StandardCharsets

// scalastyle:off regex token
class LineSplitterOutputStreamSpec extends AnyWordSpec with Matchers {

  import LineSplitterOutputStreamSpec._

  "LineSplitterOutputStream" should {

    "handle writing bytes" in {
      val setup = new TestSetup
      setup.printer.write('A')
      setup.printer.write('B')
      setup.printer.write('C')
      setup.writer.getLines shouldBe empty
      setup.printer.write('\n')
      setup.writer.getLines shouldBe List("ABC")
      setup.printer.close()
      setup.writer.getLines shouldBe empty
    }

    "handle writing byte arrays" in {
      val setup = new TestSetup
      setup.printer.write("ABC\n".getBytes(UTF8))
      setup.writer.getLines shouldBe List("ABC")
      setup.printer.close()
      setup.writer.getLines shouldBe empty
    }

    "handle writing partial byte arrays" in {
      val setup = new TestSetup
      setup.printer.write("ZDEF\n".getBytes(UTF8), 1, 3)
      setup.writer.getLines shouldBe empty
      setup.printer.write("\nZ\n".getBytes(UTF8), 0, 1)
      setup.writer.getLines shouldBe List("DEF")
      setup.printer.close()
      setup.writer.getLines shouldBe empty
    }

    "handle writing lines" in {
      val setup = new TestSetup
      setup.printer.println("ABC")
      setup.writer.getLines shouldBe List("ABC")
      setup.printer.println(1)
      setup.printer.close()
      setup.writer.getLines shouldBe List("1")
    }

    "handle multiple lines" in {
      val setup = new TestSetup
      setup.printer.println("ABC\nDEF")
      setup.writer.getLines shouldBe List("ABC", "DEF")
      setup.printer.write("abc\ndef\n".getBytes(UTF8))
      setup.writer.getLines shouldBe List("abc", "def")
    }

    "handle empty lines" in {
      val setup = new TestSetup
      setup.printer.println("\nABC\n\nDEF\n")
      setup.writer.getLines shouldBe List("", "ABC", "", "DEF", "")
    }

    "handle CR" in {
      val setup = new TestSetup
      setup.printer.write("ABC\r\n".getBytes(UTF8))
      setup.writer.getLines shouldBe List("ABC")
      setup.printer.write("ABC\r\r\n".getBytes(UTF8))
      setup.writer.getLines shouldBe List("ABC\r")
      setup.printer.close()
      setup.writer.getLines shouldBe empty
    }

    "handle CR and LF written separately" in {
      val setup = new TestSetup
      setup.printer.write("ABC\r".getBytes(UTF8))
      setup.writer.getLines shouldBe empty
      setup.printer.write("\nDEF\r\n".getBytes(UTF8))
      setup.writer.getLines shouldBe List("ABC", "DEF")
    }

    "do nothing on nominal flushing" in {
      val setup = new TestSetup
      setup.printer.write("ABC".getBytes(UTF8))
      setup.writer.getLines shouldBe empty
      setup.printer.flush()
      setup.writer.getLines shouldBe empty
    }

    "handle closing without pending data" in {
      val setup = new TestSetup
      setup.printer.close()
      setup.writer.getLines shouldBe empty
    }

    "handle closing with pending data" in {
      val setup = new TestSetup
      setup.printer.write("ABC".getBytes(UTF8))
      setup.writer.getLines shouldBe empty
      setup.printer.close()
      setup.writer.getLines shouldBe List("ABC")
    }

  }

}

object LineSplitterOutputStreamSpec {

  private val UTF8 = StandardCharsets.UTF_8

  class TestSetup {
    val writer = new MemoryLineWriter
    val output = new LineSplitterOutputStream(writer, UTF8)
    val printer = new PrintStream(output, false, UTF8)
  }

  class MemoryLineWriter extends LineWriter {

    private var lines = List.empty[String]

    override def write(line: String): Unit = {
      lines :+= line
    }

    def getLines: List[String] = {
      val r = lines
      lines = Nil
      r
    }

  }

}
// scalastyle:on regex token
