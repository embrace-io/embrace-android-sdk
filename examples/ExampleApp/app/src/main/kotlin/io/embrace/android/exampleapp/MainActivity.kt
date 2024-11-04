package io.embrace.android.exampleapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.embrace.android.exampleapp.ui.screens.ExampleCodeList
import io.embrace.android.exampleapp.ui.screens.codeExamples
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExampleCodeListScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExampleCodeListScreen() {
    ExampleAppTheme {
        Scaffold(modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                )
            },
            content = { paddingValues ->
                Column(modifier = Modifier.padding(paddingValues)) {
                    ExampleCodeList(
                        buttonItems = codeExamples
                    )
                }
            })
    }
}


@Preview(showBackground = true)
@Composable
fun ExampleCodeListScreenPreview() {
    ExampleCodeListScreen()
}
