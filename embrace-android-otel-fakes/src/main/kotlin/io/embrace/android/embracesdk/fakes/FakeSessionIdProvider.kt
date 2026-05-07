package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.SessionIdProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

class FakeSessionIdProvider(
    var userSessionId: String = "",
    var sessionPartId: String = "",
) : SessionIdProvider {
    override fun getCurrentUserSessionId(): String = userSessionId
    override fun getCurrentSessionPartId(): String = sessionPartId
    override fun getActiveSessionIds(): SessionIdsSnapshot =
        SessionIdsSnapshot(userSessionId = userSessionId, sessionPartId = sessionPartId)
}
