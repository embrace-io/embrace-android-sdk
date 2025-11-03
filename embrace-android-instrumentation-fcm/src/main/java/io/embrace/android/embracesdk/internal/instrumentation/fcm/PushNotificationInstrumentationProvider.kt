package io.embrace.android.embracesdk.internal.instrumentation.fcm

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceState

// retain a reference for use in bytecode instrumentation
var fcmDataSource: PushNotificationDataSource? = null

class PushNotificationInstrumentationProvider : InstrumentationProvider {
    override fun register(args: InstrumentationArgs): DataSourceState<*>? {
        return DataSourceState(
            factory = {
                fcmDataSource = PushNotificationDataSource(args)
                fcmDataSource
            }
        )
    }
}
