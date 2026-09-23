package fr.mediatheque.journal.ui.theme

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Les animations Lottie des célébrations (brief du 23 septembre 2026, soir) : chaque nom passé à
 * `Animation(...)` (`Animations.kt`) ou à `LottieAnimation` directement (la bobine de
 * tirer-pour-rafraîchir, `Ornements.kt`, qui pilote sa propre progression) doit avoir son fichier
 * dans `assets/lottie/`, et chaque fichier doit être un Lottie valide (les clés `v`, `fr`, `ip`,
 * `op`, `layers` non vide). Aucun registre central de noms dans le code de production : cette
 * liste **est** l'inventaire des appels, reconstituée à la main à chaque animation ajoutée.
 */
class AnimationsTest {

    // Une entrée par appelant de production. Mutation : renommer `assets/lottie/clap-2.json` en
    // `clap-3.json` sans toucher à cette liste (ou au code) fait rougir la premiere assertion
    // ci-dessous, sur `clap-2` seul.
    private val nomsReferences = listOf(
        "clap-2", // FilmEnregistreCalque.kt
        "trophee-1", // AnneeDansLaBoiteCalque.kt (ContenuRecompense)
        "confetti-1", // AnneeDansLaBoiteCalque.kt (la pluie)
        "bobine-1", // Ornements.kt, BobineIndicateur
        "ticket-1", // TicketVoyage.kt, TicketCalque
        "projecteur-1", // CartonTitreMonde.kt
    )

    private val dossierAssets = File("src/main/assets/lottie")

    @Test
    fun `chaque animation referencee a son fichier`() {
        nomsReferences.forEach { nom ->
            val fichier = File(dossierAssets, "$nom.json")
            assertTrue("fichier manquant pour l'animation '$nom' : ${fichier.path}", fichier.isFile)
        }
    }

    // Mutation : retirer la clé `layers` (ou la vider) d'un des six JSON fait rougir cette
    // assertion sur ce seul fichier, sans toucher à la précédente qui ne regarde que la présence
    // du fichier lui-même.
    @Test
    fun `chaque animation est un lottie valide`() {
        nomsReferences.forEach { nom ->
            val fichier = File(dossierAssets, "$nom.json")
            val racine = Json.parseToJsonElement(fichier.readText()).jsonObject
            listOf("v", "fr", "ip", "op").forEach { cle ->
                assertTrue("cle '$cle' absente dans $nom.json", racine.containsKey(cle))
            }
            val layers = racine["layers"]?.jsonArray
            assertTrue("'layers' absent ou vide dans $nom.json", layers != null && layers.isNotEmpty())
        }
    }
}
