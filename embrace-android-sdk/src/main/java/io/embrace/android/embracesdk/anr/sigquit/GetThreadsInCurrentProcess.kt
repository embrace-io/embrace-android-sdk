package io.embrace.android.embracesdk.anr.sigquit

internal class GetThreadsInCurrentProcess(
    private val filesDelegate: FilesDelegate
) {

    /**
     * Get threads associated with the current process
     *
     * @return a list of numerical thread ids
     */

    operator fun invoke(): List<String> {
        val dir = filesDelegate.getThreadsFileForCurrentProcess()
        return try {
            dir.listFiles()?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
