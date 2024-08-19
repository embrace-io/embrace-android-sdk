package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.network.logging.DomainCountLimiter

public class FakeDomainCountLimiter : DomainCountLimiter {

    public var canLog: Boolean = true
    override fun canLogNetworkRequest(domain: String): Boolean {
        return canLog
    }
}
