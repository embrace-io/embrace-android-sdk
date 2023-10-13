package io.embrace.android.embracesdk.capture.aei

import io.embrace.android.embracesdk.payload.AppExitInfoData

/**
 * No-op class to represent EmbraceApplicationExitInfo implementation for Android version below 11
 */
internal class NoOpApplicationExitInfoService : ApplicationExitInfoService {

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<AppExitInfoData> = ArrayList()
}
