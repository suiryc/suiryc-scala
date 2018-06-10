package suiryc.scala.settings

import com.typesafe.config.{Config, ConfigValueFactory}
import java.nio.file.{Path, Paths}
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import suiryc.scala.RichEnumeration
import suiryc.scala.misc.Units

/**
 * Config entry value.
 *
 * Relies on portable settings to get/update config.
 */
trait ConfigEntry[A] {
  // The portable settings (to track updated config)
  protected[settings] val settings: PortableSettings
  // The actual value handler
  protected val handler: ConfigEntry.Handler[A]
  // The setting path
  protected[settings] val path: String

  /** Gets whether entry exists. */
  def exists: Boolean = settings.config.hasPath(path)
  /** Gets the entry as a single value. */
  def get: A = handler.get(settings.config, path)
  /** Gets the entry as a single optional value. */
  def opt: Option[A] = if (exists) Some(get) else None
  /** Gets the entry as a list of values. */
  def getList: List[A] = handler.getList(settings.config, path)
  /** Gets the entry as a list of values (empty if entry is missing). */
  def optList: List[A] = if (exists) getList else Nil
  /** Sets the entry as a single value. */
  def set(v: A): Config = settings.withValue(path, ConfigValueFactory.fromAnyRef(handler.toInner(v)))
  /** Sets the entry as a list of values. */
  def setList(v: List[A]): Config = settings.withValue(path, ConfigValueFactory.fromIterable(v.map(handler.toInner).asJava))
  /** Removes entry. */
  def remove: Config = settings.withoutPath(path)

  /** Adds default value for 'get' (if entry is missing) */
  def withDefault(v: A): ConfigEntry[A] = new ConfigEntry.Wrapped[A](this) with ConfigEntry.WithDefault[A] {
    override protected val default: A = v
  }
  /** Adds default value for 'getList' (if entry is missing) */
  def withDefault(v: List[A]): ConfigEntry[A] = new ConfigEntry.Wrapped[A](this) with ConfigEntry.WithDefaultList[A] {
    override protected val defaultList: List[A] = v
  }

}

object ConfigEntry extends BaseConfigImplicits {

  /**
   * Config entry value reader.
   *
   * The function to call for reading an entry (single or list) depends on the
   * entry type, which is defined here.
   *
   * To set a value, ConfigValueFactory.fromAnyRef can be used, or for lists
   * ConfigValueFactory.fromIterable can be used instead.
   * In both cases, given objects are generically handled (base types are
   * managed as well as ConfigValue).
   * So let ConfigEntry call those, and define here how to convert to a
   * proper 'Inner' value (as needed by Config).
   */
  trait Handler[A] {
    /** Gets the entry as a single value. */
    def get(config: Config, path: String): A
    /** Gets the entry as a list of values. */
    def getList(config: Config, path: String): List[A]
    /** Converts from 'Outer' (application) to 'Inner' (Config) */
    def toInner(v: A): Any = v
  }

  /** Basic (default implementation) ConfigEntry. */
  class Basic[A](
    override protected[settings] val settings: PortableSettings,
    override protected val handler: ConfigEntry.Handler[A],
    override protected[settings] val path: String
  ) extends ConfigEntry[A]

  /**
   * Wrapped ConfigEntry.
   *
   * Used to override get or getList with default value.
   */
  class Wrapped[A](wrapped: ConfigEntry[A]) extends ConfigEntry[A] {
    override protected[settings] val settings: PortableSettings = wrapped.settings
    override protected val handler: ConfigEntry.Handler[A] = wrapped.handler
    override protected[settings] val path: String = wrapped.path
    override def get: A = wrapped.get
    override def getList: List[A] = wrapped.getList
  }

  /** Handles default value for config entry. */
  trait WithDefault[A] { self: ConfigEntry[A] =>
    protected val default: A
    override def get: A = {
      if (exists) handler.get(settings.config, path)
      else default
    }
  }

  /** Handles default list value for config entry. */
  trait WithDefaultList[A] { self: ConfigEntry[A] =>
    protected val defaultList: List[A]
    override def getList: List[A] = {
      if (exists) handler.getList(settings.config, path)
      else defaultList
    }
  }

  /** Builds a config entry for a type with implicit handler. */
  def from[A](settings: PortableSettings, path: String*)(implicit handler: Handler[A]): ConfigEntry[A] =
    new Basic(settings, handler, path.mkString("."))

  /** Builds a config entry for a given Enumeration. */
  def from[A <: Enumeration](settings: PortableSettings, enum: A, path: String*): ConfigEntry[A#Value] =
    new Basic(settings, enumerationHandler(enum), path.mkString("."))

  /** Boolean entry handler. */
  implicit val booleanHandler: Handler[Boolean] = baseHandler[Boolean]
  /** Int entry handler. */
  implicit val intHandler: Handler[Int] = baseHandler[Int]
  /** Long entry handler. */
  implicit val longHandler: Handler[Long] = baseHandler[Long]
  /** Double entry handler. */
  implicit val doubleHandler: Handler[Double] = baseHandler[Double]
  /** String entry handler. */
  implicit val stringHandler: Handler[String] = baseHandler[String]
  /** Duration (as scala) entry handler. */
  implicit val durationHandler: Handler[FiniteDuration] = new BaseHandler[FiniteDuration] {
    // Config does not handle scala Duration, and converts java Duration to
    // Long value (milliseconds precision).
    // However it handles the same formatted values than scala Duration, so
    // use the formatted representation.
    override def toInner(v: FiniteDuration): String = v.toString
  }
  /**
   * Bytes entry handler.
   *
   * Since we actually get Long values, this handler cannot be implicit and
   * must be explicitly used when applicable.
   */
  val bytesHandler: Handler[Long] = new BaseHandler[Long] {
    override def get(config: Config, path: String): Long = config.getBytes(path)
    override def getList(config: Config, path: String): List[Long] = config.getBytesList(path).asScala.toList.map(Long.unbox)
    override def toInner(v: Long): String = Units.storage.format(v)
  }

  // Basic handler implementation (relying on getters from BaseConfigImplicits)
  private class BaseHandler[A](implicit _get: (Config, String) => A, _getList: (Config, String) => List[A]) extends Handler[A] {
    override def get(config: Config, path: String): A = _get(config, path)
    override def getList(config: Config, path: String): List[A] = _getList(config, path)
  }

  private def baseHandler[A](implicit _get: (Config, String) => A, _getList: (Config, String) => List[A]): Handler[A] =
    new BaseHandler[A]

  /** Enumeration entry handler. */
  implicit def enumerationHandler[A <: Enumeration](implicit enum: A): Handler[A#Value] = new Handler[A#Value] {
    override def get(config: Config, path: String): A#Value = toOuter(configGetString(config, path))
    override def getList(config: Config, path: String): List[A#Value] = configGetStringList(config, path).map(toOuter)
    override def toInner(v: A#Value): String = v.toString
    private def toOuter(v: String): A#Value = enum.byName(v)
  }

  /** Path entry handler. */
  implicit val pathHandler: Handler[Path] = new Handler[Path] {
    override def get(config: Config, path: String): Path = toOuter(configGetString(config, path))
    override def getList(config: Config, path: String): List[Path] = configGetStringList(config, path).map(toOuter)
    override def toInner(v: Path): String = v.toString
    private def toOuter(v: String): Path = Paths.get(v)
  }

}
