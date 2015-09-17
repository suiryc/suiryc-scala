package suiryc.scala.util

import java.io.{InputStream, InputStreamReader, IOException}
import java.security.{AccessController, PrivilegedActionException, PrivilegedExceptionAction}
import java.util.{Locale, PropertyResourceBundle, ResourceBundle}

/**
 * UTF-8 resource bundle control.
 *
 * Resource bundle load files assuming ISO-8859-1 content.
 * To override this behaviour, one solution is to redefine the 'Control' class
 * used, for example by overriding the 'newBundle' function in order to use an
 * UTF-8 input stream reader.
 *
 * This is what this UTF8Control does, as seen in online answers. The code was
 * adapted with latest ResourceBundle.Control.newBundle code and translated to
 * scala.
 *
 * See: http://stackoverflow.com/a/4660195 (which refers to
 * http://jdevelopment.nl/internationalization-jsf-utf8-encoded-properties-files/)
 */
object UTF8Control extends ResourceBundle.Control {

  override def newBundle(baseName: String, locale: Locale, format: String, loader: ClassLoader, reload: Boolean): ResourceBundle = {
    // Note: we only have to change the behaviour for 'java.properties' format
    if (format == "java.properties") {
      val  bundleName = toBundleName(baseName, locale)
      // Changed: was toResourceName0 (private access)
      Option(toResourceName(bundleName, "properties")).flatMap { resourceName =>
        val stream = try {
          AccessController.doPrivileged(new PrivilegedExceptionAction[InputStream]() {
            def run: InputStream = {
              if (reload) {
                Option(loader.getResource(resourceName)).flatMap { url =>
                  Option(url.openConnection).map { connection =>
                    // Disable caches to get fresh data for reloading.
                    connection.setUseCaches(false)
                    connection.getInputStream
                  }
                }.orNull
              } else {
                loader.getResourceAsStream(resourceName)
              }
            }
          })
        } catch {
          case e: PrivilegedActionException => throw e.getException.asInstanceOf[IOException]
        }
        Option(stream).map { stream =>
          try {
            // Changed: was new PropertyResourceBundle(stream)
            // This is where we handle UTF-8 input
            new PropertyResourceBundle(new InputStreamReader(stream, "UTF-8"))
          } finally {
            stream.close()
          }
        }
      }.orNull
    } else super.newBundle(baseName, locale, format, loader, reload)
  }

}
