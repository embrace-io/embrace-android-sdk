package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.session.id.ActiveSessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot

class FakeActiveSessionIdsProvider(
    var snapshot: SessionIdsSnapshot = SessionIdsSnapshot("", "")
) : ActiveSessionIdsProvider {
    override fun getActiveSessionIds(): SessionIdsSnapshot = snapshot
}
