package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.aei.ApplicationExitInfoService
import io.embrace.android.embracesdk.payload.AppExitInfoData

internal class FakeApplicationExitInfoService : ApplicationExitInfoService {

    var data: List<AppExitInfoData> =
        listOf(AppExitInfoData(null, null, null, null, null, null, null, null, null, null, null))

    override fun cleanCollections() {
    }

    override fun getCapturedData(): List<AppExitInfoData> = data
}
