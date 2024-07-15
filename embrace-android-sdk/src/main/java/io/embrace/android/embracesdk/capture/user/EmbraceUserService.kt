package io.embrace.android.embracesdk.capture.user

import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.prefs.PreferencesService
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.payload.UserInfo
import io.embrace.android.embracesdk.payload.UserInfo.Companion.ofStored
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

internal class EmbraceUserService(
    private val preferencesService: PreferencesService,
    private val logger: EmbLogger
) : UserService {
    /**
     * Do not access this directly - use [userInfo] and [modifyUserInfo] to get and set this
     */
    private val userInfoReference = AtomicReference(DEFAULT_USER)
    private val userInfoProvider: Provider<UserInfo> = { ofStored(preferencesService) }

    override fun loadUserInfoFromDisk(): UserInfo? {
        return try {
            ofStored(preferencesService)
        } catch (ex: Exception) {
            logger.logError("Failed to load user info from persistent storage.", ex)
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
        preferencesService.userIdentifier = userId
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
        preferencesService.username = username
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
        preferencesService.userEmailAddress = email
    }

    override fun clearUserEmail() {
        setUserEmail(null)
    }

    override fun setUserAsPayer() {
        addUserPersona(UserInfo.PERSONA_PAYER)
    }

    override fun clearUserAsPayer() {
        clearUserPersona(UserInfo.PERSONA_PAYER)
    }

    override fun addUserPersona(persona: String?) {
        if (persona == null) {
            return
        }
        if (!VALID_PERSONA.matcher(persona).matches()) {
            logger.logWarning("Ignoring persona " + persona + " as it does not match " + VALID_PERSONA.pattern())
            return
        }
        val currentPersonas = userInfo().personas
        if (currentPersonas != null) {
            if (currentPersonas.size >= PERSONA_LIMIT) {
                logger.logWarning("Cannot set persona as the limit of " + PERSONA_LIMIT + " has been reached")
                return
            }
            if (currentPersonas.contains(persona)) {
                return
            }
        }

        val newPersonas: Set<String> = userInfo().personas?.plus(persona) ?: mutableSetOf(persona)
        modifyUserInfo(userInfo().copy(personas = newPersonas))
        preferencesService.userPersonas = newPersonas
    }

    override fun clearUserPersona(persona: String?) {
        if (persona == null) {
            return
        }
        val currentPersonas = userInfo().personas
        if (currentPersonas != null && !currentPersonas.contains(persona)) {
            logger.logWarning("Persona '$persona' is not set")
            return
        }

        val newPersonas: Set<String> = userInfo().personas?.minus(persona) ?: mutableSetOf()
        modifyUserInfo(userInfo().copy(personas = newPersonas))
        preferencesService.userPersonas = newPersonas
    }

    override fun clearAllUserPersonas() {
        val currentPersonas = userInfo().personas
        if (currentPersonas != null && currentPersonas.isEmpty()) {
            return
        }
        val personas: MutableSet<String> = HashSet()
        if (preferencesService.userPayer) {
            personas.add(UserInfo.PERSONA_PAYER)
        }
        if (preferencesService.isUsersFirstDay()) {
            personas.add(UserInfo.PERSONA_FIRST_DAY_USER)
        }
        modifyUserInfo(userInfo().copy(personas = personas))
        preferencesService.userPersonas = personas
    }

    override fun clearAllUserInfo() {
        clearUserIdentifier()
        clearUserEmail()
        clearUsername()
        clearAllUserPersonas()
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
    }

    companion object {
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
    }
}
