package io.embrace.android.exampleapp.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun ExampleCodeList(
    buttonItems: List<ExampleCodeState>,
) {
    LazyColumn {
        items(buttonItems.size) { index ->
            val buttonItem = buttonItems[index]
            Button(
                onClick = buttonItem.action,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(text = buttonItem.text)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewEmbraceActionList() {
    ExampleCodeList(codeExamples)
}
