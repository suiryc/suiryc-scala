package suiryc.scala.settings

import javafx.beans.property.{Property, SimpleObjectProperty}

/**
 * Setting snapshot.
 *
 * Allows to determine if a setting was changed and reset it.
 */
trait SettingSnapshot[A] extends Snapshot {

  // Notes:
  // We wish to
  //  - allow changing values (e.g. through an UI) without applying them until
  //    needed/validated
  //  - know whether there is a change to apply
  //  - be able to reset value to a default when applicable
  //  - have an optional side-effect if we apply a value change
  //
  // To do so, along the underlying setting, we
  //  - keep any possible change in a 'draft' value
  //  - remember a possible default value (initial value by default)
  //  - have code to change draft value (from external input, e.g. UI)
  //  - have code to call on change (side-effect)
  // The draft is kept as a Property so that caller may listen to any change
  // (e.g. to trigger side-effect when resetting to default value).
  //
  // Special note:
  // Legacy code was changing underlying setting value externally, use 'changed'
  // to determine whether any change was actually done, and when applicable
  // would call 'reset' to revert changes.
  // Going through an intermediate 'draft' value is safer, and associating code
  // to refresh the value makes the caller job easier.

  /** Original value. */
  protected val originalValue: A = getValue

  /** Default value (original one by default). */
  protected var defaultValue: A = originalValue

  /** Code to execute upon actual value change (side-effect). */
  protected var onChange: Option[A => Unit] = None

  /** Code to execute to refresh draft value (from external input). */
  protected var onRefreshDraft: Option[() => A] = None

  /** Draft value (prepared to apply value change). */
  val draft = new SimpleObjectProperty[A](originalValue)

  /** Gets underlying setting value. */
  protected def getValue: A

  /** Sets underlying setting value. */
  protected def setValue(v: A): Unit

  /**
   * Changes underlying setting value.
   *
   * Sets value and executes associated code if any.
   */
  protected def applyChange(v: A): Unit = {
    setValue(v)
    onChange.foreach(_(v))
  }

  /** Sets default value. */
  def withDefault(v: A): this.type = {
    defaultValue = v
    this
  }

  /** Sets code to execute upon actual value change. */
  def setOnChange(f: A => Unit): this.type = {
    onChange = Some(f)
    this
  }

  /** Sets code to execute to refresh draft value (form external input). */
  def setOnRefreshDraft(f: => A): this.type = {
    onRefreshDraft = Some(() => f)
    this
  }

  /** Gets whether the underlying setting value was changed. */
  override def changed(): Boolean = originalValue != getValue

  /** Resets the setting to its initial value. */
  override def reset(): Unit = if (changed()) applyChange(originalValue)

  /**
   * Gets draft/refresh value.
   *
   * Gets current draft value.
   * If requested, gets the refreshed value (without actually refreshing the
   * draft value).
   */
  def getDraftValue(refreshed: Boolean = true): A = {
    if (refreshed) onRefreshDraft.map(_()).getOrElse(draft.get)
    else draft.get
  }

  /**
   * Whether draft value was changed.
   *
   * Compares current/refreshed draft value to original or default one.
   */
  override def isDraftChanged(original: Boolean = true, refreshed: Boolean = true): Boolean = {
    val draftValue = getDraftValue(refreshed)
    if (original) draftValue != originalValue
    else draftValue != defaultValue
  }

  /** Refreshes, applies draft value and returns whether it was changed. */
  override def applyDraft(): Boolean = {
    refreshDraft()
    if (isDraftChanged(refreshed = false)) {
      applyChange(draft.get)
      true
    } else false
  }

  /** Refreshes draft value. */
  override def refreshDraft(): Unit = onRefreshDraft.foreach(f => draft.set(f()))

  /**
   * Sets draft to original or default value.
   *
   * If asked, first refresh the draft value (useful to trigger value change
   * if the draft value was not up-to-date yet).
   */
  override def resetDraft(original: Boolean = true, refresh: Boolean = true): Unit = {
    if (refresh) refreshDraft()
    if (original) draft.set(originalValue)
    else draft.set(defaultValue)
  }

}

object SettingSnapshot {

  /** Builds a snapshot from a property. */
  def apply[A](property: Property[A]): SettingSnapshot[A] =
    new SettingSnapshot[A] {
      override protected def getValue: A = property.getValue
      override protected def setValue(v: A): Unit = property.setValue(v)
    }

  /** Builds a snapshot from a (portable setting) config entry. */
  def apply[A](setting: ConfigEntry[A]): ConfigStdEntrySnapshot[A] =
    new ConfigStdEntrySnapshot[A](setting) {
      override protected def getValue: A = setting.get
      override protected def setValue(v: A): Unit = setting.set(v)
    }

  /** Builds a snapshot from an optional (portable setting) config entry. */
  def opt[A](setting: ConfigEntry[A]): ConfigOptEntrySnapshot[A] =
    new ConfigOptEntrySnapshot[A](setting) {
      override protected def getValue: Option[A] = setting.opt
      override protected def setValue(v: Option[A]): Unit = {
        v match {
          case Some(a) => setting.set(a)
          case None => setting.reset()
        }
      }
    }

}

/**
 * Generic snapshot feature.
 *
 * Abstracts the most generic needs. From a user point of view, whether a
 * snapshot points to one or many settings does not really matter: what usually
 * matters is whether there is a pending change, and reset/apply the change.
 * This trait helps build simple (one value) and complex (list of values,
 * possibly recursive) snapshots around those generic features.
 */
trait Snapshot {

  /** Gets whether the setting value was changed. */
  def changed(): Boolean

  /** Resets the setting to its initial value. */
  def reset(): Unit

  /**
   * Whether draft value was changed.
   *
   * Compares current/refreshed draft value to original or default one.
   */
  def isDraftChanged(original: Boolean = true, refreshed: Boolean = true): Boolean

  /** Refreshes, applies draft value and returns whether it was changed. */
  def applyDraft(): Boolean

  /** Refreshes draft value. */
  def refreshDraft(): Unit

  /**
   * Sets draft to original or default value.
   *
   * If asked, first refresh the draft value (useful to trigger value change
   * if the draft value was not up-to-date yet).
   */
  def resetDraft(original: Boolean = true, refresh: Boolean = true): Unit

}

/**
 * Settings snapshot.
 *
 * Hold a list of snapshots. Allows to determine if any changed, and reset
 * them all.
 */
class Snapshots[A <: Snapshot] extends Snapshot {

  /** Managed snapshots. */
  protected var snapshots: List[A] = Nil

  /** Gets currently managed snapshots. */
  def getSnapshots: List[A] = snapshots

  /** Adds setting snapshots. */
  def add(others: Seq[A]): Unit = snapshots ++= others.toList

  /** Adds setting snapshots. (vararg variant) */
  def add(others: A*)(implicit d: DummyImplicit): Unit = add(others.toSeq)

  /** Gets whether any snapshot changed. */
  override def changed(): Boolean = snapshots.exists(_.changed())

  /** Resets all snapshots. */
  override def reset(): Unit = snapshots.foreach(_.reset())

  /**
   * Whether any draft value was changed.
   *
   * Compares current/refreshed draft value to original or default one.
   */
  override def isDraftChanged(original: Boolean = true, refreshed: Boolean = true): Boolean =
    snapshots.exists(_.isDraftChanged(original, refreshed))

  /** Refreshes, applies drafts values and returns whether any was changed. */
  override def applyDraft(): Boolean = snapshots.foldLeft(false) { (changed, snapshot) =>
    // Note: always apply draft, then merge 'changed' result
    snapshot.applyDraft() || changed
  }

  /** Refreshes drafts values. */
  override def refreshDraft(): Unit = snapshots.foreach(_.refreshDraft())

  /** Sets drafts to original or default value. */
  override def resetDraft(original: Boolean = true, refresh: Boolean = true): Unit =
    snapshots.foreach(_.resetDraft(original, refresh))

}

class SettingsSnapshot extends Snapshots[Snapshot] with Snapshot
