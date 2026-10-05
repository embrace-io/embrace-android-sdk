package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.utils.UuidSourceImpl
import kotlin.random.Random

/**
 * A [UuidSource] used for tests that generates UUIDs deterministically with a customizable seed.
 */
class TestUuidSource(seed: Int = 0) : UuidSource {
    private val delegate: UuidSource = UuidSourceImpl(Random(seed))

    override fun createUuid(): String = delegate.createUuid()
}
