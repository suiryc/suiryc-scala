package suiryc.scala.settings

import javafx.beans.property.{Property, SimpleObjectProperty}

/**
 * Setting snapshot.
 *
 * Allows to determine if a setting was changed and reset it.
 */
trait SettingSnapshot[T] {

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
  protected val originalValue: T = getValue

  /** Default value (original one by default). */
  protected var defaultValue: T = originalValue

  /** Code to execute upon actual value change (side-effect). */
  protected var onChange: Option[T ⇒ Unit] = None

  /** Code to execute to refresh draft value (from external input). */
  protected var onRefreshDraft: Option[() ⇒ T] = None

  /** Draft value (prepared to apply value change). */
  val draft = new SimpleObjectProperty[T](originalValue)

  /** Gets underlying setting value. */
  protected def getValue: T

  /** Sets underlying setting value. */
  protected def setValue(v: T): Unit

  /**
   * Changes underlying setting value.
   *
   * Sets value and executes associated code if any.
   */
  protected def applyChange(v: T): Unit = {
    setValue(v)
    onChange.foreach(_(v))
  }

  /** Sets default value. */
  def withDefault(v: T): SettingSnapshot[T] = {
    defaultValue = v
    this
  }

  /** Sets code to execute upon actual value change. */
  def setOnChange(f: T ⇒ Unit): SettingSnapshot[T] = {
    onChange = Some(f)
    this
  }

  /** Sets code to execute to refresh draft value (form external input). */
  def setOnRefreshDraft(f: ⇒ T): SettingSnapshot[T] = {
    onRefreshDraft = Some(() ⇒ f)
    this
  }

  /** Gets whether the underlying setting value was changed. */
  def changed(): Boolean = originalValue != getValue

  /** Resets the setting to its initial value. */
  def reset(): Unit = if (changed()) applyChange(originalValue)

  /**
   * Gets draft/refresh value.
   *
   * Gets current draft value.
   * If requested, gets the refreshed value (without actually refreshing the
   * draft value).
   */
  def getDraftValue(refreshed: Boolean = true): T = {
    if (refreshed) onRefreshDraft.map(_()).getOrElse(draft.get)
    else draft.get
  }

  /**
   * Whether draft value was changed.
   *
   * Compares current/refreshed draft value to original or default one.
   */
  def isDraftChanged(original: Boolean = true, refreshed: Boolean = true): Boolean = {
    val draftValue = getDraftValue(refreshed)
    if (original) draftValue != originalValue
    else draftValue != defaultValue
  }

  /** Refreshes, applies draft value and returns whether it was changed. */
  def applyDraft(): Boolean = {
    refreshDraft()
    if (isDraftChanged(refreshed = false)) {
      applyChange(draft.get)
      true
    } else false
  }

  /** Refreshes draft value. */
  def refreshDraft(): Unit = onRefreshDraft.foreach(f ⇒ draft.set(f()))

  /**
   * Sets draft to original or default value.
   *
   * If asked, first refresh the draft value (useful to trigger value change
   * if the draft value was not up-to-date yet).
   */
  def resetDraft(original: Boolean = true, refresh: Boolean = true): Unit = {
    if (refresh) refreshDraft()
    if (original) draft.set(originalValue)
    else draft.set(defaultValue)
  }

}

object SettingSnapshot {

  /** Builds a snapshot from a Preference. */
  def apply[T](preference: Preference[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {
      override protected def getValue: T = preference()
      override protected def setValue(v: T): Unit = preference() = v
    }

  /** Builds a snapshot from a property. */
  def apply[T](property: Property[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {
      override protected def getValue: T = property.getValue
      override protected def setValue(v: T): Unit = property.setValue(v)
    }

  /** Builds a snapshot from a persistent setting. */
  def apply[T](setting: PersistentSetting[T]): SettingSnapshot[T] =
    new SettingSnapshot[T] {
      override protected def getValue: T = setting()
      override protected def setValue(v: T): Unit = setting() = v
    }

  /** Builds a snapshot from a (portable setting) object config entry. */
  def apply[A >: Null](setting: ConfigEntry[A]): SettingSnapshot[A] =
    new SettingSnapshot[A] {
      override protected def getValue: A = setting.opt.orNull
      override protected def setValue(v: A): Unit = {
        if (v == null) setting.remove()
        else setting.set(v)
      }
    }

  /** Builds a snapshot from a (portable setting) value config entry. */
  // Variant for values that cannot be null (Int, etc).
  def apply[A <: AnyVal](setting: ConfigEntry[A])(implicit d: DummyImplicit): SettingSnapshot[A] =
    new SettingSnapshot[A] {
      override protected def getValue: A = setting.get
      override protected def setValue(v: A): Unit = setting.set(v)
    }

}

/**
 * Settings snapshot.
 *
 * Hold a list of snapshots. Allows to determine if any changed, and reset
 * them all.
 */
class SettingsSnapshot {

  /** Setting snapshots. */
  private var snapshots: List[SettingSnapshot[_]] = Nil

  /** Adds setting snapshots. */
  def add(others: Seq[SettingSnapshot[_]]): Unit = snapshots ++= others.toList

  /** Adds setting snapshots. (vararg variant) */
  def add(others: SettingSnapshot[_]*)(implicit d: DummyImplicit): Unit = add(others)

  /** Gets whether any setting changed. */
  def changed(): Boolean = snapshots.exists(_.changed())

  /** Resets all settings. */
  def reset(): Unit = snapshots.foreach(_.reset())

  /**
   * Whether any draft value was changed.
   *
   * Compares current/refreshed draft value to original or default one.
   */
  def isDraftChanged(original: Boolean = true, refreshed: Boolean = true): Boolean =
    snapshots.exists(_.isDraftChanged(original, refreshed))

  /** Refreshes, applies drafts values and returns whether any was changed. */
  def applyDraft(): Boolean = snapshots.foldLeft(false)(_ || _.applyDraft())

  /** Refreshes drafts values. */
  def refreshDraft(): Unit = snapshots.foreach(_.refreshDraft())

  /** Sets drafts to original or default value. */
  def resetDraft(original: Boolean = true, refresh: Boolean = true): Unit =
    snapshots.foreach(_.resetDraft(original, refresh))

}
