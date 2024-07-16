package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.logging.DomainCountLimiter

internal class FakeDomainCountLimiter : DomainCountLimiter {

    var canLog = true
    override fun canLogNetworkRequest(domain: String): Boolean {
        return canLog
    }
}
