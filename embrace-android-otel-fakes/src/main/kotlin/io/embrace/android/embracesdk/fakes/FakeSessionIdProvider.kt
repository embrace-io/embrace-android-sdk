package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider

class FakeSessionIdProvider(
    var userSessionId: String = "",
    var sessionPartId: String = "",
) : SessionIdProvider {
    override fun getCurrentUserSessionId(): String = userSessionId
    override fun getCurrentSessionPartId(): String = sessionPartId
}
