package io.embrace.android.embracesdk.internal.capture.state

import io.embrace.android.embracesdk.internal.arch.datasource.StateUpdate
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.utils.event.EventBus

/**
 * Reports the user's personas as a state: once now, and again whenever the user's information changes.
 *
 * A state holds one value, so the set of personas is written as its members in order, joined by commas. An update that
 * leaves the personas as they were is not a change, which is decided by whatever records the updates.
 */
fun reportPersonasAsState(userService: UserService, eventBus: EventBus, clock: Clock) {
    // reads and emits one at a time, so that the last value emitted is the one the user has
    val lock = Any()
    val report = {
        synchronized(lock) {
            val personas = userService.getUserInfo().personas.orEmpty().sorted().joinToString(PERSONA_SEPARATOR)
            eventBus.emit(StateUpdate.KEY, StateUpdate(PERSONAS_STATE, personas, clock.now()))
        }
    }
    userService.addUserInfoListener(report)
    report()
}

/**
 * The name of the state that holds the user's personas.
 */
const val PERSONAS_STATE: String = "personas"

private const val PERSONA_SEPARATOR = ","
