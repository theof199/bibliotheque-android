package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/** Le tableau de bord, réduit aux deux chiffres du profil : `dashboard.periods.{all,year}.counts.finished_by_type.movie`. */
@Serializable
data class StatsResponse(val dashboard: Dashboard)

@Serializable
data class Dashboard(val periods: Periods)

@Serializable
data class Periods(val year: Totals, val all: Totals)

@Serializable
data class Totals(val counts: Counts)

@Serializable
data class Counts(val finished_by_type: Map<String, Int>)

val Counts.movies: Int get() = finished_by_type["movie"] ?: 0
