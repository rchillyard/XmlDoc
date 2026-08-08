package com.phasmidsoftware.xml

import scala.language.implicitConversions
import scala.reflect.ClassTag

/**
 * Trait to allow for functions to have names.
 * A function, for example T=>R, can simply extend NamedFunction[T=>R] and toString will show the name.
 *
 * The name is held as a var. It is updated by the ^^ method.
 *
 * CONSIDER having a method which adds to the name but doesn't replace it.
 *
 * @tparam E the type of the function to be named.
 */
trait NamedFunction[E] {
    /**
     * Updates the name of the current instance and returns it as the specified type.
     *
     * @param w the new name to be assigned.
     * @return the instance of the current object cast to type E.
     */
    def ^^(w: String): E = {
        name = w
        this.asInstanceOf[E]
    }

    /**
     * Appends the given string to the current name, separated by a colon, and updates the name of the current instance.
     *
     * @param w the string to prepend to the current name.
     * @return the instance of the current object cast to type E.
     */
    def ^+(w: String): E = {
        name = s"$w: $name"
        this.asInstanceOf[E]
    }

    /**
     * Represents the name attribute of a `NamedFunction` instance.
     *
     * The `name` is a mutable string variable with a default value of "unnamed".
     * It can be updated via methods such as `^^` and `^+` in the `NamedFunction` trait.
     */
    var name: String = "unnamed"

    override def toString: String = name
}

/**
 * The `NamedFunction` object provides utilities for working with instances of the `NamedFunction` trait,
 * including methods to assert and combine named function instances.
 */
object NamedFunction {
    private def assertNamedNotNullMember[N: NamedFunction, T: ClassTag](caller: String, member: String): Unit = {
        // NOTE: for now we do nothing to actually evaluate the implicit evidence of NamedFunction[N].
//        Option(implicitly[NamedFunction[N]]) match {
//            case None =>
//                val kml: KmlRenderers.type = KmlRenderers
//                logger.warn(s"$caller: named function for $member of ${implicitly[ClassTag[T]]} is not initialized")
//                throw new AssertionError(s"named function for $member of ${implicitly[ClassTag[T]]} is not initialized")
//            case _ =>
//        }
    }

    /**
     * Ensures that a named function, along with its type constraints, is not null at runtime.
     * This method serves to validate that specific types implementing the `NamedFunction` trait
     * are properly instantiated and available for the calling context.
     *
     * @param caller the name of the invoking method or context, used for diagnostic messaging.
     * @tparam N the type of the named function that must have evidence of `NamedFunction`.
     * @tparam T the class type associated with the named function, requiring evidence of `ClassTag`.
     * @return Unit, as this method performs a validation check without producing a result.
     */
    def assertNamedNotNull[N: NamedFunction, T: ClassTag](caller: String): Unit = assertNamedNotNullMember("", "anonymous")

    /**
     * Provides the implicit `NamedFunction` instance for the specified type `P`.
     *
     * This method uses the context bound `P: NamedFunction` to ensure that an implicit `NamedFunction` instance
     * for the type `P` is available in the current scope.
     * If such an instance exists, it returns it using
     * the `implicitly` method.
     *
     * @tparam P the type for which the `NamedFunction` instance is required.
     * @return an instance of `NamedFunction[P]` from the implicit scope.
     */
    def named[P: NamedFunction]: NamedFunction[P] = implicitly[NamedFunction[P]]

    /**
     * Retrieves the name of the parameterized entity `P` by applying a `NamedFunction` to it.
     * If the name is not available, returns the string "uninitialized".
     *
     * @return A string representing the name of the parameterized entity `P` or "uninitialized" if the name is not set.
     */
    def name[P: NamedFunction]: String = Option(named[P]) map (_.name) getOrElse "uninitialized"

    /**
     * Combines the names of two instances of `NamedFunction` into a single string.
     *
     * This method retrieves the names of the two given types using the implicit evidence
     * of their `NamedFunction` instances and concatenates them with a "+" separator.
     *
     * @tparam T0 the type of the first `NamedFunction`.
     * @tparam T1 the type of the second `NamedFunction`.
     * @return a string representation of the combined names of the two `NamedFunction`s, separated by "+".
     */
    def combineNamed2[T0: NamedFunction, T1: NamedFunction]: String =
        combineNames2(named[T0], named[T1])

    /**
     * Combines the names of three instances of `NamedFunction` for specified types `T0`, `T1`, and `T2`.
     * The names are derived using the `named` method and concatenated using the `combineNames3` method.
     *
     * @tparam T0 the type of the first `NamedFunction`.
     * @tparam T1 the type of the second `NamedFunction`.
     * @tparam T2 the type of the third `NamedFunction`.
     * @return a concatenated string representation of the names of the three `NamedFunction` instances.
     */
    def combineNamed3[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction]: String =
        combineNames3(named[T0], named[T1], named[T2])

    /**
     * Combines the names of four types, each having an implicit `NamedFunction` instance.
     *
     * This method retrieves the `NamedFunction` instance for each type parameter (`T0`, `T1`, `T2`, `T3`),
     * extracts their names via the `name` method, and concatenates them using a specific combination logic
     * defined in the `combineNames4` method.
     *
     * @tparam T0 the first type parameter with an associated `NamedFunction`.
     * @tparam T1 the second type parameter with an associated `NamedFunction`.
     * @tparam T2 the third type parameter with an associated `NamedFunction`.
     * @tparam T3 the fourth type parameter with an associated `NamedFunction`.
     * @return a `String` representing the combined names of the four types, formatted according to the logic in `combineNames4`.
     */
    def combineNamed4[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction, T3: NamedFunction]: String =
        combineNames4(named[T0], named[T1], named[T2], named[T3])

    /**
     * Combines the names of five `NamedFunction` instances into a single string.
     * The names are retrieved using the `named` method and then combined using the `combineNames5` method.
     *
     * @tparam T0 the type of the first `NamedFunction`.
     * @tparam T1 the type of the second `NamedFunction`.
     * @tparam T2 the type of the third `NamedFunction`.
     * @tparam T3 the type of the fourth `NamedFunction`.
     * @tparam T4 the type of the fifth `NamedFunction`.
     * @return a string representing the combined names of the five `NamedFunction` instances.
     */
    def combineNamed5[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction, T3: NamedFunction, T4: NamedFunction]: String =
        combineNames5(named[T0], named[T1], named[T2], named[T3], named[T4])

    /**
     * Combines the names of six types, represented as instances of `NamedFunction`, into a single string.
     * The result is constructed by combining the result of `combineNamed5` for the first five types and
     * the name of the sixth type.
     *
     * @tparam T0 the first type parameter, required to have a `NamedFunction` evidence.
     * @tparam T1 the second type parameter, required to have a `NamedFunction` evidence.
     * @tparam T2 the third type parameter, required to have a `NamedFunction` evidence.
     * @tparam T3 the fourth type parameter, required to have a `NamedFunction` evidence.
     * @tparam T4 the fifth type parameter, required to have a `NamedFunction` evidence.
     * @tparam T5 the sixth type parameter, required to have a `NamedFunction` evidence.
     * @return a string representing the concatenated names of the six types, separated by "+".
     */
    def combineNamed6[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction, T3: NamedFunction, T4: NamedFunction, T5: NamedFunction]: String =
        combineNamed5[T0, T1, T2, T3, T4] + "+" + name[T5]

    /**
     * Combines the names of seven `NamedFunction` instances into a single string.
     * The resulting string is constructed by concatenating the combined names of the first six `NamedFunction` instances
     * using `combineNamed6`, followed by the name of the seventh `NamedFunction`, separated by a plus sign ("+").
     *
     * @tparam T0 the type of the first `NamedFunction`.
     * @tparam T1 the type of the second `NamedFunction`.
     * @tparam T2 the type of the third `NamedFunction`.
     * @tparam T3 the type of the fourth `NamedFunction`.
     * @tparam T4 the type of the fifth `NamedFunction`.
     * @tparam T5 the type of the sixth `NamedFunction`.
     * @tparam T6 the type of the seventh `NamedFunction`.
     * @return a string composed of the combined names of the seven `NamedFunction` instances.
     */
    def combineNamed7[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction, T3: NamedFunction, T4: NamedFunction, T5: NamedFunction, T6: NamedFunction]: String =
        combineNamed6[T0, T1, T2, T3, T4, T5] + "+" + name[T6]

    /**
     * Combines the names of two `NamedFunction` instances using the given type constraints.
     *
     * @param t1n the second `NamedFunction` of type `T1`, whose name will be combined with the name of the first function.
     * @tparam T0 the type of the first `NamedFunction`, which must have an implicit `NamedFunction` instance.
     * @tparam T1 the type of the second `NamedFunction`.
     * @return a `String` representing the combined names of the two `NamedFunction` instances.
     */
    def combineNameds2[T0: NamedFunction, T1](t1n: NamedFunction[T1]): String =
        combineNames2(named[T0], t1n)

    /**
     * Combines the names of three types using the associated `NamedFunction` trait.
     * Each type `T0`, `T1`, and `T2` is expected to have an implicitly available instance of `NamedFunction`.
     * The name for `T2` is provided explicitly as an argument.
     *
     * @param t2n the `NamedFunction` instance associated with the third type `T2`
     * @tparam T0 the first type parameter with an implicit `NamedFunction` available
     * @tparam T1 the second type parameter with an implicit `NamedFunction` available
     * @tparam T2 the third type parameter whose associated `NamedFunction` is explicitly passed
     * @return a string made by combining the names of `T0`, `T1`, and `T2` as defined by their respective `NamedFunction`
     */
    def combineNameds3[T0: NamedFunction, T1: NamedFunction, T2](t2n: NamedFunction[T2]): String =
        combineNames3(named[T0], named[T1], t2n)

    /**
     * Combines the names of four `NamedFunction` instances into a single string representation.
     *
     * @param t3n the fourth `NamedFunction` instance to include in the combination.
     * @tparam T0 the type parameter of the first `NamedFunction`.
     * @tparam T1 the type parameter of the second `NamedFunction`.
     * @tparam T2 the type parameter of the third `NamedFunction`.
     * @tparam T3 the type parameter of the fourth `NamedFunction`.
     * @return a string representing the combined names of the four `NamedFunction` instances.
     */
    def combineNameds4[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction, T3](t3n: NamedFunction[T3]): String =
        combineNames4(named[T0], named[T1], named[T2], t3n)

    /**
     * Combines the names of five types, where the first four types are inferred from context
     * and the fifth type is provided explicitly via its `NamedFunction` instance.
     *
     * @param t4n an instance of `NamedFunction` representing the fifth type to combine.
     * @tparam T0 the first type, which has an implicit `NamedFunction` in scope.
     * @tparam T1 the second type, which has an implicit `NamedFunction` in scope.
     * @tparam T2 the third type, which has an implicit `NamedFunction` in scope.
     * @tparam T3 the fourth type, which has an implicit `NamedFunction` in scope.
     * @tparam T4 the fifth type, represented by the `NamedFunction` passed explicitly.
     * @return the combined name as a `String`.
     */
    def combineNameds5[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction, T3: NamedFunction, T4](t4n: NamedFunction[T4]): String =
        combineNames5(named[T0], named[T1], named[T2], named[T3], t4n)

    /**
     * Combines the names of six `NamedFunction` instances, where the first five types are implicitly resolved,
     * and the sixth is provided as a parameter.
     *
     * @param t5n the sixth `NamedFunction` instance whose name is combined with the previous five.
     * @tparam T0 the type of the first `NamedFunction` instance.
     * @tparam T1 the type of the second `NamedFunction` instance.
     * @tparam T2 the type of the third `NamedFunction` instance.
     * @tparam T3 the type of the fourth `NamedFunction` instance.
     * @tparam T4 the type of the fifth `NamedFunction` instance.
     * @tparam T5 the type of the sixth `NamedFunction` instance.
     * @return a string representing the combined names of the six `NamedFunction` instances.
     */
    def combineNameds6[T0: NamedFunction, T1: NamedFunction, T2: NamedFunction, T3: NamedFunction, T4: NamedFunction, T5](t5n: NamedFunction[T5]): String =
        combineNames6(named[T0], named[T1], named[T2], named[T3], named[T4], t5n)

    /**
     * Combines the names of two `NamedFunction` instances into a single string.
     *
     * @param t0n the first `NamedFunction` instance
     * @param t1n the second `NamedFunction` instance
     * @return a string representing the combined names of the two `NamedFunction` instances,
     *         separated by a "+" symbol
     */
    def combineNames2[T0, T1](t0n: NamedFunction[T0], t1n: NamedFunction[T1]): String =
        t0n.name + "+" + t1n.name

    /**
     * Combines the names of three given `NamedFunction` instances into a single string.
     *
     * @param t0n the first `NamedFunction` instance whose name is to be combined.
     * @param t1n the second `NamedFunction` instance whose name is to be combined.
     * @param t2n the third `NamedFunction` instance whose name is to be combined.
     * @return a string representing the combined names of the three `NamedFunction` instances.
     */
    def combineNames3[T0, T1, T2](t0n: NamedFunction[T0], t1n: NamedFunction[T1], t2n: NamedFunction[T2]): String =
        combineNames2(t0n, t1n) + "+" + t2n.name

    /**
     * Combines the names of four `NamedFunction` instances into a single string.
     * The result is constructed by combining the names of the first three `NamedFunction` instances
     * using `combineNames3`, followed by appending the name of the fourth `NamedFunction` instance,
     * separated by a "+".
     *
     * @param t0n the first `NamedFunction` whose name will be included in the combined result
     * @param t1n the second `NamedFunction` whose name will be included in the combined result
     * @param t2n the third `NamedFunction` whose name will be included in the combined result
     * @param t3n the fourth `NamedFunction` whose name will be appended in the combined result
     * @return a string representing the combined names of the four `NamedFunction` instances
     */
    def combineNames4[T0, T1, T2, T3](t0n: NamedFunction[T0], t1n: NamedFunction[T1], t2n: NamedFunction[T2], t3n: NamedFunction[T3]): String =
        combineNames3(t0n, t1n, t2n) + "+" + t3n.name

    /**
     * Combines the names of five `NamedFunction` instances into a single string.
     *
     * @param t0n the first `NamedFunction` instance.
     * @param t1n the second `NamedFunction` instance.
     * @param t2n the third `NamedFunction` instance.
     * @param t3n the fourth `NamedFunction` instance.
     * @param t4n the fifth `NamedFunction` instance.
     * @return a string representation of the combined names of the five instances.
     */
    def combineNames5[T0, T1, T2, T3, T4](t0n: NamedFunction[T0], t1n: NamedFunction[T1], t2n: NamedFunction[T2], t3n: NamedFunction[T3], t4n: NamedFunction[T4]): String =
        combineNames4(t0n, t1n, t2n, t3n) + "+" + t4n.name

    /**
     * Combines the names of six `NamedFunction` instances into a single string.
     * The resulting string is formed by concatenating the names of the first five instances using the `combineNames5` method
     * and appending the name of the sixth instance, separated by a "+" symbol.
     *
     * @param t0n the first `NamedFunction` instance.
     * @param t1n the second `NamedFunction` instance.
     * @param t2n the third `NamedFunction` instance.
     * @param t3n the fourth `NamedFunction` instance.
     * @param t4n the fifth `NamedFunction` instance.
     * @param t5n the sixth `NamedFunction` instance.
     * @return a string representing the combined names of the six `NamedFunction` instances.
     */
    def combineNames6[T0, T1, T2, T3, T4, T5](t0n: NamedFunction[T0], t1n: NamedFunction[T1], t2n: NamedFunction[T2], t3n: NamedFunction[T3], t4n: NamedFunction[T4], t5n: NamedFunction[T5]): String =
        combineNames5(t0n, t1n, t2n, t3n, t4n) + "+" + t5n.name
}
