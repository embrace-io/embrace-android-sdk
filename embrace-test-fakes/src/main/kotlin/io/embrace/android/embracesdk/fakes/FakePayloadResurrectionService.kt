package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.resurrection.PayloadResurrectionService

class FakePayloadResurrectionService : PayloadResurrectionService {

    var resurrectCount: Int = 0

    override fun resurrectOldPayloads() {
        resurrectCount++
    }
}
