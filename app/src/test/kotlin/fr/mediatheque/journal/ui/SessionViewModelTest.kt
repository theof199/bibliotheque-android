package fr.mediatheque.journal.ui

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.InMemorySessionStore
import fr.mediatheque.journal.api.SessionCookieJar
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

    @Test
    fun `sans cookie, deconnecte sans appeler le back`() {
        val vm = SessionViewModel(api, SessionCookieJar(InMemorySessionStore()))
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `avec cookie, demande qui est connecte`() {
        val vm = SessionViewModel(api, jarAvecSession())
        assertEquals(SessionState.SignedIn(FakeJournalApi.ALICE), vm.state.value)
        assertEquals(listOf("me"), api.calls)
    }

    @Test
    fun `un 401 au lancement efface le cookie et deconnecte`() {
        api.onMe = { throw FakeJournalApi.unauthorized() }
        val jar = jarAvecSession()
        val vm = SessionViewModel(api, jar)
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertFalse(jar.hasSession)
    }

    @Test
    fun `une panne reseau au lancement se dit, et se reessaie`() {
        api.onMe = { throw FakeJournalApi.network() }
        val vm = SessionViewModel(api, jarAvecSession())
        assertTrue(vm.state.value is SessionState.Unreachable)

        api.onMe = { FakeJournalApi.ALICE }
        vm.retry()
        assertEquals(SessionState.SignedIn(FakeJournalApi.ALICE), vm.state.value)
    }

    @Test
    fun `se deconnecter appelle le back, efface le cookie, meme si le back echoue`() {
        api.onLogout = { throw FakeJournalApi.network() }
        val jar = jarAvecSession()
        val vm = SessionViewModel(api, jar)
        vm.signOut()
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertFalse(jar.hasSession)
        assertTrue(api.calls.contains("logout"))
    }

    @Test
    fun `expirer deconnecte sans appeler le back`() {
        val jar = jarAvecSession()
        val vm = SessionViewModel(api, jar)
        api.calls.clear()
        vm.expire()
        assertEquals(SessionState.SignedOut, vm.state.value)
        assertFalse(jar.hasSession)
        assertTrue(api.calls.isEmpty())
    }
}
