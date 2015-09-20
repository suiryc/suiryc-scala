package suiryc.scala

import suiryc.scala.util.I18NBase

package object javafx {

  object I18NBase extends I18NBase("i18n.suiryc-scala-javafx")

  /** Whether the OS is Linux ("os.name" starts with it). */
  lazy val isLinux: Boolean =
    Option(System.getProperty("os.name")).exists { os =>
      os.toLowerCase.startsWith("linux")
    }

}
