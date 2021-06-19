package suiryc.scala.javafx.beans.binding

import java.lang.{Boolean => jBoolean}
import java.util.{ArrayList => jArrayList}
import javafx.beans.Observable
import javafx.beans.binding.{Binding, Bindings}
import javafx.beans.property.Property
import javafx.beans.value.ObservableValue
import monix.execution.Scheduler
import scala.concurrent.duration.FiniteDuration
import suiryc.scala.akka.CoreSystem
import suiryc.scala.concurrent.Cancellable
import suiryc.scala.javafx.beans.RichObservable
import suiryc.scala.javafx.beans.value.RichObservableValue
import suiryc.scala.javafx.concurrent.JFXSystem
import suiryc.scala.util.{CallThrottler, CallsThrottler}

/**
 * Bindings helpers.
 *
 * When necessary, can enforce the values are updated through a given execution
 * context (e.g. to make sure JavaFX component properties are updated from the
 * JavaFX thread even though observed values are modified from other threads).
 * The downside is that, since it's next to impossible for us to know whether
 * we already are in the wanted execution context, the updated value may not
 * (depending on the execution context actual implementation) be changed
 * synchronously (to observed ones) when we are in the wanted execution context.
 */
object BindingsEx {

  // Notes:
  // Listeners use weak references, so it's not a good idea to use them with
  // objects that only the listener points to: the object will be reclaimed by
  // GC and listener stop working.
  // e.g. if a Binding is created and not bound to target property, listening
  // to its changes in order to propagate value changes won't work as expected.
  //
  // We can only listen to invalidation with a simple Observable: it is the role
  // of associated code (which computes new value) to reset its invalidation
  // e.g. by reading the actual underlying value.
  // For bindings without throttling, an issue may arise since the invalidation
  // may be done asynchronously (through target execution context), in which
  // case some intermediate changes in the observed values may be ignored.
  // To prevent this we can instead listen to ObservableValues, since then
  // invalidation is automatically reset (and is done before delegating the
  // target value update to the execution context).
  // When using throttling, it is on the contrary better to listen to Observable
  // so that invalidation is not reset by us until actual call is performed,
  // which limits greatly the use of throttling ('throttle' calls) if nothing
  // else invalidates it: with an ObservableValue, since the invalidation is
  // reset automatically, every value change would trigger a 'throttle' call.

  /**
   * Binding helper.
   *
   * Can create a Binding from a function that returns a value that may not be
   * of the same type as the actual Binding.
   * Typically used to create a link between scala base types and java ones,
   * and/or automatically call the appropriate Bindings.createXXXBinding.
   */
  trait Linker[A, B] {
    def create(f: () => A, dependencies: Seq[Observable]): Binding[B]
  }

  /**
   * Binds target with a unidirection binding.
   *
   * A real Binding is created: the target property is bound after this function
   * returns.
   *
   * Reminder: since dependencies are listened as Observables, it is expected
   * that the given function callback resets its invalidation state (e.g. by
   * getting the underlying value). Otherwise not all changes will be taken
   * into account.
   *
   * @param target target property to update
   * @param dependencies values to observe
   * @param f function to compute updated value
   * @param linker creates the actual Binding; links the function value to the
   *               target property
   */
  def bind[A, B](target: Property[B], dependencies: Seq[Observable])(f: => A)(implicit linker: Linker[A, B]): Unit = {
    target.bind(linker.create(() => f, dependencies))
  }

  /**
   * Binds target with a unidirection binding.
   *
   * vararg variant.
   */
  def bind[A, B](target: Property[B], dependencies: Observable*)(f: => A)(implicit linker: Linker[A, B], d: DummyImplicit): Unit = {
    bind(target, dependencies.toSeq)(f)
  }

  /**
   * Binds target with a unidirection binding in JavaFX execution context.
   *
   * This function does not create a true Binding.
   * The target value change is guaranteed to be done inside JavaFX thread.
   * If a real Binding is necessary (in particular to make the target property
   * bound), the Builder can be used.
   *
   * @param target target property to update
   * @param dependencies values to observe
   * @param f function to compute updated value
   * @return Cancellable to stop observing values
   */
  def jfxBind[A](target: Property[A], dependencies: Seq[ObservableValue[_]])(f: => A): Cancellable = {
    val cancellable = RichObservableValue.listen[Any](dependencies) {
      JFXSystem.run(target.setValue(f))
    }
    // With proper binding, initial value is pushed to target. Do it too.
    JFXSystem.run(target.setValue(f))
    cancellable
  }

  /**
   * Binds target with a unidirection binding in JavaFX execution context.
   *
   * vararg variant.
   */
  def jfxBind[A](target: Property[A], dependencies: ObservableValue[_]*)(f: => A)(implicit d: DummyImplicit): Cancellable = {
    jfxBind(target, dependencies.toSeq)(f)
  }

  /**
   * Binds target with a throttled unidirection binding.
   *
   * Uses the Builder (with only our one target added).
   * This function does not create a true Binding, mainly because throttling
   * does not require it to update the target value, and this feature does not
   * conform to a real Binding (intermediate value changes are ignored during
   * throttling).
   * If a real Binding is necessary (in particular to make the target property
   * bound), the Builder can be used explicitly.
   *
   * Reminder: since dependencies are listened as Observables, it is expected
   * that the given function callback resets its invalidation state (e.g. by
   * getting the underlying value). Otherwise not all changes will be taken
   * into account.
   *
   * @param target target property to update
   * @param throttle throttling duration
   * @param scheduler scheduler used upon throttling
   * @param dependencies values to observe
   * @param f function to compute updated value
   * @return Cancellable to stop observing values
   */
  def bind[A](target: Property[A], throttle: FiniteDuration, scheduler: Scheduler,
    dependencies: Seq[Observable])(f: => A): Cancellable =
  {
    new Builder(scheduler) {
      add(target)(f)
    }.bind(throttle, dependencies: _*)
  }

  /**
   * Binds target with a throttled unidirection binding.
   *
   * vararg variant.
   */
  def bind[A](target: Property[A], throttle: FiniteDuration, scheduler: Scheduler,
    dependencies: Observable*)(f: => A)(implicit d: DummyImplicit): Cancellable =
  {
    bind(target, throttle, scheduler, dependencies.toSeq)(f)
  }

  /**
   * Binds target with a throttled unidirection binding.
   *
   * Uses the Builder (with only our one target added).
   * This function does not create a true Binding, mainly because throttling
   * does not require it to update the target value, and this feature does not
   * conform to a real Binding (intermediate value changes are ignored during
   * throttling).
   * If a real Binding is necessary (in particular to make the target property
   * bound), the Builder can be used explicitly.
   *
   * Reminder: since dependencies are listened as Observables, it is expected
   * that the given function callback resets its invalidation state (e.g. by
   * getting the underlying value). Otherwise not all changes will be taken
   * into account.
   *
   * @param target target property to update
   * @param throttle throttling duration
   * @param throttler calls throttler to use upon throttling
   * @param dependencies values to observe
   * @param f function to compute updated value
   * @return Cancellable to stop observing values
   */
  def bind[A](target: Property[A], throttle: FiniteDuration, throttler: CallsThrottler,
    dependencies: Seq[Observable])(f: => A): Cancellable =
  {
    new Builder(throttler) {
      add(target)(f)
    }.bind(throttle, dependencies: _*)
  }

  /**
   * Binds target with a throttled unidirection binding.
   *
   * vararg variant.
   */
  def bind[A](target: Property[A], throttle: FiniteDuration, throttler: CallsThrottler,
    dependencies: Observable*)(f: => A)(implicit d: DummyImplicit): Cancellable =
  {
    bind(target, throttle, throttler, dependencies.toSeq)(f)
  }

  /**
   * Builder able to create multiple bindings with the same observed values.
   *
   * More than one target can be added for the same observed values.
   * Throttling can be used.
   * When given, the target value is updated inside the execution context of
   * the scheduler (also used upon throttling).
   *
   * Side effects can be associated to the bindings: they will be executed once
   * when all bindings are updated.
   *
   * @param schedulerOpt optional scheduler to use upon throttling; target value
   *                     is also updated through its execution context
   * @param throttlerOpt optional calls throttler to use upon throttling; calls
   *                     are grouped by throttling and executed together
   */
  class Builder(schedulerOpt: Option[Scheduler], throttlerOpt: Option[CallsThrottler]) {

    // Bindings
    private val bindings = new jArrayList[() => Unit]()
    // Extra side effect codes
    private var sideEffects = Set.empty[() => Any]

    def this() = {
      this(None, None)
    }

    def this(scheduler: Scheduler) = {
      this(Some(scheduler), None)
    }

    def this(throttler: CallsThrottler) = {
      this(None, Some(throttler))
    }

    // Actually updates the target value.
    // Execution is done in the scheduler execution context when applicable.
    @inline
    private def update(inEC: Boolean, withSideEffects: Boolean): Unit = {
      val it = bindings.iterator
      @scala.annotation.tailrec
      def loop(): Unit = {
        if (it.hasNext) {
          it.next()()
          loop()
        }
      }
      @inline
      def _update(): Unit = {
        loop()
        if (withSideEffects) sideEffects.foreach(_())
      }
      if (inEC || schedulerOpt.isEmpty) _update()
      else schedulerOpt.get.execute(() => _update())
    }

    /**
     * Binds target with a unidirection binding.
     *
     * If requested a true Binding is created (target value is bound).
     * Otherwise a simple function to update the target value is created.
     *
     * @param target target property to update
     * @param direct whether to directly update target, or create a true Binding
     * @param f function to compute updated value
     * @return this builder
     */
    def add[A](target: Property[A], direct: Boolean = true)(f: => A): Builder = {
      if (direct) {
        bindings.add(() => target.setValue(f))
        ()
      } else {
        val binding = Bindings.createObjectBinding[A](() => f)
        bindings.add(() => binding.invalidate())
        target.bind(binding)
      }
      this
    }

    /**
     * Adds a side effect.
     *
     * The function will be called after all bindings are updated.
     * If a calls throttler has been passed, each side effect is registered as
     * an independent call: if the same side effect is used in another binding
     * with the same throttling, the side effect will only be applied once for
     * all the bindings.
     *
     * @param f side effect code
     * @return this builder
     */
    def sideEffect(f: () => Any): Builder = {
      sideEffects += f
      this
    }

    /**
     * Binds the targets.
     *
     * @param dependencies values to observe
     * @return Cancellable to stop observing values
     */
    def bind(dependencies: Seq[ObservableValue[_]]): Cancellable = {
      val cancellable = RichObservableValue.listen[Any](dependencies) {
        update(inEC = false, withSideEffects = true)
      }
      update(inEC = false, withSideEffects = true)
      cancellable
    }

    /**
     * Binds the targets.
     *
     * vararg variant.
     */
    def bind(dependencies: ObservableValue[_]*)(implicit d: DummyImplicit): Cancellable = {
      bind(dependencies.toSeq)
    }

    /**
     * Binds the targets using throttling.
     *
     * Reminder: since dependencies are listened as Observables, it is expected
     * that the associated function callbacks reset its invalidation state (e.g.
     * by getting the underlying value). Otherwise not all changes will be taken
     * into account.
     *
     * @param throttle throttling duration
     * @param dependencies values to observe
     * @return Cancellable to stop observing values
     */
    def bind(throttle: FiniteDuration, dependencies: Seq[Observable]): Cancellable = {
      val callThrottlers = throttlerOpt match {
        case Some(throttler) =>
          // Grouped code execution throttling.
          // Side effects can be registered as independent calls so that if
          // used with other bindings only one call will be performed.
          throttler.callThrottler(throttle) { () =>
            update(inEC = true, withSideEffects = false)
          } :: sideEffects.toList.map(throttler.callThrottler(throttle))

        case None =>
          // Single code execution throttling.
          List(CallThrottler(schedulerOpt.getOrElse(CoreSystem.scheduler), throttle) { inEC =>
            update(inEC || schedulerOpt.isEmpty, withSideEffects = true)
          })
      }
      @inline
      def _update(): Unit = callThrottlers.foreach(_.throttle())
      val cancellable = RichObservable.listen(dependencies) {
        _update()
      }
      _update()
      cancellable
    }

    /**
     * Binds the targets using throttling.
     *
     * vararg variant.
     */
    def bind(throttle: FiniteDuration, dependencies: Observable*)(implicit d: DummyImplicit): Cancellable = {
      bind(throttle, dependencies.toSeq)
    }

  }

  // Simple binding linker from scala to java Boolean
  implicit val booleanLinker: Linker[Boolean, jBoolean] =
    (f: () => Boolean, dependencies: Seq[Observable]) => Bindings.createBooleanBinding(() => f(), dependencies: _*)
  // Simple binding linker from scala to java Double
  implicit val doubleLinker: Linker[Double, Number] =
    (f: () => Double, dependencies: Seq[Observable]) => Bindings.createDoubleBinding(() => f(), dependencies: _*)
  // Simple binding linker from scala to java Float
  implicit val floatLinker: Linker[Float, Number] =
    (f: () => Float, dependencies: Seq[Observable]) => Bindings.createFloatBinding(() => f(), dependencies: _*)
  // Simple binding linker from scala to java Int
  implicit val integerLinker: Linker[Int, Number] =
    (f: () => Int, dependencies: Seq[Observable]) => Bindings.createIntegerBinding(() => f(), dependencies: _*)
  // Simple binding linker from scala to java Long
  implicit val longLinker: Linker[Long, Number] =
    (f: () => Long, dependencies: Seq[Observable]) => Bindings.createLongBinding(() => f(), dependencies: _*)
  // Simple binding linker for string values
  implicit val stringLinker: Linker[String, String] =
    (f: () => String, dependencies: Seq[Observable]) => Bindings.createStringBinding(() => f(), dependencies: _*)
  // Simple binding linker for object values
  implicit def objectLinker[A <: B, B]: Linker[A, B] =
    (f: () => A, dependencies: Seq[Observable]) => Bindings.createObjectBinding[B](() => f(), dependencies: _*)

}
