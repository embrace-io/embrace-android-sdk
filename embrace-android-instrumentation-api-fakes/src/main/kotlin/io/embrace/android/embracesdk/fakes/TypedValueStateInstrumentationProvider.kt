package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.datasource.StateInstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.schema.TypedStateValue

class TypedValueStateInstrumentationProvider : StateInstrumentationProvider<TypedValueStateDataSource, TypedStateValue>() {
    override fun factoryProvider(args: InstrumentationArgs): () -> TypedValueStateDataSource {
        return {
            TypedValueStateDataSource(args)
        }
    }
}
