package io.embrace.android.embracesdk.internal.state

import io.embrace.android.embracesdk.internal.utils.PropertyUtils

/**
 * The one rule for what a state's value is and when it has changed, used by everything that has to decide either: a data source
 * before it reports, and the recorder before it gives a change its number. Two places that decide by different tests can
 * disagree, so both call this.
 */
object StateValues {

    /**
     * The longest a state's value, or the value of a change's own attribute, may be. It is the SDK's limit on an internal
     * attribute's value.
     */
    const val MAX_VALUE_LENGTH: Int = 2000

    /**
     * [value] as it is recorded: cut to [MAX_VALUE_LENGTH] less three and `...` when it is longer, without splitting a surrogate
     * pair.
     */
    fun cap(value: String): String = PropertyUtils.truncate(value, MAX_VALUE_LENGTH)

    /**
     * Whether an update to [value] leaves a state as it was, given what it [held]. Both are text already cut by [cap], and a
     * value the SDK generated is not the same as an equal one the app or the system gave.
     */
    fun isRepeat(held: String, heldSystem: Boolean, value: String, system: Boolean): Boolean =
        heldSystem == system && held == value
}
