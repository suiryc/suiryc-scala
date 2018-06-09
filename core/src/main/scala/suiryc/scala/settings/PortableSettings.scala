package suiryc.scala.settings

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions, ConfigValue}
import com.typesafe.scalalogging.StrictLogging
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

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
class PortableSettings(filepath: Path, var _config: Config) {

  /** Whether config was already backuped. */
  private var backupDone = false

  /** The underlying config. */
  def config: Config = _config

  /** Changes a path value. */
  def withValue(path: String, value: ConfigValue): Config = {
    backup()
    _config = _config.withValue(path, value)
    save()
    _config
  }

  /** Removes a path. */
  def withoutPath(path: String): Config = {
    backup()
    _config = _config.withoutPath(path)
    save()
    _config
  }

  /** Saves the config. */
  def save(): Unit = this.synchronized {
    save(filepath)
  }

  /** Backups the config. */
  protected def backup(): Unit = this.synchronized {
    if (!backupDone) {
      save(PortableSettings.backupPath(filepath))
      backupDone = true
    }
  }

  /** Actual config saving (given file path). */
  protected def save(path: Path): Unit = {
    val renderOptions = ConfigRenderOptions.defaults.setOriginComments(false).setJson(false)
    Files.write(path, config.root.render(renderOptions).getBytes(StandardCharsets.UTF_8))
    ()
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
    val config = appConfig.withFallback(defaultReference(confpath:_*))
    new PortableSettings(filepath, config)
  }

  private def backupPath(filepath: Path): Path = {
    filepath.resolveSibling(filepath.getFileName.toString + ".bak")
  }

}
