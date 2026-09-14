package fr.mediatheque.journal.senscritique

/** Jumeau de `FakeExternalRatingService`, pour la connexion et le renouvellement de jeton. */
class FakeSensCritiqueAuthClient(
    var onSignIn: suspend (String, String) -> SignInOutcome = { _, _ -> SignInOutcome.Unreachable },
    var onRefresh: suspend (String) -> RefreshOutcome = { RefreshOutcome.Refused },
) : SensCritiqueAuthClient {
    val refreshCalls = mutableListOf<String>()
    val signInCalls = mutableListOf<Pair<String, String>>()

    override suspend fun signIn(email: String, password: String): SignInOutcome {
        signInCalls += email to password
        return onSignIn(email, password)
    }

    override suspend fun refresh(refreshToken: String): RefreshOutcome {
        refreshCalls += refreshToken
        return onRefresh(refreshToken)
    }
}
