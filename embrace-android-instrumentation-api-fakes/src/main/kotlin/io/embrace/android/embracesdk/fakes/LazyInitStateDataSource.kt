package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateDataSource
import io.embrace.android.embracesdk.internal.arch.schema.SchemaType.State

class LazyInitStateDataSource(
    args: InstrumentationArgs,
) : StateDataSource<String>(
    args = args,
    stateTypeFactory = ::LazyInitState,
    defaultValue = "UNKNOWN",
) {
    override val captureStateOnCreation: Boolean = false
}

class LazyInitState(initialValue: String) : State<String>(initialValue, "lazy-init")
