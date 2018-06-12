package suiryc.scala.sys

/** OS helpers. */
object OS {

  /** OS name ("os.name"). */
  val name: String = Option(System.getProperty("os.name")).getOrElse("unknown")

  /** Whether the OS is Linux ("os.name" starts with it). */
  lazy val isLinux: Boolean = name.toLowerCase.startsWith("linux")

}
