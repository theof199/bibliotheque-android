package fr.mediatheque.journal.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormatTest {
    @Test fun `une date en toutes lettres, en francais`() = assertEquals("3 septembre 2026", formatDate("2026-09-03"))
    @Test fun `le premier du mois est ordinalise, 1er pas 1`() = assertEquals("1er septembre 2026", formatDate("2026-09-01"))
    @Test fun `realisateur et annee, separes d une virgule`() = assertEquals("Hayao Miyazaki, 2001", subtitle("Hayao Miyazaki", 2001))
    @Test fun `l un sans l autre`() {
        assertEquals("2001", subtitle(null, 2001))
        assertEquals("Hayao Miyazaki", subtitle("Hayao Miyazaki", null))
        assertEquals("", subtitle(null, null))
    }

    // Décision 2 du brief du 21 septembre 2026, « les dépenses ».
    @Test fun `un mois en toutes lettres, capitalise`() = assertEquals("Août 2026", formatMoisAnnee("2026-08"))
    @Test fun `un mois a deux chiffres reste capitalise`() = assertEquals("Décembre 2025", formatMoisAnnee("2025-12"))

    @Test fun `une decimale, virgule a la francaise`() {
        assertEquals("12,7", formatCentimes(12.7))
        // Mutation : sans l'arrondi à une décimale, ce serait "101,16".
        assertEquals("101,2", formatCentimes(101.16))
    }

    // Revue du 24 septembre 2026, point 4, « titres sans doublon ». Mutation : retirer le
    // `takeIf` (toujours renvoyer `titreOriginal` tel quel) fait tomber la deuxième assertion ;
    // comparer sans tenir compte de la casse ferait tomber la troisième, qui doit rester distincte.
    @Test fun `le titre original ne s affiche que s il differe`() {
        assertEquals("Metropolis", titreOriginalAffiche("Le Voyage de Chihiro", "Metropolis"))
        assertEquals(null, titreOriginalAffiche("Metropolis", "Metropolis"))
        assertEquals(null, titreOriginalAffiche("Metropolis", null))
        assertEquals("METROPOLIS", titreOriginalAffiche("Metropolis", "METROPOLIS"))
    }

    // Suivis, rétrospectives et cycles (25 septembre 2026). Mutations : `< 7` changé en `<= 7` ferait
    // lire 7 jours « il y a 7 jours » ; `< 35` en `< 28` ferait lire 28 jours « il y a 0 mois ».
    private val jeudi = LocalDate.parse("2026-09-25")

    @Test fun `aujourd hui et hier`() {
        assertEquals("aujourd’hui", formatRelatif("2026-09-25", jeudi))
        assertEquals("hier", formatRelatif("2026-09-24", jeudi))
        // Une date à venir (horloge du téléphone en retard) ne dit pas « il y a -1 jours ».
        assertEquals("aujourd’hui", formatRelatif("2026-09-26", jeudi))
    }

    @Test fun `de deux a six jours, en jours`() {
        assertEquals("il y a 2 jours", formatRelatif("2026-09-23", jeudi))
        assertEquals("il y a 6 jours", formatRelatif("2026-09-19", jeudi))
    }

    @Test fun `d une a quatre semaines, accorde`() {
        assertEquals("il y a 1 semaine", formatRelatif("2026-09-18", jeudi))
        assertEquals("il y a 2 semaines", formatRelatif("2026-09-11", jeudi))
        assertEquals("il y a 4 semaines", formatRelatif("2026-08-22", jeudi))
    }

    @Test fun `au dela, en mois`() {
        // 35 jours : la bascule des semaines aux mois.
        assertEquals("il y a 1 mois", formatRelatif("2026-08-21", jeudi))
        // 35 jours par-dessus février : un mois civil tout de même.
        assertEquals("il y a 1 mois", formatRelatif("2026-01-27", LocalDate.parse("2026-03-03")))
        assertEquals("il y a 5 mois", formatRelatif("2026-04-02", jeudi))
    }

    @Test fun `un instant complet se lit a son jour`() =
        assertEquals("hier", formatRelatif("2026-09-24T23:59:00.000Z", jeudi))
}
