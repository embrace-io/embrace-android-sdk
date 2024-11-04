package io.embrace.android.exampleapp.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.embrace.android.exampleapp.ui.theme.EmbraceBlack

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun appBarColors() = TopAppBarColors(
    containerColor = EmbraceBlack,
    scrolledContainerColor = EmbraceBlack,
    navigationIconContentColor = EmbraceBlack,
    titleContentColor = Color.White,
    actionIconContentColor = Color.White,
)
