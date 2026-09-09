package fr.mediatheque.journal.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.showBriefly

@Composable
fun HomeScreen(message: String?, onMessageShown: () -> Unit, onAdd: () -> Unit, onProfile: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            // Consommé avant l'attente, pas après : si l'écran part pendant les deux secondes de
            // la snackbar, le message ne doit pas rester « en attente » et se rejouer au retour
            // (revue de la tâche 5).
            onMessageShown()
            // Deux secondes (design §6), pas la durée Material par défaut : `showBriefly`
            // (décision 3 de la tâche 5) referme elle-même la snackbar après le délai.
            snackbar.showBriefly(message)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            IconButton(onClick = onProfile, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Person, contentDescription = "Profil", tint = MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = onAdd,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(52.dp),
            ) { Text("Ajouter un film") }
        }
    }
}
