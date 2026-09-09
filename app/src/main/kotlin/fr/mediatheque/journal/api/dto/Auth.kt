package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginBody(val pseudo: String, val password: String)

@Serializable
data class User(val id: String, val pseudo: String, val identity_color: String)

@Serializable
data class SessionResponse(val user: User)
