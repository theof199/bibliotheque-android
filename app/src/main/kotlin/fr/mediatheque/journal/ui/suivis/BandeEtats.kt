package fr.mediatheque.journal.ui.suivis

import fr.mediatheque.journal.api.dto.FilmSuivi

/**
 * L'état d'une case de la bande d'un cycle (Suivis, rétrospectives et cycles, 25 septembre 2026) :
 * vu (en couleur, coché), le prochain à voir (liseré corail), pas encore (atténué, désaturé),
 * introuvable (atténué, tampon « PERDU ») — ce dernier seulement quand « Masquer les introuvables »
 * est coupé.
 */
enum class EtatBande { VU, PROCHAIN, PAS_ENCORE, INTROUVABLE }

/** Une case de la bande : le film et ce qu'elle en montre. */
data class CaseBande(val film: FilmSuivi, val etat: EtatBande)

/**
 * La bande d'un cycle, découpée en rangées de `parRangee` cases (six par défaut), la dernière plus
 * courte au besoin, dans l'ordre du back (de la plus ancienne sortie à la plus récente).
 *
 * - `masquerIntrouvables` : les introuvables quittent la bande ; sinon ils y restent,
 *   `INTROUVABLE` — la marque prime sur le visionnage, comme le tampon sur l'affiche ailleurs.
 * - `PROCHAIN` : le film de `prochainAVoir` (la règle de l'accueil, qui saute déjà les
 *   introuvables) — une case au plus, aucune quand tout est vu.
 * - `VU` : un visionnage journalisé ; le reste `PAS_ENCORE`.
 */
fun disposerBande(films: List<FilmSuivi>, masquerIntrouvables: Boolean, parRangee: Int = 6): List<List<CaseBande>> {
    require(parRangee > 0) { "parRangee doit être positif : $parRangee" }
    val prochain = prochainAVoir(films)
    return films
        .filterNot { masquerIntrouvables && it.introuvable }
        .map { film ->
            val etat = when {
                film.introuvable -> EtatBande.INTROUVABLE
                film.vu != null -> EtatBande.VU
                film === prochain -> EtatBande.PROCHAIN
                else -> EtatBande.PAS_ENCORE
            }
            CaseBande(film, etat)
        }
        .chunked(parRangee)
}

/**
 * Le compte d'une carte de cycle, (vus, total), **tel que la bande le dessine** : décision du
 * 25 septembre 2026, le « 3/6 » de la carte (et « 3 sur 6 » de sa sous-ligne) compte les cases
 * visibles et les cases cochées. Avec « Masquer les introuvables », le total les exclut donc ;
 * sinon il les compte (sans jamais les compter vus, leur case n'étant pas cochée).
 */
fun compteBande(films: List<FilmSuivi>, masquerIntrouvables: Boolean): Pair<Int, Int> {
    val cases = disposerBande(films, masquerIntrouvables).flatten()
    return cases.count { it.etat == EtatBande.VU } to cases.size
}
