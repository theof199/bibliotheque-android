package fr.mediatheque.journal.ui.suivis

import fr.mediatheque.journal.api.dto.CollectionResult
import fr.mediatheque.journal.api.dto.PersonneResult
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.Saga

/**
 * Ce qu'un réalisateur et une saga ont en commun pour l'écran partagé (brief
 * « les sagas », 15 septembre 2026) : un identifiant TMDB, un nom, une image
 * — qui s'appelle `profile_url` chez l'un et `cover_url` chez l'autre, d'où
 * ces quatre petites fonctions plutôt qu'un DTO réseau commun forcé sur les
 * deux formes. `tmdbId` a le même défaut que côté back : deux espaces
 * d'identifiants distincts (personnes, collections), jamais mélangés au delà
 * de cette seule vue.
 */
data class EntiteSuivie(val tmdbId: Int, val nom: String, val imageUrl: String?)

fun Realisateur.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, profile_url)
fun PersonneResult.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, profile_url)
fun Saga.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, cover_url)
fun CollectionResult.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, cover_url)
