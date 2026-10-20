package com.rendyhd.vicu.ui.components.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The words about location. The disclosure is what Google Play asks for before an app requests
 * background location: it names the data, that it is used while the app is closed, and why.
 */
internal object LocationCopy {
    const val TITLE = "Remind you at a place"
    const val DISCLOSURE =
        "Vicu uses your location to remind you when you arrive at or leave a place you chose, " +
            "even when the app is closed or not in use."
    const val PRIVACY = "Your location and your places stay on this device. They are never sent to your Vikunja server."
    const val ALWAYS = "On the next screen, allow location access all the time (Always)."
    const val ACCESS_NEEDED = "Location reminders need location access set to Always, so they work while Vicu is closed."
}

/** Explains the location permission before the system asks; shown while the view model says so. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDisclosureSheet(viewModel: PlacesViewModel) {
    val shown by viewModel.disclosureShown.collectAsStateWithLifecycle()
    if (!shown) return

    ModalBottomSheet(onDismissRequest = viewModel::dismissDisclosure) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(LocationCopy.TITLE, style = MaterialTheme.typography.titleLarge)
            Text(
                LocationCopy.DISCLOSURE,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                LocationCopy.PRIVACY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                LocationCopy.ALWAYS,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::confirmDisclosure,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Continue")
            }
            TextButton(
                onClick = viewModel::dismissDisclosure,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Not now")
            }
        }
    }
}
