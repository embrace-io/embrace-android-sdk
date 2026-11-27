package io.embrace.android.embracesdk.internal.api.delegate

import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.capture.user.UserService

internal class UserApiDelegate(
    private val userService: Lazy<UserService>,
) : UserApi {

    override var userIdentifier: String?
        get() = userService.value.getUserInfo().userId
        set(value) {
            userService.value.setUserIdentifier(value)
        }
}
