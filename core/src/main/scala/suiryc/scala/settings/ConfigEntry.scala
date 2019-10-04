package suiryc.scala.settings

import com.typesafe.config.{Config, ConfigValue, ConfigValueFactory}
import java.nio.file.{Path, Paths}
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import suiryc.scala.RichEnumeration
import suiryc.scala.misc.Units

/**
 * Config entry value.
 *
 * Relies on portable settings to get/update config.
 */
trait ConfigEntry[A] {
  /** The portable settings (to track updated config) */
  val settings: PortableSettings
  /** The setting path */
  val path: String
  /** The actual value handler */
  protected val handler: ConfigEntry.Handler[A]

  // Cached value (to prevent re-parsing)
  protected var cached: Option[Option[A]] = None
  protected var cachedList: Option[List[A]] = None

  /** Gets whether entry exists. */
  def exists: Boolean = settings.config.hasPath(path)
  /** Gets ConfigValue. */
  def raw: ConfigValue = settings.config.getValue(path)
  /** Gets optional ConfigValue. */
  def rawOpt: Option[ConfigValue] = if (exists) Some(raw) else None
  /** Gets the entry as a single value. */
  def get: A = cached.flatten.getOrElse {
    val v = handler.get(settings.config, path)
    cached = Some(Some(v))
    v
  }
  /** Gets the entry as a single optional value. */
  def opt: Option[A] = cached.getOrElse {
    if (exists) Some(get)
    else {
      cached = Some(None)
      None
    }
  }
  /** Gets the entry as a list of values. */
  def getList: List[A] = cachedList.getOrElse {
    val v = handler.getList(settings.config, path)
    cachedList = Some(v)
    v
  }
  /** Gets the entry as a list of values (empty if entry is missing). */
  def optList: List[A] = cachedList.getOrElse {
    if (exists) getList
    else {
      cachedList = Some(Nil)
      Nil
    }
  }
  /** Sets the entry as a single value. */
  def set(v: A): Unit = {
    if ((v == null) || refOpt.contains(v)) reset()
    else {
      settings.withValue(path, ConfigValueFactory.fromAnyRef(handler.toInner(v)))
      cached = Some(Some(v))
      cachedList = None
    }
  }
  /** Sets the entry raw value. */
  def rawSet(v: Any): Unit = {
    if (v == null) reset()
    else {
      settings.withValue(path, ConfigValueFactory.fromAnyRef(v))
      cached = None
      cachedList = None
      // Now that the raw value has been set, we can check whether it matches
      // the reference in which case we simply reset it.
      if (refOpt.contains(get)) reset()
    }
  }
  /** Sets the entry as a list of values. */
  def setList(v: List[A]): Unit = {
    if (refOptList == v) reset()
    else {
      settings.withValue(path, ConfigValueFactory.fromIterable(v.map(handler.toInner).asJava))
      cached = None
      cachedList = Some(v)
    }
  }
  /** Resets entry. */
  def reset(): Unit = {
    settings.withoutPath(path)
    // Since the fallback config may hold a default value, let next read call
    // determine it.
    cached = None
    cachedList = None
  }

  // Some helpers to read the same setting in the reference configuration.
  protected var refCached: Option[Option[A]] = None
  protected var refCachedList: Option[List[A]] = None

  /** Gets whether reference entry exists. */
  def refExists: Boolean = settings.reference.hasPath(path)
  /** Gets the reference entry as a single value. */
  def refGet: A = refCached.flatten.getOrElse {
    val v = handler.get(settings.reference, path)
    refCached = Some(Some(v))
    v
  }
  /** Gets the reference entry as a single optional value. */
  def refOpt: Option[A] = refCached.getOrElse(if (refExists) Some(refGet) else None)
  /** Gets the reference entry as a list of values. */
  def refGetList: List[A] = refCachedList.getOrElse {
    val v = handler.getList(settings.reference, path)
    refCachedList = Some(v)
    v
  }
  /** Gets the reference entry as a list of values (empty if entry is missing). */
  def refOptList: List[A] = refCachedList.getOrElse(if (refExists) refGetList else Nil)

  /** Adds default value for 'get'/'refGet' (if entry is missing) */
  def withDefault(v: A): ConfigEntry[A] = new ConfigEntry.Wrapped[A](this) with ConfigEntry.WithDefault[A] {
    override protected val default: A = v
  }
  /** Adds default value for 'getList'/'refGetList' (if entry is missing) */
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
    override val settings: PortableSettings,
    override val path: String,
    override protected val handler: ConfigEntry.Handler[A]
  ) extends ConfigEntry[A]

  /**
   * Wrapped ConfigEntry.
   *
   * Used to override get or getList with default value.
   */
  class Wrapped[A](wrapped: ConfigEntry[A]) extends ConfigEntry[A] {
    override val settings: PortableSettings = wrapped.settings
    override val path: String = wrapped.path
    override protected val handler: ConfigEntry.Handler[A] = wrapped.handler

    // Propagate changes to wrapped entry.
    // Notes:
    // This helps if there is a need to create a wrapped value temporarily while
    // having the underlying one retain changes done.
    // Since it is the same value that is set twice, only the first change in
    // the underlying portable settings (withValue) actually triggers something.
    override def set(v: A): Unit = {
      wrapped.set(v)
      super.set(v)
    }
    override def setList(v: List[A]): Unit = {
      wrapped.setList(v)
      super.setList(v)
    }
    override def reset(): Unit = {
      wrapped.reset()
      super.reset()
    }
  }

  /** Handles default value for config entry. */
  trait WithDefault[A] extends ConfigEntry[A] {
    protected val default: A
    override def get: A = cached.flatten.getOrElse {
      val v =
        if (exists) handler.get(settings.config, path)
        else default
      cached = Some(Some(v))
      v
    }
    override def opt: Option[A] = cached.getOrElse(Some(get))
    override def set(v: A): Unit = {
      if (default == v) reset()
      else super.set(v)
    }
    override def reset(): Unit = {
      super.reset()
      // Re-set cache since the value is now the default one
      cached = Some(Some(default))
    }
    override def refGet: A = refCached.flatten.getOrElse {
      val v =
        if (refExists) handler.get(settings.reference, path)
        else default
      refCached = Some(Some(v))
      v
    }
    override def refOpt: Option[A] = refCached.getOrElse(Some(refGet))
  }

  /** Handles default list value for config entry. */
  trait WithDefaultList[A] extends ConfigEntry[A] {
    protected val defaultList: List[A]
    override def getList: List[A] = cachedList.getOrElse {
      val v =
        if (exists) handler.getList(settings.config, path)
        else defaultList
      cachedList = Some(v)
      v
    }
    override def optList: List[A] = cachedList.getOrElse(getList)
    override def setList(v: List[A]): Unit = {
      if (defaultList == v) reset()
      else super.setList(v)
    }
    override def reset(): Unit = {
      super.reset()
      // Re-set cache since the value is now the default one
      cachedList = Some(defaultList)
    }
    override def refGetList: List[A] = refCachedList.getOrElse {
      val v =
        if (refExists) handler.getList(settings.reference, path)
        else defaultList
      refCachedList = Some(v)
      v
    }
    override def refOptList: List[A] = refCachedList.getOrElse(refGetList)
  }

  /** Builds a config entry for a type with implicit handler. */
  def from[A](settings: PortableSettings, path: Seq[String])(implicit handler: Handler[A]): ConfigEntry[A] =
    new Basic(settings, BaseConfig.joinPath(path), handler)

  /** Builds a config entry for a type with implicit handler. */
  def from[A](settings: PortableSettings, path: String*)(implicit handler: Handler[A], d: DummyImplicit): ConfigEntry[A] =
    from(settings, path.toSeq)

  /** Builds a config entry for a given Enumeration. */
  def from[A <: Enumeration](settings: PortableSettings, enum: A, path: Seq[String]): ConfigEntry[A#Value] =
    new Basic(settings, BaseConfig.joinPath(path), enumerationHandler(enum))

  /** Builds a config entry for a given Enumeration. */
  def from[A <: Enumeration](settings: PortableSettings, enum: A, path: String*)(implicit d: DummyImplicit): ConfigEntry[A#Value] =
    from(settings, enum, path.toSeq)

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
