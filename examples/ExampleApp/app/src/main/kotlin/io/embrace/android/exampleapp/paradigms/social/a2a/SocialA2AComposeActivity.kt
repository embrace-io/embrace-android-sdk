package io.embrace.android.exampleapp.paradigms.social.a2a

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.paradigms.social.ui.ComposePostUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class SocialA2AComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                ComposePostUi(
                    onPost = { body ->
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_BODY, body))
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_BODY: String = "body"

        fun newIntent(context: Context): Intent =
            Intent(context, SocialA2AComposeActivity::class.java)
    }
}
