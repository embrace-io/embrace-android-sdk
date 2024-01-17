package io.embrace.android.embracesdk

import io.embrace.android.embracesdk.session.SessionService

internal class FakeSessionService : SessionService {

    var crashId: String? = null

    override fun handleCrash(crashId: String) {
        this.crashId = crashId
    }

    override fun endSessionManually(clearUserInfo: Boolean) {
        TODO("Not yet implemented")
    }
}
