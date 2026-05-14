package io.embrace.android.exampleapp.paradigms.social.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.paradigms.social.ui.ProfileScreen
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class SocialA2AProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val handle = intent.getStringExtra(EXTRA_HANDLE)
        if (handle.isNullOrEmpty()) {
            finish()
            return
        }
        setContent {
            ExampleAppTheme {
                ProfileScreen(
                    handle = handle,
                    onPostClick = { id ->
                        startActivity(SocialA2APostDetailActivity.newIntent(this, id))
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_HANDLE = "handle"

        fun newIntent(context: Context, handle: String): Intent =
            Intent(context, SocialA2AProfileActivity::class.java)
                .putExtra(EXTRA_HANDLE, handle)
    }
}
