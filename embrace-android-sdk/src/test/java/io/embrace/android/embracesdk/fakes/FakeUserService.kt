package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.capture.user.UserService
import io.embrace.android.embracesdk.payload.UserInfo

internal class FakeUserService : UserService {

    var obj: UserInfo = UserInfo()

    override fun getUserInfo(): UserInfo = obj

    override fun clearAllUserInfo() {
        TODO("Not yet implemented")
    }

    override fun loadUserInfoFromDisk(): UserInfo? {
        return obj
    }

    override fun setUserIdentifier(userId: String?) {
        TODO("Not yet implemented")
    }

    override fun clearUserIdentifier() {
        TODO("Not yet implemented")
    }

    override fun setUserEmail(email: String?) {
        TODO("Not yet implemented")
    }

    override fun clearUserEmail() {
        TODO("Not yet implemented")
    }

    override fun setUserAsPayer() {
        TODO("Not yet implemented")
    }

    override fun clearUserAsPayer() {
        TODO("Not yet implemented")
    }

    override fun addUserPersona(persona: String?) {
        TODO("Not yet implemented")
    }

    override fun clearUserPersona(persona: String?) {
        TODO("Not yet implemented")
    }

    override fun clearAllUserPersonas() {
        TODO("Not yet implemented")
    }

    override fun setUsername(username: String?) {
        TODO("Not yet implemented")
    }

    override fun clearUsername() {
        TODO("Not yet implemented")
    }
}
