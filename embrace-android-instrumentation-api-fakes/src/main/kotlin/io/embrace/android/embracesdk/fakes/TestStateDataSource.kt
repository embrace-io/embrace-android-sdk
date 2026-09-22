package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.State
import io.embrace.android.embracesdk.internal.arch.schema.SystemStateValue

/**
 * State data source that accepts any value, including [TestStateValue].
 */
class TestStateDataSource(
    args: InstrumentationArgs,
) : StateDataSource<Any>(
    args = args,
    stateTypeFactory = ::TestState,
    defaultValue = "UNKNOWN",
)

class TestState(initialValue: Any) : State<Any>(initialValue, "test")

/**
 * A state value whose string form alone does not identify it: a [SystemValue] and a [NonSystemValue] can share the same [value].
 */
sealed class TestStateValue {
    abstract val value: String

    override fun toString(): String = value

    data class NonSystemValue(override val value: String) : TestStateValue()

    data class SystemValue(override val value: String) : TestStateValue(), SystemStateValue
}
