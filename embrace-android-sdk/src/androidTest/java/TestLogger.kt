import android.util.Log

// use same tag value so that we can filter logs in logcat on CI job
private const val LOG_TAG = "[Embrace]"

internal fun logTestMessage(message: String) {
    Log.w(LOG_TAG, message)
}
