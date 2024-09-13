package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService

interface ConfigModule {
    val configService: ConfigService
}
