package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.TamponVoyage
import fr.mediatheque.journal.api.dto.VoyageResponse
import fr.mediatheque.journal.ui.frise.Recompense
import fr.mediatheque.journal.ui.frise.TamponDecennie
import fr.mediatheque.journal.ui.frise.mondeDeLaDecennie
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Le passeport (décision 3 du brief du 21 septembre 2026, « les récompenses ») : charge ses
 * propres tampons, légers, puis construit le tampon complet du générique au tap — jumeau de
 * `PortefeuilleViewModelTest` à côté.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasseportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    @Test
    fun `refresh charge les tampons depuis voyage, sans journal`() = runTest(dispatcher) {
        api.onVoyage = { VoyageResponse(configure = true, tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z"))) }
        val vm = PasseportViewModel(api) { expire++ }

        vm.refresh()
        runCurrent()

        assertEquals(listOf(1890), vm.ui.value.tampons?.map { it.decennie })
        assertEquals(mondeDeLaDecennie(1890).titreVoyageur, vm.ui.value.tampons?.first()?.titreVoyageur)
        // Aucun appel au journal pour la carte légère du passeport (économie de jetons/réseau).
        assertTrue(api.calls.none { it.startsWith("journal") })
    }

    @Test
    fun `un 401 sur le passeport previent la session`() = runTest(dispatcher) {
        api.onVoyage = { throw FakeJournalApi.unauthorized() }
        val vm = PasseportViewModel(api) { expire++ }

        vm.refresh()
        runCurrent()

        assertEquals(1, expire)
    }

    // Au tap sur un tampon (décision 3) : le journal déjà chargé par la Frise se réutilise tel
    // quel, sans le redemander — mutation : ignorer `journalDejaCharge` ferait apparaître un appel
    // `journal` de plus ici.
    @Test
    fun `ouvrirGenerique reutilise le journal deja charge par la Frise, sans le redemander`() = runTest(dispatcher) {
        api.onVoyage = {
            VoyageResponse(
                configure = true,
                annees = listOf(AnneeVoyage(1895, "ouverte", recompense = "ours")),
                tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z")),
            )
        }
        val vm = PasseportViewModel(api) { expire++ }
        vm.refresh()
        runCurrent()

        val journal = listOf(FakeJournalApi.item("m-a", "2026-02-11", null, emptyList(), null, id = "e-a", title = "Un film", year = 1895))
        var recu: TamponDecennie? = null
        vm.ouvrirGenerique(1890, journal) { recu = it }
        runCurrent()

        assertEquals(listOf("Un film"), recu?.films?.map { it.titre })
        assertEquals(listOf(Recompense.OURS), recu?.recompenses)
        assertTrue(api.calls.none { it.startsWith("journal") })
    }

    // Sans journal déjà chargé (Profil ouvert avant que la Frise n'ait rien tiré) : il se charge
    // lui-même — mutation : ne jamais appeler `journalComplet()` en repli laisserait le générique
    // sans film ni date, même quand le journal existe côté back.
    @Test
    fun `ouvrirGenerique charge le journal lui-meme quand il manque`() = runTest(dispatcher) {
        api.onVoyage = { VoyageResponse(configure = true, tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z"))) }
        api.onJournal = { cursor ->
            if (cursor == null) {
                JournalResponse(
                    listOf(FakeJournalApi.item("m-a", "2026-02-11", null, emptyList(), null, id = "e-a", title = "Un film", year = 1895)),
                    null,
                )
            } else {
                JournalResponse(emptyList(), null)
            }
        }
        val vm = PasseportViewModel(api) { expire++ }
        vm.refresh()
        runCurrent()

        var recu: TamponDecennie? = null
        vm.ouvrirGenerique(1890, emptyList()) { recu = it }
        runCurrent()

        assertEquals(listOf("Un film"), recu?.films?.map { it.titre })
        assertTrue(api.calls.any { it.startsWith("journal") })
    }
}
