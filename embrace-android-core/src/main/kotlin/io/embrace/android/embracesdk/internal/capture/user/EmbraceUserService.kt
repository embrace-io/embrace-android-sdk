package io.embrace.android.embracesdk.internal.capture.user

import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.payload.UserInfo
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

internal class EmbraceUserService(
    private val impl: KeyValueStore,
    private val clock: Clock,
    private val logger: InternalLogger,
) : UserService {

    /**
     * Do not access this directly - use [userInfo] and [modifyUserInfo] to get and set this
     */
    private val userInfoReference = AtomicReference(DEFAULT_USER)
    private val userInfoProvider: Provider<UserInfo> = { getStoredUserInfo() }
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    private var userPayer: Boolean
        get() = impl.getBoolean(USER_IS_PAYER_KEY, false)
        set(value) = impl.edit { putBoolean(USER_IS_PAYER_KEY, value) }

    private var userId: String?
        get() = impl.getString(USER_IDENTIFIER_KEY)
        set(value) = impl.edit { putString(USER_IDENTIFIER_KEY, value) }

    private var userEmailAddress: String?
        get() = impl.getString(USER_EMAIL_ADDRESS_KEY)
        set(value) = impl.edit { putString(USER_EMAIL_ADDRESS_KEY, value) }

    private var userPersonas: Set<String>?
        get() = impl.getStringSet(USER_PERSONAS_KEY)
        set(value) = impl.edit { putStringSet(USER_PERSONAS_KEY, value) }

    private var name: String?
        get() = impl.getString(USER_USERNAME_KEY)
        set(value) = impl.edit { putString(USER_USERNAME_KEY, value) }

    private fun isUsersFirstDay(): Boolean {
        val installDate = installDate
        return installDate != null && clock.now() - installDate <= DAY_IN_MS
    }

    private var installDate: Long?
        get() = impl.getLong(INSTALL_DATE_KEY)
        set(value) = impl.edit { putLong(INSTALL_DATE_KEY, value) }

    init {
        if (installDate == null) {
            installDate = clock.now()
        }
    }

    override fun loadUserInfoFromDisk(): UserInfo? {
        return try {
            getStoredUserInfo()
        } catch (ex: Exception) {
            logger.trackInternalError(InternalErrorType.USER_LOAD_FAIL, ex)
            null
        }
    }

    override fun getUserInfo(): UserInfo = userInfo().copy()

    override fun setUserIdentifier(userId: String?) {
        val currentUserId = userInfo().userId
        if (currentUserId != null && currentUserId == userId) {
            return
        }
        modifyUserInfo(userInfo().copy(userId = userId))
        this.userId = userId
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
        this.name = username
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
        this.userEmailAddress = email
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
        this.userPersonas = newPersonas
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
        this.userPersonas = newPersonas
    }

    override fun clearAllUserPersonas() {
        val currentPersonas = userInfo().personas
        if (currentPersonas != null && currentPersonas.isEmpty()) {
            return
        }
        val personas: MutableSet<String> = HashSet()
        if (this.userPayer) {
            personas.add(PERSONA_PAYER)
        }
        if (isUsersFirstDay()) {
            personas.add(PERSONA_FIRST_DAY_USER)
        }
        modifyUserInfo(userInfo().copy(personas = personas))
        this.userPersonas = personas
    }

    override fun clearAllUserInfo() {
        clearUserIdentifier()
        clearUserEmail()
        clearUsername()
        clearAllUserPersonas()
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
        listeners.forEach { it() }
    }

    /**
     * Creates an instance of [UserInfo] from the cache.
     */
    private fun getStoredUserInfo(): UserInfo {
        EmbTrace.trace("load-user-info-from-pref") {
            val id = userId
            val name = name
            val email = userEmailAddress
            val personas: MutableSet<String> = HashSet()
            userPersonas?.let(personas::addAll)

            personas.remove(PERSONA_PAYER)
            if (userPayer) {
                personas.add(PERSONA_PAYER)
            }
            personas.remove(PERSONA_FIRST_DAY_USER)
            if (isUsersFirstDay()) {
                personas.add(PERSONA_FIRST_DAY_USER)
            }
            return UserInfo(id, email, name, personas)
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
            personas = emptySet()
        )

        const val USER_IDENTIFIER_KEY = "io.embrace.userid"
        const val USER_EMAIL_ADDRESS_KEY = "io.embrace.useremail"
        const val USER_USERNAME_KEY = "io.embrace.username"
        const val USER_IS_PAYER_KEY = "io.embrace.userispayer"
        const val USER_PERSONAS_KEY = "io.embrace.userpersonas"
        const val INSTALL_DATE_KEY = "io.embrace.installtimestamp"
        const val PERSONA_PAYER = "payer"
        const val PERSONA_FIRST_DAY_USER = "first_day"
        const val DAY_IN_MS: Long = 60 * 60 * 24 * 1000L
    }
}
