package io.embrace.android.exampleapp.ui.examples

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.embrace.android.exampleapp.ui.RadioButtonList

private enum class CrashType {
    SIGSEGV,
    SIGILL,
    SIGABRT,
    CPP_EXCEPTION;
}

@Composable
fun NdkCrashExample() {
    var crashType by remember { mutableStateOf(CrashType.SIGSEGV) }

    RadioButtonList(
        items = CrashType.entries,
        selectedItem = crashType
    ) {
        crashType = it
    }

    Button(onClick = { triggerCrash(crashType) }) {
        Text("Trigger Crash")
    }
}

private fun triggerCrash(crashType: CrashType) {
    when (crashType) {
        CrashType.SIGABRT -> abort()
        CrashType.SIGSEGV -> segfault()
        CrashType.SIGILL -> sigill()
        CrashType.CPP_EXCEPTION -> throwException()
    }
}

private external fun abort(): Unit
private external fun segfault(): Unit
private external fun sigill(): Unit
private external fun throwException(): Unit
