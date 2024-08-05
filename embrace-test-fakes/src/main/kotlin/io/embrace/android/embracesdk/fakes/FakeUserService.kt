package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.payload.UserInfo

public class FakeUserService : UserService {

    public var obj: UserInfo = UserInfo()
    public var id: String? = null
    public var email: String? = null
    public var name: String? = null
    public var payer: Boolean? = null
    public var personas: MutableList<String> = mutableListOf()
    public var clearedCount: Int = 0

    override fun getUserInfo(): UserInfo = obj

    override fun clearAllUserInfo() {
        clearedCount += 1
    }

    override fun loadUserInfoFromDisk(): UserInfo? {
        return obj
    }

    override fun setUserIdentifier(userId: String?) {
        id = userId
    }

    override fun clearUserIdentifier() {
        id = null
    }

    override fun setUserEmail(email: String?) {
        this.email = email
    }

    override fun clearUserEmail() {
        email = null
    }

    override fun setUserAsPayer() {
        payer = true
    }

    override fun clearUserAsPayer() {
        payer = null
    }

    override fun addUserPersona(persona: String?) {
        personas.add(checkNotNull(persona))
    }

    override fun clearUserPersona(persona: String?) {
        personas.remove(checkNotNull(persona))
    }

    override fun clearAllUserPersonas() {
        personas.clear()
    }

    override fun setUsername(username: String?) {
        this.name = username
    }

    override fun clearUsername() {
        this.name = null
    }
}
