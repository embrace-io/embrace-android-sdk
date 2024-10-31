package io.embrace.android.embracesdk.internal.gating

import io.embrace.android.embracesdk.internal.gating.SessionGatingKeys.USER_PERSONAS
import io.embrace.android.embracesdk.internal.payload.UserInfo

internal class UserInfoSanitizer(
    private val userInfo: UserInfo?,
    private val enabledComponents: Set<String>,
) : Sanitizable<UserInfo> {

    override fun sanitize(): UserInfo {
        if (userInfo == null) {
            return UserInfo()
        }

        if (!shouldSendUserPersonas()) {
            return userInfo.copy(personas = null)
        }

        return userInfo
    }

    private fun shouldSendUserPersonas() =
        enabledComponents.contains(USER_PERSONAS)
}
