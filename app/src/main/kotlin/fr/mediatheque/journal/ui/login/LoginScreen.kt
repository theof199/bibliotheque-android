package fr.mediatheque.journal.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(vm: LoginViewModel) {
    val ui by vm.ui.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showPassword by remember { mutableStateOf(false) }

    // Le bouton se réactive tout seul à la fin du délai du 429.
    LaunchedEffect(ui.blockedUntilMillis) {
        while (ui.blockedUntilMillis != null && now < ui.blockedUntilMillis!!) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val blocked = ui.blockedUntilMillis?.let { it > now } ?: false

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Journal", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = vm.pseudo,
            onValueChange = { vm.pseudo = it },
            label = { Text("Pseudo") },
            singleLine = true,
            isError = ui.pseudoInvalid,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.password,
            onValueChange = { vm.password = it },
            label = { Text("Mot de passe") },
            singleLine = true,
            isError = ui.passwordInvalid,
            shape = MaterialTheme.shapes.medium,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Masquer" else "Voir") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = vm::submit,
            enabled = !ui.busy && !blocked,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (blocked) "Patiente ${((ui.blockedUntilMillis!! - now) / 1_000).coerceAtLeast(0)} s" else "Se connecter")
        }
        // Décision 1 : « Réessayer » ne s'affiche que si l'erreur du back l'autorise (panne réseau),
        // jamais pour un pseudo ou mot de passe refusé.
        ui.error?.let { ErrorBlock(it, retryable = ui.retryable, onRetry = vm::submit) }
    }
}
