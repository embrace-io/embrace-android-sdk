package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.ndk.NativeCrashService
import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.utils.Provider

class FakePayloadResurrectionService : PayloadResurrectionService {

    var resurrectCount: Int = 0

    override fun resurrectOldPayloads(nativeCrashServiceProvider: Provider<NativeCrashService?>) {
        resurrectCount++
    }
}
