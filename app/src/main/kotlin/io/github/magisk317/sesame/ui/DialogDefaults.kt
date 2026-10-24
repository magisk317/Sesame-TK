package io.github.magisk317.sesame.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Keeps app dialogs compact while still allowing the full available width on phones.
 */
fun Modifier.sesameDialogWidth(): Modifier = widthIn(max = 420.dp)
