package suiryc.scala.settings

import com.typesafe.config._
import com.typesafe.scalalogging.StrictLogging
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

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
class PortableSettings(filepath: Path, var _config: Config, ref: Config) {

  /** Whether config was already backuped. */
  private var backupDone = false

  /** The underlying config. */
  def config: Config = _config

  /** Changes a path value. */
  def withValue(path: String, value: ConfigValue): Config = {
    // Do nothing if value did not change
    if (!_config.hasPath(path) || (_config.getValue(path) != value)) {
      backup()
      // Keep origin when applicable
      val actual =
        if (ref.hasPath(path)) value.withOrigin(ref.getValue(path).origin)
        else value
      _config = _config.withValue(path, actual)
      save()
    }
    _config
  }

  /** Removes a path. */
  def withoutPath(path: String): Config = {
    // Do nothing if path was already absent
    if (_config.hasPath(path)) {
      backup()
      _config = _config.withoutPath(path)
      save()
    }
    _config
  }

  /** Saves the config. */
  def save(): Unit = this.synchronized {
    save(filepath, backup = false)
  }

  /** Backups the config. */
  protected def backup(): Unit = this.synchronized {
    if (!backupDone) {
      save(PortableSettings.backupPath(filepath), backup = true)
      backupDone = true
    }
  }

  /** Actual config saving (given file path). */
  protected def save(path: Path, backup: Boolean): Unit = {
    clean(config) match {
      case Some(cleanedConfig) =>
        val renderOptions = ConfigRenderOptions.defaults.setOriginComments(false).setJson(false)
        Files.write(path, cleanedConfig.root.render(renderOptions).getBytes(StandardCharsets.UTF_8))

      case None =>
        // We could delete the configuration as there is no change from the
        // reference. But it's more user-friendly to simply say so in the file.
        // We only delete the backup file in this case.
        if (backup && Files.exists(path)) Files.delete(path)
        if (!backup) Files.write(path, "# No value overrides the reference configuration\n".getBytes(StandardCharsets.UTF_8))
    }
    ()
  }

  @scala.annotation.tailrec
  private def isEmpty(objs: List[ConfigObject]): Boolean = {
    // If there is no more entry to check, we are empty
    if (objs.isEmpty) true
    else {
      val head = objs.head
      val tail = objs.tail
      // If next object is empty, check the rest
      if (head.isEmpty) isEmpty(objs.tail)
      else {
        val values = head.values
        val children = values.asScala.toList.takeWhile(_.valueType == ConfigValueType.OBJECT)
        // If there are non objects, we are not empty; otherwise check all
        // children are empty.
        if (values.size != children.size) false
        else isEmpty(tail ::: children.map(_.asInstanceOf[ConfigObject]))
      }
    }
  }

  private case class ObjectEntry(path: String, value: ConfigObject)

  private def clean(config: Config): Option[Config] = {
    @scala.annotation.tailrec
    def clean(config: Config, entries: List[ObjectEntry]): Option[Config] = {
      // If there is no more entry to check, we are done
      if (entries.isEmpty) Some(config)
      else {
        val head = entries.head
        val tail = entries.tail
        // If next entry is empty, remove its path and check the rest
        if (isEmpty(List(head.value))) {
          // If we are to remove the root path, whole config is empty
          if (head.path.isEmpty) None
          else clean(config.withoutPath(head.path), tail)
        } else {
          // Adds children objects for next.
          val newEntries = head.value.entrySet.asScala.toList.filter(_.getValue.valueType == ConfigValueType.OBJECT).map { entry =>
            ObjectEntry(
              path = List(head.path, entry.getKey).filter(_.length > 0).mkString("."),
              value = entry.getValue.asInstanceOf[ConfigObject]
            )
          }
          clean(config, tail ::: newEntries)
        }
      }
    }

    // We first remove unchanged values
    val cleaned1 = config.entrySet.asScala.foldLeft(config) { (acc, entry) =>
      val key = entry.getKey
      if (ref.hasPath(key) && ref.getValue(key) == entry.getValue) acc.withoutPath(key)
      else acc
    }

    // Then we can remove empty objects
    clean(cleaned1, List(ObjectEntry("", cleaned1.root)))
  }

}

object PortableSettings extends StrictLogging {

  /** Gets the Config default reference (for the given config path). */
  def defaultReference(path: String*): Config = {
    val fullpath = path.mkString(".")
    ConfigFactory.defaultReference().getConfig(fullpath).atPath(fullpath)
  }

  /**
   * Gets portable settings.
   *
   * If given file path cannot be parsed as a Config, backup path is used
   * instead.
   * In both cases, default reference is used as fallback.
   */
  def apply(filepath: Path, confpath: String*): PortableSettings = {
    val backup = backupPath(filepath).toFile
    val appConfig = try {
      ConfigFactory.parseFile(filepath.toFile)
    } catch {
      case ex: Exception if backup.exists =>
        logger.error(s"Failed to load configuration=<$filepath>, switching to backup: ${ex.getMessage}", ex)
        try {
          ConfigFactory.parseFile(backup)
        } catch {
          case _: Exception =>
            throw ex
        }
    }
    val ref = defaultReference(confpath:_*)
    val config = appConfig.withFallback(ref)
    new PortableSettings(filepath, config, ref)
  }

  private def backupPath(filepath: Path): Path = {
    filepath.resolveSibling(filepath.getFileName.toString + ".bak")
  }

}
