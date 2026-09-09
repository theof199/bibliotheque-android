package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/** L'enveloppe d'erreur du back : `message` est rédigé pour être affiché tel quel. */
@Serializable
data class ApiErrorBody(val code: String, val message: String, val retryable: Boolean = false)
