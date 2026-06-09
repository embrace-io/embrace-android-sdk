package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.utils.UuidSource

class FakeUuidSource(private val value: String = "fakeuuid") : UuidSource {
    override fun createUuid(): String = value
}
