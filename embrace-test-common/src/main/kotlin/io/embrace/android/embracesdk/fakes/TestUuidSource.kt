package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import kotlin.random.Random

/**
 * A [UuidSource] used for tests that generates UUIDs deterministically
 */
class TestUuidSource : UuidSource {
    private val delegate: UuidSource = UuidSourceImpl(Random(0))

    override fun createUuid(): String = delegate.createUuid()
}
