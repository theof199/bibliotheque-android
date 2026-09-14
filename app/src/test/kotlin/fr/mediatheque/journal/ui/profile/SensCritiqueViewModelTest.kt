package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.senscritique.FakeSensCritiqueAuthClient
import fr.mediatheque.journal.senscritique.InMemorySensCritiqueStore
import fr.mediatheque.journal.senscritique.QueuedPush
import fr.mediatheque.journal.senscritique.SensCritiqueAuth
import fr.mediatheque.journal.senscritique.SignInOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** L'écran de connexion SensCritique (brief du 14 septembre 2026) — jumeau de `LoginViewModelTest`. */
class SensCritiqueViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = InMemorySensCritiqueStore()
    private val client = FakeSensCritiqueAuthClient()
    private val vm = SensCritiqueViewModel(store, client)

    @Test
    fun `une connexion reussie stocke le cookieRef, la dateExpiration et le pseudo, jamais le mot de passe`() {
        client.onSignIn = { _, _ -> SignInOutcome.Success("cookie-1", "2026-10-14T10:00:00Z", "TheofB") }
        vm.email = "theo@example.com"
        vm.password = "secret"
        vm.connect()

        assertEquals("TheofB", vm.ui.value.connectedPseudo)
        assertEquals(SensCritiqueAuth("cookie-1", "2026-10-14T10:00:00Z", "TheofB"), store.readAuth())
        assertEquals("", vm.email)
        assertEquals("", vm.password)
    }

    // Mutation : appeler `authClient.signIn(email, password)` sans `.trim()` sur l'email ferait
    // echouer cette assertion des qu'un espace traine (copier-coller frequent d'un clavier mobile).
    @Test
    fun `l email est nettoye des espaces avant l envoi`() {
        client.onSignIn = { email, _ ->
            if (email == "theo@example.com") SignInOutcome.Success("cookie-1", "2026-10-14T10:00:00Z", "TheofB")
            else SignInOutcome.Unreachable
        }
        vm.email = "  theo@example.com  "
        vm.password = "secret"
        vm.connect()
        assertEquals("TheofB", vm.ui.value.connectedPseudo)
    }

    // Brief du 14 septembre 2026, apres un premier essai reel : contrairement a Firebase, l'API
    // GraphQL de SensCritique ne rend pas de code catalogue pour la mutation de connexion — le
    // meme message s'affiche quel que soit le code, y compris nul (corps de refus illisible).
    // Mutation : afficher `outcome.code` dans le message (au lieu du texte fixe) ferait echouer ces
    // deux assertions des que le code change.
    @Test
    fun `un refus GraphQL affiche Identifiants refuses, quel que soit le code, sans reessai`() {
        client.onSignIn = { _, _ -> SignInOutcome.Refused("auth/wrong-password") }
        vm.email = "theo@example.com"
        vm.password = "faux"
        vm.connect()

        assertEquals("Identifiants refusés.", vm.ui.value.error)
        assertFalse(vm.ui.value.retryable)
        assertNull(store.readAuth())
    }

    @Test
    fun `un refus GraphQL sans code lisible affiche le meme message`() {
        client.onSignIn = { _, _ -> SignInOutcome.Refused(null) }
        vm.email = "theo@example.com"
        vm.password = "faux"
        vm.connect()

        assertEquals("Identifiants refusés.", vm.ui.value.error)
        assertFalse(vm.ui.value.retryable)
        assertNull(store.readAuth())
    }

    @Test
    fun `SensCritique injoignable — message dedie, avec reessai`() {
        client.onSignIn = { _, _ -> SignInOutcome.Unreachable }
        vm.email = "theo@example.com"
        vm.password = "x"
        vm.connect()

        assertEquals("SensCritique est injoignable.", vm.ui.value.error)
        assertTrue(vm.ui.value.retryable)
    }

    // « Efface tout, file comprise » (brief §1). Mutation : oublier `store.clear()` (ne garder que
    // `_ui.value = SensCritiqueUi()`) laisse cette assertion en echec — l'ecran dirait
    // « Non connecte » alors que le jeton et la file resteraient stockes.
    @Test
    fun `deconnecter efface le magasin en entier, file comprise`() {
        store.writeAuth(SensCritiqueAuth("cookie-1", "2026-10-14T10:00:00Z", "TheofB"))
        store.writeQueue(mapOf("m1" to QueuedPush("m1", "Chihiro", null, 2001, 8, "2026-09-10", 42L)))
        store.writeDecisions(mapOf("m2" to null))

        vm.disconnect()

        assertNull(vm.ui.value.connectedPseudo)
        assertNull(store.readAuth())
        assertTrue(store.readQueue().isEmpty())
        assertTrue(store.readDecisions().isEmpty())
    }

    @Test
    fun `refresh relit le magasin — utile apres une deconnexion par le rejeu de la file`() {
        store.writeAuth(SensCritiqueAuth("cookie-1", "2026-10-14T10:00:00Z", "TheofB"))
        val vmFraiche = SensCritiqueViewModel(store, client) // lu a la construction
        assertEquals("TheofB", vmFraiche.ui.value.connectedPseudo)

        store.writeAuth(null) // simule la deconnexion faite par SensCritiqueSync (jeton refuse)
        vmFraiche.refresh()
        assertNull(vmFraiche.ui.value.connectedPseudo)
    }

    // Mineur a de la revue du 14 septembre 2026 : ni le formulaire (email, mot de passe), ni
    // l'erreur d'une tentative precedente ne doivent survivre a la sortie de l'ecran — sinon la
    // prochaine visite les retrouverait, ce `ViewModel` etant indexe sur l'Activite. Mutation : ne
    // vider que `email` (oublier `password`, ou l'erreur) fait echouer l'assertion correspondante.
    @Test
    fun `clearCredentials efface l email, le mot de passe et l erreur d une tentative refusee`() {
        client.onSignIn = { _, _ -> SignInOutcome.Refused("auth/wrong-password") }
        vm.email = "theo@example.com"
        vm.password = "secret"
        vm.connect()
        assertEquals("Identifiants refusés.", vm.ui.value.error) // etat de depart : une erreur affichee

        vm.clearCredentials()

        assertEquals("", vm.email)
        assertEquals("", vm.password)
        assertNull(vm.ui.value.error)
        assertFalse(vm.ui.value.retryable)
    }

    // Le pseudo connu ne doit jamais disparaitre a l'appel : c'est ce que `ProfileScreen` continue
    // de lire hors visite de l'ecran SensCritique.
    @Test
    fun `clearCredentials ne touche pas au pseudo connecte`() {
        store.writeAuth(SensCritiqueAuth("cookie-1", "2026-10-14T10:00:00Z", "TheofB"))
        val vmConnecte = SensCritiqueViewModel(store, client)
        assertEquals("TheofB", vmConnecte.ui.value.connectedPseudo)

        vmConnecte.clearCredentials()

        assertEquals("TheofB", vmConnecte.ui.value.connectedPseudo)
    }
}
