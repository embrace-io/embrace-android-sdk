package io.embrace.android.embracesdk.internal.arch.screen

import io.embrace.android.embracesdk.internal.utils.event.EventKey

/**
 * The kind of source a screen comes from. Each source identifies its screens by the Activity they are shown in, having at
 * most one open screen per Activity at a time.
 */
enum class ScreenSource(val value: String) {
    /**
     * An Activity, named by itself.
     */
    ACTIVITY("activity"),

    /**
     * A navigation library within an Activity, such as androidx-navigation.
     */
    NAVIGATION("navigation"),
}

/**
 * A screen named [name] has started being shown by [source] within the Activity identified by [activityId].
 *
 * Screens from different sources can be open at once, such as an Activity and the navigation destination shown within it.
 * The source emits a matching [ScreenStop] for the same [source] and [activityId] when the screen stops.
 */
data class ScreenStart(
    val name: String,
    val source: ScreenSource,
    val activityId: Int,
    val timestampMs: Long,
) {
    companion object {
        /**
         * The channel every screen start is emitted on.
         */
        val KEY: EventKey<ScreenStart> = EventKey()
    }
}

/**
 * The screen open for [source] within the Activity identified by [activityId] has stopped being shown.
 *
 * While it is delivered, handlers can write attributes summarizing the screen with [setAttribute]. They are read once every
 * handler has run, so a handler should write only while it handles the event.
 */
class ScreenStop(
    val source: ScreenSource,
    val activityId: Int,
    val timestampMs: Long,
    val reason: Reason,
) {

    private val screenAttributes = LinkedHashMap<String, String>()

    /**
     * Sets an attribute summarizing the screen, replacing any value already written for [key].
     */
    fun setAttribute(key: String, value: String) {
        synchronized(screenAttributes) {
            screenAttributes[key] = value
        }
    }

    /**
     * Returns what has been written with [setAttribute] so far.
     */
    fun writtenAttributes(): Map<String, String> = synchronized(screenAttributes) { screenAttributes.toMap() }

    /**
     * Why the screen stopped.
     */
    enum class Reason(val value: String) {
        /**
         * Its source replaced it with another screen.
         */
        NAVIGATED("navigated"),

        /**
         * The Activity it was shown in stopped being shown.
         */
        HIDDEN("hidden"),
    }

    companion object {
        /**
         * The channel every screen stop is emitted on.
         */
        val KEY: EventKey<ScreenStop> = EventKey()
    }
}
