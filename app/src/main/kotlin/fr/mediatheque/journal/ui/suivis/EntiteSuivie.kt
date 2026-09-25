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
 *
 * `ajouteLe` (Suivis, rétrospectives et cycles, 25 septembre 2026) : le `ajoute_le` ISO du back, qui
 * trie une entité sans film vu et dit « ajouté le … » sous son nom. Vide pour un résultat de
 * recherche (`PersonneResult`, `CollectionResult`), qui n'a pas encore été ajouté — il ne sert
 * qu'à choisir, jamais à être trié ni affiché en carte. Vide par défaut, pour les tests qui ne
 * parlent pas de tri.
 */
data class EntiteSuivie(val tmdbId: Int, val nom: String, val imageUrl: String?, val ajouteLe: String = "")

fun Realisateur.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, profile_url, ajoute_le)
fun PersonneResult.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, profile_url, ajouteLe = "")
fun Saga.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, cover_url, ajoute_le)
fun CollectionResult.versEntite(): EntiteSuivie = EntiteSuivie(tmdb_id, name, cover_url, ajouteLe = "")
