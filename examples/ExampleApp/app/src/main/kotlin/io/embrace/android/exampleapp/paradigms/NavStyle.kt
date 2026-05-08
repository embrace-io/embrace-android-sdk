package io.embrace.android.exampleapp.paradigms

enum class NavStyle(val displayName: String) {
    ACTIVITY_TO_ACTIVITY("Activity → Activity"),
    FRAGMENTS_PRE_24("Fragments + NavController (pre-2.4)"),
    NAV_COMPOSE_28("Nav-Compose 2.8+"),
    NAV3("Navigation 3"),
}
