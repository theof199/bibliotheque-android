package fr.mediatheque.journal.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.ErrorBlock

/** L'écran « SensCritique » du profil (brief du 14 septembre 2026) — jumeau de `LoginScreen`. */
@Composable
fun SensCritiqueScreen(vm: SensCritiqueViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var showPassword by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
        }
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("SensCritique", style = MaterialTheme.typography.titleLarge)

            val pseudo = ui.connectedPseudo
            if (pseudo != null) {
                Text("Connecté : $pseudo", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = vm::disconnect) { Text("Déconnecter", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                OutlinedTextField(
                    value = vm.email,
                    onValueChange = { vm.email = it },
                    label = { Text("E-mail") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.password,
                    onValueChange = { vm.password = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Masquer" else "Voir") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = vm::connect,
                    enabled = !ui.busy,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Connecter") }
                ui.error?.let { ErrorBlock(it, retryable = ui.retryable, onRetry = vm::connect) }
            }
        }
    }
}
