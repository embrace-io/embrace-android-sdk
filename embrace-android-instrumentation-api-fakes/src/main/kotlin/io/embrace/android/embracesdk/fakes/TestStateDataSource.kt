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
    changeAttributeKeys = setOf(TEST_STATE_DECLARED_ATTRIBUTE_KEY),
)

/**
 * The one attribute key a change of the test state may carry when the app state data model is on.
 */
const val TEST_STATE_DECLARED_ATTRIBUTE_KEY: String = "test.declared"

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
