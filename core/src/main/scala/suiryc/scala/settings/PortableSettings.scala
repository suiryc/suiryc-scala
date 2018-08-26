package suiryc.scala.settings

import com.typesafe.config._
import com.typesafe.scalalogging.StrictLogging
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import suiryc.scala.io.PathsEx

/**
 * Portable settings.
 *
 * Handles application configuration stored in a given file path.
 * ConfigEntry is used to access a given entry (by path). Changing its value
 * updates the underlying config.
 *
 * First change triggers a backup of the original config.
 * After each change the file is updated.
 */
class PortableSettings(filepath: Path, private var _config: Config, val reference: Config) {

  /** The standalone config (without reference fallback). */
  private var _configStandalone: Config = updateStandalone(_config)

  /** Whether config was already backuped. */
  private var backupDone = false

  /** Whether saving is currently delayed and needed. */
  private var needSave = Option.empty[Boolean]

  private def updateStandalone(c: Config): Config = {
    _configStandalone = c
    // We always keep reference as fallback
    _config = _configStandalone.withFallback(reference).resolve()
    _configStandalone
  }

  /** The underlying config. */
  def config: Config = _config

  /** Changes a path value. */
  def withValue(path: String, value: ConfigValue): Unit = {
    // Do nothing if value did not change
    if (!_config.hasPath(path) || (_config.getValue(path) != value)) {
      backup()
      // Keep origin when applicable
      // (take care of ConfigNull values too)
      val actual =
        if (reference.hasPathOrNull(path)) value.withOrigin(getValue(reference, path).origin)
        else value
      updateStandalone(_configStandalone.withValue(path, actual))
      save()
    }
  }

  /** Removes a path (but still fallbacks to the reference). */
  def withoutPath(path: String): Unit = {
    // Do nothing if path was already absent
    if (_configStandalone.hasPathOrNull(path)) {
      backup()
      updateStandalone(_configStandalone.withoutPath(path))
      save()
    }
  }

  /** Enables/disables saving delaying. */
  def setDelaySave(delay: Boolean): Unit = {
    if (delay) {
      if (needSave.isEmpty) needSave = Some(false)
    } else {
      val needed = needSave.contains(true)
      needSave = None
      if (needed) save()
    }
  }

  /** Delay saving while executing code. */
  def delayedSave[A](f: ⇒ A): A = {
    setDelaySave(delay = true)
    try {
      f
    } finally {
      setDelaySave(delay = false)
    }
  }

  /** Saves the config. */
  def save(): Unit = this.synchronized {
    if (needSave.isDefined) needSave = Some(true)
    else save(filepath, backup = false)
  }

  /** Backups the config. */
  protected def backup(): Unit = this.synchronized {
    if (!backupDone) {
      save(PathsEx.backupPath(filepath), backup = true)
      backupDone = true
    }
  }

  /** Actual config saving (given file path). */
  protected def save(path: Path, backup: Boolean): Unit = {
    clean(_configStandalone) match {
      case Some(cleanedConfig) ⇒
        val renderOptions = ConfigRenderOptions.defaults.setOriginComments(false).setJson(false)
        Files.write(path, cleanedConfig.root.render(renderOptions).getBytes(StandardCharsets.UTF_8))

      case None ⇒
        // We could delete the configuration as there is no change from the
        // reference. But it's more user-friendly to simply say so in the file.
        // We only delete the backup file in this case.
        if (backup && Files.exists(path)) Files.delete(path)
        if (!backup) Files.write(path, "# No value overrides the reference configuration\n".getBytes(StandardCharsets.UTF_8))
    }
    ()
  }

  // Gets value, even if null (ConfigNull).
  // Config.getValue throws Exception for null values. To workaround this we
  // need to go through ConfigObject.get which works with keys (direct children)
  // instead of paths. Use ConfigUtil.splitPath to determine the children
  // hierarchy to go through to get the value.
  private def getValue(config: Config, path: String): ConfigValue = {
    // Get the value directly, throws an appropriate exception when applicable
    def direct(): ConfigValue = config.getValue(path)

    @scala.annotation.tailrec
    def loop(value: ConfigValue, path: List[String]): ConfigValue = {
      // If this is the end ot the path, we got our value
      if (path.isEmpty) value
      else if (value.valueType != ConfigValueType.OBJECT) {
        // Should not happen: we checked that the path existed
        direct()
      } else {
        // Process child value (its key is the head of the path)
        val head = path.head
        val tail = path.tail
        Option(value.asInstanceOf[ConfigObject].get(head)) match {
          case Some(obj) ⇒
            loop(obj, tail)

          case None ⇒
            // Should not happen: we checked that the path existed
            direct()
        }
      }
    }

    if (!config.hasPathOrNull(path) || !config.getIsNull(path)) direct()
    else loop(config.root, ConfigUtil.splitPath(path).asScala.toList)
  }

  // Finds all paths (and values, even if null).
  // Config.entrySet does filter null values. To workaround this, we need to
  // go through ConfigObject.entrySet (returns direct children) recursively.
  private def findPaths(obj: ConfigObject): Map[String, ConfigValue] = {
    @scala.annotation.tailrec
    def loop(entries: Map[String, ConfigValue], objs: List[(List[String], ConfigObject)]): Map[String, ConfigValue] = {
      // If there is no more object to process, we are done
      if (objs.isEmpty) entries
      else {
        val head = objs.head
        val tail = objs.tail
        val path = head._1
        val obj = head._2
        // Get this object content, adding simple values to entries to return
        // and objects to those left to process.
        val (entries2, objs2) = obj.entrySet.asScala.toList.foldLeft((entries, tail)) { case ((entries0, objs0), entry) ⇒
          val key = entry.getKey
          val path2 = path :+ key
          val value = entry.getValue
          if (value.valueType != ConfigValueType.OBJECT) (entries0 + (ConfigUtil.joinPath(path2:_*) → value), objs0)
          else (entries0, objs0 :+ (path2 → value.asInstanceOf[ConfigObject]))
        }
        loop(entries2, objs2)
      }
    }

    loop(Map.empty, List(Nil → obj))
  }

  // Gets whether an object is empty.
  // Object is empty it it contains nothing or children that are empty.
  private def isEmpty(obj: ConfigObject): Boolean = {
    @scala.annotation.tailrec
    def loop(objs: List[ConfigObject]): Boolean = {
      // If there is no more entry to check, we are empty
      if (objs.isEmpty) true
      else {
        val head = objs.head
        val tail = objs.tail
        // If next object is empty, check the rest
        if (head.isEmpty) loop(objs.tail)
        else {
          val values = head.values
          val children = values.asScala.toList.takeWhile(_.valueType == ConfigValueType.OBJECT)
          // If there are non objects, we are not empty; otherwise check all
          // children are empty.
          if (values.size != children.size) false
          else loop(tail ::: children.map(_.asInstanceOf[ConfigObject]))
        }
      }
    }

    loop(List(obj))
  }

  private case class ObjectEntry(path: List[String], value: ConfigObject)

  // Cleans configuration.
  // Removes null entries or with default values, and empty objects.
  // Returns remaining configuration, or None if empty.
  // Note: this is a last-time effort to drop useless entries in configuration
  // before saving it. In most cases (using ConfigEntry to access content), at
  // least default values can be taken care of by code holding those settings.
  private def clean(config: Config): Option[Config] = {
    @scala.annotation.tailrec
    def clean(config: Config, entries: List[ObjectEntry]): Option[Config] = {
      // If there is no more entry to check, we are done
      if (entries.isEmpty) Some(config)
      else {
        val head = entries.head
        val tail = entries.tail
        // If next entry is empty, remove its path and check the rest
        if (isEmpty(head.value)) {
          // If we are to remove the root path, whole config is empty
          if (head.path.isEmpty) None
          else clean(config.withoutPath(BaseConfig.joinPath(head.path)), tail)
        } else {
          // Adds children objects for next.
          val newEntries = head.value.entrySet.asScala.toList.filter(_.getValue.valueType == ConfigValueType.OBJECT).map { entry ⇒
            ObjectEntry(
              path = head.path :+ entry.getKey,
              value = entry.getValue.asInstanceOf[ConfigObject]
            )
          }
          clean(config, tail ::: newEntries)
        }
      }
    }

    // We first remove unchanged values.
    // Take care of null values too.
    val cleaned1 = findPaths(config.root).foldLeft(config) { (acc, entry) ⇒
      val key = entry._1
      val value = entry._2
      if (reference.hasPathOrNull(key) && (getValue(reference, key) == value)) acc.withoutPath(key)
      else acc
    }

    // Then we can remove empty objects
    clean(cleaned1, List(ObjectEntry(Nil, cleaned1.root)))
  }

}

object PortableSettings extends StrictLogging {

  /** Gets the Config default reference (for the given config path). */
  def defaultReference(path: Seq[String]): Config = {
    val fullpath = BaseConfig.joinPath(path)
    val ref = ConfigFactory.defaultReference()
    if (ref.hasPath(fullpath)) ref.getConfig(fullpath).atPath(fullpath)
    else ConfigFactory.empty()
  }

  /** Gets the Config default reference (for the given config path). */
  def defaultReference(path: String*)(implicit d: DummyImplicit): Config = {
    defaultReference(path)
  }

  /**
   * Gets portable settings.
   *
   * If given file path cannot be parsed as a Config, backup path is used
   * instead.
   * In both cases, default reference is used as fallback.
   */
  def apply(filepath: Path, confpath: Seq[String]): PortableSettings = {
    val backup = PathsEx.backupPath(filepath).toFile
    val appConfig = try {
      ConfigFactory.parseFile(filepath.toFile)
    } catch {
      case ex: Exception if backup.exists ⇒
        logger.error(s"Failed to load configuration=<$filepath>, switching to backup: ${ex.getMessage}", ex)
        try {
          ConfigFactory.parseFile(backup)
        } catch {
          case _: Exception ⇒
            throw ex
        }
    }
    val ref = defaultReference(confpath)
    new PortableSettings(filepath, appConfig, ref)
  }

  /**
   * Gets portable settings.
   *
   * vararg variant.
   */
  def apply(filepath: Path, confpath: String*)(implicit d: DummyImplicit): PortableSettings = {
    apply(filepath, confpath)
  }

}
