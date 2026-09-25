package io.embrace.android.embracesdk.internal.instrumentation.fcm

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.datasource.DataSource
import io.embrace.android.embracesdk.internal.arch.datasource.DataSourceFactory

// retain a reference for use in bytecode instrumentation
var fcmDataSource: PushNotificationDataSource? = null

class PushNotificationInstrumentationProvider : InstrumentationProvider {

    override val asyncInit: Boolean = true

    override fun register(args: InstrumentationArgs): DataSourceFactory<DataSource>? {
        return {
            fcmDataSource = PushNotificationDataSource(args)
            fcmDataSource
        }
    }
}
