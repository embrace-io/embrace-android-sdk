package io.embrace.android.exampleapp.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import io.embrace.android.exampleapp.MainApplication

/**
 * Composable accessor for the application-scoped [AppGraph]. Pulls [MainApplication] off the
 * current [LocalContext] and returns its lazily-built graph. Cheap (one cast, one field read).
 */
@Composable
@ReadOnlyComposable
fun appGraph(): AppGraph =
    (LocalContext.current.applicationContext as MainApplication).graph

/** Non-composable accessor for activity / fragment code. */
fun Context.appGraph(): AppGraph =
    (applicationContext as MainApplication).graph
