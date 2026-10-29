package io.embrace.android.embracesdk.internal.capture.user

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import io.embrace.android.embracesdk.internal.logging.guardSdkCodeEach
import io.embrace.android.embracesdk.internal.payload.UserInfo
import io.embrace.android.embracesdk.internal.store.PersistedSdkStateStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

internal class EmbraceUserService(
    private val stateStore: PersistedSdkStateStore,
    private val clock: Clock,
    private val errorHandler: ErrorHandler,
) : UserService {

    /**
     * Do not access this directly - use [userInfo] and [modifyUserInfo] to get and set this
     */
    private val userInfoReference = AtomicReference(DEFAULT_USER)
    private val userInfoProvider: Provider<UserInfo> = { getStoredUserInfo() }
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    private fun isUsersFirstDay(): Boolean {
        return clock.now() - stateStore.snapshot().installTimestampMs <= DAY_IN_MS
    }

    override fun loadUserInfoFromDisk(): UserInfo? {
        return errorHandler.guardSdkCode(SdkErrorType.UserLoadFail, null, ::getStoredUserInfo)
    }

    override fun getUserInfo(): UserInfo = userInfo().copy()

    override fun setUserIdentifier(userId: String?) {
        val currentUserId = userInfo().userId
        if (currentUserId != null && currentUserId == userId) {
            return
        }
        modifyUserInfo(userInfo().copy(userId = userId))
        stateStore.update { this.userId = userId }
    }

    override fun clearUserIdentifier() {
        setUserIdentifier(null)
    }

    override fun setUsername(username: String?) {
        val currentUserName = userInfo().username
        if (currentUserName != null && currentUserName == username) {
            return
        }
        modifyUserInfo(userInfo().copy(username = username))
        stateStore.update { userName = username }
    }

    override fun clearUsername() {
        setUsername(null)
    }

    override fun setUserEmail(email: String?) {
        val currentEmail = userInfo().email
        if (currentEmail != null && currentEmail == email) {
            return
        }
        modifyUserInfo(userInfo().copy(email = email))
        stateStore.update { userEmail = email }
    }

    override fun clearUserEmail() {
        setUserEmail(null)
    }

    override fun addUserPersona(persona: String?) {
        if (persona == null) {
            return
        }
        if (!VALID_PERSONA.matcher(persona).matches()) {
            return
        }
        val currentPersonas = userInfo().personas
        if (currentPersonas != null) {
            if (currentPersonas.size >= PERSONA_LIMIT) {
                return
            }
            if (currentPersonas.contains(persona)) {
                return
            }
        }

        val newPersonas: Set<String> = userInfo().personas?.plus(persona) ?: mutableSetOf(persona)
        modifyUserInfo(userInfo().copy(personas = newPersonas))
        stateStore.update { userPersonas = newPersonas }
    }

    override fun clearUserPersona(persona: String?) {
        if (persona == null) {
            return
        }
        val currentPersonas = userInfo().personas
        if (currentPersonas != null && !currentPersonas.contains(persona)) {
            return
        }

        val newPersonas: Set<String> = userInfo().personas?.minus(persona) ?: mutableSetOf()
        modifyUserInfo(userInfo().copy(personas = newPersonas))
        stateStore.update { userPersonas = newPersonas }
    }

    override fun clearAllUserPersonas() {
        val personas = clearedPersonas() ?: return
        modifyUserInfo(userInfo().copy(personas = personas))
        stateStore.update { userPersonas = personas }
    }

    override fun clearAllUserInfo() {
        val personas = clearedPersonas()
        modifyUserInfo(
            userInfo().copy(userId = null, email = null, username = null, personas = personas ?: userInfo().personas),
        )
        stateStore.update {
            userId = null
            userEmail = null
            userName = null
            if (personas != null) {
                userPersonas = personas
            }
        }
    }

    /**
     * Returns the SDK-derived personas that remain once every user-set persona is cleared, or null if the current
     * personas are already empty.
     */
    private fun clearedPersonas(): Set<String>? {
        val currentPersonas = userInfo().personas
        if (currentPersonas != null && currentPersonas.isEmpty()) {
            return null
        }
        val personas: MutableSet<String> = HashSet()
        if (isUsersFirstDay()) {
            personas.add(PERSONA_FIRST_DAY_USER)
        }
        return personas
    }

    override fun addUserInfoListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    private fun userInfo(): UserInfo {
        if (userInfoReference.get() === DEFAULT_USER) {
            synchronized(userInfoReference) {
                if (userInfoReference.get() === DEFAULT_USER) {
                    userInfoReference.set(userInfoProvider())
                }
            }
        }

        return userInfoReference.get()
    }

    private fun modifyUserInfo(newUserInfo: UserInfo) {
        synchronized(userInfoReference) {
            userInfoReference.set(newUserInfo)
        }
        errorHandler.guardSdkCodeEach(SdkErrorType.UserInfoCallbackFail, listeners) { listener -> listener() }
    }

    /**
     * Creates an instance of [UserInfo] from the cache.
     */
    private fun getStoredUserInfo(): UserInfo {
        EmbTrace.trace("load-user-info-from-pref") {
            val state = stateStore.snapshot()
            val personas: MutableSet<String> = HashSet()
            state.userPersonas?.let(personas::addAll)

            personas.remove(PERSONA_FIRST_DAY_USER)
            if (isUsersFirstDay()) {
                personas.add(PERSONA_FIRST_DAY_USER)
            }
            return UserInfo(state.userId, state.userEmail, state.userName, personas)
        }
    }

    private companion object {
        // Valid persona regex representation.
        val VALID_PERSONA: Pattern = Pattern.compile("^[a-zA-Z0-9_]{1,32}$")

        // Maximum number of allowed personas.
        const val PERSONA_LIMIT = 10

        private val DEFAULT_USER = UserInfo(
            userId = "",
            email = "",
            username = "",
            personas = emptySet(),
        )

        const val PERSONA_FIRST_DAY_USER = "first_day"
        const val DAY_IN_MS: Long = 60 * 60 * 24 * 1000L
    }
}
