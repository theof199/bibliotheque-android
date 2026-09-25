package fr.mediatheque.journal.ui.form

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.FeuilleDeLecture

/**
 * « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») : sur la fiche d'un
 * film — journal (`FormScreen`, `Screen.Edit`), Voyage (`FicheVoyageScreen`) ou réalisateur
 * (`FicheFilmScreen`) — rouvre le carton de ce film dans la même feuille de lecture que celle
 * ouverte après un enregistrement (`FilmEnregistreCalque`). `titreConnu` reste affiché tant que le
 * carton (`configure`/`statut`) n'a pas donné le sien.
 *
 * `bord` (« la fiche · trois visages », reprise validée du 25 septembre 2026) : les fiches d'un
 * film le passent en `secondary` — « Le film », dernier de la pile, se distingue des autres
 * boutons en contour par son filet or ; `outline` partout ailleurs, comme avant. `shape` : le
 * rayon 12 dp des piles de boutons des fiches, la forme du thème au formulaire.
 */
@Composable
fun BoutonLeFilm(
    carton: CartonViewModel,
    titreConnu: String,
    modifier: Modifier = Modifier,
    bord: Color = MaterialTheme.colorScheme.outline,
    shape: Shape = ButtonDefaults.outlinedShape,
) {
    var ouverte by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { ouverte = true }, modifier = modifier, shape = shape, border = BorderStroke(1.dp, bord)) { Text("Le film") }
    if (ouverte) {
        val cartonUi by carton.ui.collectAsState()
        FeuilleDeLecture(
            titre = cartonUi.titre ?: titreConnu,
            etat = etatFeuilleCarton(cartonUi),
            onDismiss = { ouverte = false },
        )
    }
}
