package io.embrace.android.exampleapp.paradigms.social.a2a

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.social.ui.TimelineUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class SocialA2ATimelineActivity : ComponentActivity() {

    private var postedBody by mutableStateOf<String?>(null)

    private val composeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            postedBody = result.data?.getStringExtra(SocialA2AComposeActivity.EXTRA_BODY)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                TimelineUi(
                    title = "Home (A2A)",
                    posts = SampleData.posts,
                    onPostClick = { id ->
                        startActivity(SocialA2APostDetailActivity.newIntent(this, id))
                    },
                    onAuthorClick = { handle ->
                        startActivity(SocialA2AProfileActivity.newIntent(this, handle))
                    },
                    onCompose = {
                        composeLauncher.launch(SocialA2AComposeActivity.newIntent(this))
                    },
                    postedBody = postedBody,
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, SocialA2ATimelineActivity::class.java)
    }
}
