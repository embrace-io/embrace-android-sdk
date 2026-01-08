package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.State

class TestStateDataSource(
    args: InstrumentationArgs,
) : StateDataSource<String>(
    args = args,
    stateTypeFactory = ::TestState,
    defaultValue = "UNKNOWN",
    maxTransitions = 4,
)

class TestState(initialValue: String) : State<String>(initialValue, "test")
