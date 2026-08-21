package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.payload.UserInfo

class FakeUserService : UserService {

    var obj: UserInfo = UserInfo()
    var id: String? = null
    var email: String? = null
    var name: String? = null
    var personas: MutableList<String> = mutableListOf()
    var clearedCount: Int = 0
    var listeners: MutableList<() -> Unit> = mutableListOf()

    override fun getUserInfo(): UserInfo = obj

    override fun clearAllUserInfo() {
        clearedCount += 1
        clearUserIdentifier()
        clearUserEmail()
        clearUsername()
        clearAllUserPersonas()
    }

    override fun loadUserInfoFromDisk(): UserInfo {
        return obj
    }

    override fun setUserIdentifier(userId: String?) {
        id = userId
        notifyUserInfoChanged()
    }

    override fun clearUserIdentifier() {
        id = null
        notifyUserInfoChanged()
    }

    override fun setUserEmail(email: String?) {
        this.email = email
        notifyUserInfoChanged()
    }

    override fun clearUserEmail() {
        email = null
        notifyUserInfoChanged()
    }

    override fun addUserPersona(persona: String?) {
        personas.add(checkNotNull(persona))
        notifyUserInfoChanged()
    }

    override fun clearUserPersona(persona: String?) {
        personas.remove(checkNotNull(persona))
        notifyUserInfoChanged()
    }

    override fun clearAllUserPersonas() {
        personas.clear()
        notifyUserInfoChanged()
    }

    override fun setUsername(username: String?) {
        this.name = username
        notifyUserInfoChanged()
    }

    override fun clearUsername() {
        this.name = null
        notifyUserInfoChanged()
    }

    override fun addUserInfoListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun notifyUserInfoChanged() {
        listeners.forEach { it() }
    }
}
