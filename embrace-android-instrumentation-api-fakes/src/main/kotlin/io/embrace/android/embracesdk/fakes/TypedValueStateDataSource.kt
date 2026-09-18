package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.State
import io.embrace.android.embracesdk.internal.arch.schema.TypedStateValue

class TypedValueStateDataSource(
    args: InstrumentationArgs,
) : StateDataSource<TypedStateValue>(
    args = args,
    stateTypeFactory = ::TypedValueState,
    defaultValue = TypedStateValue("UNKNOWN", true),
) {
    override val captureStateOnCreation: Boolean = false
}

class TypedValueState(initialValue: TypedStateValue) : State<TypedStateValue>(initialValue, "typed")

data class TypedStateValue(
    val value: String,
    override val isSystemValue: Boolean = false,
) : TypedStateValue {
    override fun toString(): String = value
}
