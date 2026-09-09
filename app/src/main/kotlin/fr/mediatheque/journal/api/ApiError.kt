package fr.mediatheque.journal.api

/**
 * Ce que toute route peut lever. `message` vient du back et s'affiche tel
 * quel ; les cas particuliers se lisent sur les propriétés, jamais par
 * comparaison de chaîne.
 */
class ApiError(
    val code: String,
    message: String,
    val retryable: Boolean,
    /** Nul quand le réseau n'a pas répondu du tout. */
    val status: Int?,
    /** Secondes, lues sur `Retry-After` quand il existe. */
    val retryAfterSeconds: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isUnauthenticated: Boolean get() = status == 401
    val isRateLimited: Boolean get() = status == 429

    companion object {
        const val NETWORK_CODE = "NETWORK"
        const val NETWORK_MESSAGE = "L’API est injoignable."

        fun network(cause: Throwable) =
            ApiError(NETWORK_CODE, NETWORK_MESSAGE, retryable = true, status = null, cause = cause)
    }
}
