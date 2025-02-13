package io.embrace.android.gradle.plugin.system

class SystemClock : Clock {

    override fun now() = System.currentTimeMillis()
}
