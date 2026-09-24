package fr.mediatheque.journal.ui.form

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.mediatheque.journal.ui.FeuilleDeLecture

/**
 * « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») : sur la fiche d'un
 * film — journal (`FormScreen`, `Screen.Edit`), Voyage (`FicheVoyageScreen`) ou réalisateur
 * (`FicheFilmScreen`) — rouvre le carton de ce film dans la même feuille de lecture que celle
 * ouverte après un enregistrement (`FilmEnregistreCalque`). `titreConnu` reste affiché tant que le
 * carton (`configure`/`statut`) n'a pas donné le sien.
 */
@Composable
fun BoutonLeFilm(carton: CartonViewModel, titreConnu: String, modifier: Modifier = Modifier) {
    var ouverte by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { ouverte = true }, modifier = modifier) { Text("Le film") }
    if (ouverte) {
        val cartonUi by carton.ui.collectAsState()
        FeuilleDeLecture(
            titre = cartonUi.titre ?: titreConnu,
            etat = etatFeuilleCarton(cartonUi),
            onDismiss = { ouverte = false },
        )
    }
}
