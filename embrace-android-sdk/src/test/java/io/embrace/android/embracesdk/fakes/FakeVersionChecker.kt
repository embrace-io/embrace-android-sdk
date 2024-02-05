package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.utils.VersionChecker

internal class FakeVersionChecker(private val enabled: Boolean) : VersionChecker {
    override fun isAtLeast(min: Int): Boolean = enabled
}
