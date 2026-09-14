package fr.mediatheque.journal.ui

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.InMemorySessionStore
import fr.mediatheque.journal.api.SessionCookieJar
import fr.mediatheque.journal.senscritique.ExternalPushOutcome
import fr.mediatheque.journal.senscritique.FakeExternalRatingService
import fr.mediatheque.journal.senscritique.InMemorySensCritiqueStore
import fr.mediatheque.journal.senscritique.QueuedPush
import fr.mediatheque.journal.senscritique.SensCritiqueAuth
import fr.mediatheque.journal.senscritique.SensCritiqueStore
import fr.mediatheque.journal.senscritique.SensCritiqueSync
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeJournalApi()
    private val url = "https://mini-mediatheque.fr/api/".toHttpUrl()

    private fun jarAvecSession() = SessionCookieJar(InMemorySessionStore()).apply {
        saveFromResponse(url, listOf(
            Cookie.Builder().name(SessionCookieJar.COOKIE_NAME).value("s").hostOnlyDomain("mini-mediatheque.fr")
                .expiresAt(System.currentTimeMillis() + 60_000).build(),
        ))
    }

    // Magasin SensCritique vide (non connecté) par défaut : `SensCritiqueSync.replayQueue()`
    // ressort tout de suite (`isConnected()` faux), donc ce double ne demande aucun comportement
    // programmé pour la plupart des tests ci-dessous — seul le dernier s'en sert vraiment.
    private fun sync(store: SensCritiqueStore = InMemorySensCritiqueStore()) =
        SensCritiqueSync(FakeExternalRatingService(), store)

    @Test
    fun `sans cookie, deconnecte sans appeler le back`() {
        val vm = SessionViewModel(api, SessionCookieJar(InMemorySessionStore()), sync())
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `avec cookie, demande qui est connecte`() {
        val vm = SessionViewModel(api, jarAvecSession(), sync())
        assertEquals(SessionState.SignedIn(FakeJournalApi.ALICE), vm.state.value)
        assertEquals(listOf("me"), api.calls)
    }

    @Test
    fun `un 401 au lancement efface le cookie et deconnecte`() {
        api.onMe = { throw FakeJournalApi.unauthorized() }
        val jar = jarAvecSession()
        val vm = SessionViewModel(api, jar, sync())
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertFalse(jar.hasSession)
    }

    @Test
    fun `une panne reseau au lancement se dit, et se reessaie`() {
        api.onMe = { throw FakeJournalApi.network() }
        val vm = SessionViewModel(api, jarAvecSession(), sync())
        assertTrue(vm.state.value is SessionState.Unreachable)

        api.onMe = { FakeJournalApi.ALICE }
        vm.retry()
        assertEquals(SessionState.SignedIn(FakeJournalApi.ALICE), vm.state.value)
    }

    @Test
    fun `se deconnecter appelle le back, efface le cookie, meme si le back echoue`() {
        api.onLogout = { throw FakeJournalApi.network() }
        val jar = jarAvecSession()
        val vm = SessionViewModel(api, jar, sync())
        vm.signOut()
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertFalse(jar.hasSession)
        assertTrue(api.calls.contains("logout"))
    }

    @Test
    fun `expirer deconnecte sans appeler le back`() {
        val jar = jarAvecSession()
        val vm = SessionViewModel(api, jar, sync())
        api.calls.clear()
        vm.expire()
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertFalse(jar.hasSession)
        assertTrue(api.calls.isEmpty())
    }

    // La file SensCritique se rejoue au lancement, si connecté (brief §5) : preuve que
    // `SessionViewModel` appelle bien `replayQueue()` sur une connexion reussie, en verifiant son
    // effet de bord (la poussee en file disparait). Mutation : ne jamais appeler `replayQueue()`
    // dans `check()` fait echouer cette assertion (la file resterait pleine).
    @Test
    fun `une connexion reussie rejoue la file SensCritique`() {
        val store = InMemorySensCritiqueStore().apply {
            writeAuth(SensCritiqueAuth("refresh", "TheofB"))
            writeQueue(mapOf("m1" to QueuedPush("m1", "Chihiro", null, 2001, 8, "2026-09-10", productId = 42L)))
        }
        val service = FakeExternalRatingService(onPush = { _, _, _ -> ExternalPushOutcome.Success })
        SessionViewModel(api, jarAvecSession(), SensCritiqueSync(service, store))
        assertTrue("la poussee en file doit avoir ete tentee", store.readQueue().isEmpty())
    }
}
