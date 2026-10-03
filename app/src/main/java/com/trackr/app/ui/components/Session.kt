package com.trackr.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import com.trackr.app.domain.model.Profile

/** The signed-in user's profile (null while offline-unknown); read by top bars and greeting. */
val LocalProfile = staticCompositionLocalOf<Profile?> { null }
