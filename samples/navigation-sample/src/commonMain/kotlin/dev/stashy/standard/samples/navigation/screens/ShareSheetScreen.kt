package dev.stashy.standard.samples.navigation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ShareSheetScreen(slug: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Share \"$slug\"", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("This overlay is ephemeral and is not represented in the URL.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}
