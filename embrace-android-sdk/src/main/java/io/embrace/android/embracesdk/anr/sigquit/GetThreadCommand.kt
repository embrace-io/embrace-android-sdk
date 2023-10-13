package io.embrace.android.embracesdk.anr.sigquit

internal class GetThreadCommand(
    private val filesDelegate: FilesDelegate
) {

    /**
     * Get command name associated with thread id
     *
     * @return a command name, or empty if none found
     */

    operator fun invoke(threadId: String): String {
        val file = filesDelegate.getCommandFileForThread(threadId)
        return try {
            file.readText()
        } catch (e: Exception) {
            ""
        }
    }
}
