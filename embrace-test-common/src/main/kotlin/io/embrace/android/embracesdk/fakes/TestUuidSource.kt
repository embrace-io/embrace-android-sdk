package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import kotlin.random.Random

/**
 * A [UuidSource] used for tests that is thread-safe and generates UUIDs deterministically
 */
class TestUuidSource : UuidSource {
    private val delegate: UuidSource = UuidSourceImpl(Random(0))

    @Synchronized
    override fun createUuid(): String = delegate.createUuid()
}
