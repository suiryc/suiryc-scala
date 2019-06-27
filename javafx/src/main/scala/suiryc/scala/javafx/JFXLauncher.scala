package suiryc.scala.javafx

import javafx.application.{Application, Platform}
import javafx.stage.Stage
import scala.reflect.{classTag, ClassTag}

/**
 * JavaFX application launcher.
 *
 * An object can extend this class to automatically launch a JFXApplication. It
 * also exposes methods to shutdown the application.
 * The default (no-args) constructor of the JFXApplication is used to create a
 * new instance. If necessary, the JFXLauncher can be overridden to create the
 * instance any way it wants.
 *
 * Delegating launching to another class may also be useful when trying to run
 * modular JavaFX (>= 11) without caring for modules. Apparently JavaFX does
 * enforce proper modules usage (requires to declare used modules, as well as
 * packages to export to other - unnamed - modules) only if the run 'main'
 * function belongs to an Application instance.
 * This is useful when running from an IDE or when trying to run from a fat jar
 * as there is no need to change anything (as if there was no module involved).
 * In the case of scala, it is also needed to use a different name between the
 * 'object' extending JFXLauncher and the 'class' extending JFXApplication.
 * For example:
 *  object MyApp extends JFXLauncher[MyJFXApp]
 *  class MyJFXApp extends JFXApplication {
 *    override def start(stage: Stage): Unit = ???
 *  }
 */
class JFXLauncher[A <: JFXApplication : ClassTag] {

  // Use the default no-args constructor to instantiate our JFXApplication.
  def newApplication: A = classTag[A].runtimeClass.asInstanceOf[Class[A]].getConstructor().newInstance()

  def main(args: Array[String]): Unit = {
    newApplication.launch(args:_*)
  }

  def shutdown(stage: Stage): Unit = {
    // Make sure to close the stage through the JavaFX thread.
    Platform.runLater(() => stage.close())
    shutdown()
  }

  def shutdown(): Unit = {
    // Exit JavaFX through the JavaFX thread, to ensure the stage is properly
    // closed before doing so.
    Platform.runLater(() => Platform.exit())
  }

}

/** JavaFX application. */
trait JFXApplication extends Application {

  def launch(args: String*): Unit = {
    // 'launch' does not return until application is closed
    Application.launch(getClass, args:_*)
  }

}

// Alternatively, reflection could be used to run the 'main' method of a given
// object. In this case, there is no need to use different names between the
// JFXLauncher and JFXApplication implementations, but it requires to pass the
// object full name (package included) as first parameter.
//object JFXLauncher {
//
//  def runMain(args: Array[String]): Unit = {
//    runMain(args.head, args.drop(1))
//  }
//
//  def runMain(objectName: String, args: Array[String]): Unit = {
//    import scala.reflect.runtime.universe
//
//    val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
//    val module = runtimeMirror.staticModule(objectName)
//    val obj = runtimeMirror.reflectModule(module)
//    val targetName = universe.TermName("main")
//    val method = obj.symbol.info.baseClasses.flatMap { cls =>
//      cls.info.decls.find { s =>
//        lazy val m = s.asMethod
//        s.isMethod && (m.name == targetName) && {
//          m.paramLists.headOption.exists { p =>
//            (p.size == 1) && (p.head.typeSignature =:= universe.typeOf[Array[String]])
//          }
//        }
//      }
//    }.headOption.map { s =>
//      runtimeMirror.reflect(obj.instance).reflectMethod(s.asMethod)
//    }.getOrElse {
//      throw new Exception(s"Could not find method=<$targetName> in object=<$objectName>")
//    }
//    method(args)
//    ()
//  }
//
//}
