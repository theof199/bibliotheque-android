package fr.mediatheque.journal.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.R
import fr.mediatheque.journal.api.dto.User
import fr.mediatheque.journal.ui.ErrorBlock

@Composable
fun ProfileScreen(user: User, vm: ProfileViewModel, onBack: () -> Unit, onFilms: () -> Unit, onSignOut: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val chiffre = MaterialTheme.typography.displaySmall.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurface)
    val mots = MaterialTheme.typography.bodyLarge.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Ce `ViewModel` est indexé sur l'Activité, clé fixe (jumeau du piège réglé sur
    // `LoginViewModel`/`SearchViewModel` dans `Root`, revues des tâches 4 et 5) : sans ce
    // rechargement à chaque entrée, la même instance reviendrait avec les chiffres de la
    // première ouverture, en retard d'un film après un enregistrement, une correction ou
    // une suppression faits depuis « Mes films ».
    LaunchedEffect(Unit) { vm.retry() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
        }
        // Le reste défile en un seul bloc (jumeau de `FormScreen`) : à la taille de police
        // maximale, les deux chiffres et la liste s'étirent, et sans ce `verticalScroll` le
        // bouton « Se déconnecter » et la mention TMDB sortaient de l'écran (design §8,
        // revue du tour de correction 1).
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(user.pseudo, style = MaterialTheme.typography.titleMedium)
                when {
                    ui.error != null -> ErrorBlock(ui.error!!.message ?: "", retryable = ui.error!!.retryable, onRetry = vm::retry)
                    ui.total != null -> Text(buildAnnotatedString {
                        withStyle(chiffre) { append("${ui.total}") }
                        withStyle(mots) { append(" films vus, ") }
                        withStyle(chiffre) { append("${ui.thisYear}") }
                        withStyle(mots) { append(" cette année") }
                    })
                    // Ni l'un ni l'autre : le premier chargement n'a pas encore répondu. Rien
                    // ne s'affiche — jamais un zéro, qui serait un compte, pas une absence de
                    // réponse (décision 3 de la tâche 7).
                }
                ListItem(
                    headlineContent = { Text("Mes films", style = MaterialTheme.typography.titleMedium) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable(onClick = onFilms),
                )
                TextButton(onClick = onSignOut) { Text("Se déconnecter", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            // La mention TMDB : une condition de leurs conditions d'utilisation de l'API
            // (§3 « Attribution », https://www.themoviedb.org/api-terms-of-use), pas une
            // politesse. Le logo vient de leur kit de marque
            // (https://www.themoviedb.org/about/logos-attribution, lu le 9 septembre 2026) :
            // à cette date la page n'offre que des logos « blue » en SVG, aucune version dédiée
            // au fond sombre ni aucun PNG — « Alt short (blue) » choisi, converti en PNG,
            // fond transparent. La phrase ci-dessous est celle qu'imposent les conditions à
            // cette même date, « application » remplaçant leur liste de mots-clés entre
            // crochets ([website, program, service, application, product]).
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Image(painterResource(R.drawable.tmdb_logo), contentDescription = "TMDB", modifier = Modifier.width(88.dp))
                Text(
                    "This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
