package fr.mediatheque.journal.ui.login

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val api = FakeJournalApi()
    private var connecte: User? = null
    private val vm = LoginViewModel(api) { connecte = it }

    @Test
    fun `se connecte et previent`() {
        vm.pseudo = "alice"; vm.password = "secret"
        vm.submit()
        assertEquals(FakeJournalApi.ALICE, connecte)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun `refuse un champ vide sans appeler le back`() {
        vm.pseudo = "alice"; vm.password = ""
        vm.submit()
        assertTrue(api.calls.isEmpty())
        assertTrue(vm.ui.value.passwordInvalid)
        assertNull(connecte)
    }

    @Test
    fun `affiche le message du back tel quel`() {
        api.onLogin = { _, _ -> throw FakeJournalApi.refused() }
        vm.pseudo = "alice"; vm.password = "faux"
        vm.submit()
        assertEquals("Pseudo ou mot de passe incorrect.", vm.ui.value.error)
        assertNull(vm.ui.value.blockedUntilMillis)
    }

    @Test
    fun `un 429 bloque le bouton pour la duree annoncee`() {
        api.onLogin = { _, _ -> throw FakeJournalApi.rateLimited(900) }
        vm.pseudo = "alice"; vm.password = "x"
        val avant = System.currentTimeMillis()
        vm.submit()
        val apres = System.currentTimeMillis()
        val jusqua = vm.ui.value.blockedUntilMillis
        assertNotNull(jusqua)
        assertTrue(jusqua!! >= avant + 900_000L - 1_000L)
        assertTrue(jusqua <= apres + 900_000L)
        assertEquals("Trop de tentatives.", vm.ui.value.error)
    }

    @Test
    fun `un second essai pendant le blocage du 429 n appelle pas le back`() {
        api.onLogin = { _, _ -> throw FakeJournalApi.rateLimited(900) }
        vm.pseudo = "alice"; vm.password = "x"
        vm.submit()

        api.onLogin = { _, _ -> FakeJournalApi.ALICE }
        vm.submit()

        assertEquals(listOf("login alice"), api.calls)
        assertNull(connecte)
    }

    @Test
    fun `une panne reseau se dit en une phrase fixe`() {
        api.onLogin = { _, _ -> throw FakeJournalApi.network() }
        vm.pseudo = "alice"; vm.password = "x"
        vm.submit()
        assertEquals("L’API est injoignable.", vm.ui.value.error)
    }

    @Test
    fun `un refus de connexion ne propose pas de reessai`() {
        api.onLogin = { _, _ -> throw FakeJournalApi.refused() }
        vm.pseudo = "alice"; vm.password = "faux"
        vm.submit()
        assertFalse(vm.ui.value.retryable)
    }

    @Test
    fun `une panne reseau a la connexion propose un reessai`() {
        api.onLogin = { _, _ -> throw FakeJournalApi.network() }
        vm.pseudo = "alice"; vm.password = "x"
        vm.submit()
        assertTrue(vm.ui.value.retryable)
    }

    @Test
    fun `reset vide le pseudo, le mot de passe et l etat`() {
        api.onLogin = { _, _ -> throw FakeJournalApi.refused() }
        vm.pseudo = "alice"; vm.password = "faux"
        vm.submit()
        assertEquals("Pseudo ou mot de passe incorrect.", vm.ui.value.error)

        vm.reset()

        assertEquals("", vm.pseudo)
        assertEquals("", vm.password)
        assertEquals(LoginUi(), vm.ui.value)
    }
}
