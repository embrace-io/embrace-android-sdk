package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

class FakeSessionIdsProvider(
    var userSessionId: String = "",
    var sessionPartId: String = "",
) : SessionIdsProvider {
    override fun getCurrentUserSessionId(): String = userSessionId
    override fun getCurrentSessionPartId(): String = sessionPartId
    override fun getActiveSessionIds(): SessionIdsSnapshot =
        SessionIdsSnapshot(userSessionId = userSessionId, sessionPartId = sessionPartId)
}
