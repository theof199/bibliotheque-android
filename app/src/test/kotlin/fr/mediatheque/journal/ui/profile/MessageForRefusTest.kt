package fr.mediatheque.journal.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le message affiché pour chaque code de refus Firebase (brief §2 de la revue du 14 septembre
 * 2026, après un premier essai réel qui a montré `EMAIL_NOT_FOUND` — ce projet Firebase rend les
 * codes classiques, pas `INVALID_LOGIN_CREDENTIALS`). Chaque test déplace mentalement son code vers
 * une autre branche pour vérifier qu'il casse : voir les mutations essayées et rapportées.
 */
class MessageForRefusTest {

    @Test
    fun `EMAIL_NOT_FOUND — aucun compte avec cet e-mail, sans reessai`() {
        val (message, retryable) = messageForRefus("EMAIL_NOT_FOUND")
        assertEquals("Aucun compte SensCritique avec cet e-mail.", message)
        assertFalse(retryable)
    }

    @Test
    fun `INVALID_PASSWORD — identifiants refuses, sans reessai`() {
        val (message, retryable) = messageForRefus("INVALID_PASSWORD")
        assertEquals("Identifiants refusés.", message)
        assertFalse(retryable)
    }

    @Test
    fun `INVALID_LOGIN_CREDENTIALS — meme message que INVALID_PASSWORD`() {
        val (message, retryable) = messageForRefus("INVALID_LOGIN_CREDENTIALS")
        assertEquals("Identifiants refusés.", message)
        assertFalse(retryable)
    }

    @Test
    fun `USER_DISABLED — compte desactive, sans reessai`() {
        val (message, retryable) = messageForRefus("USER_DISABLED")
        assertEquals("Ce compte SensCritique est désactivé.", message)
        assertFalse(retryable)
    }

    @Test
    fun `TOO_MANY_ATTEMPTS_TRY_LATER — trop d essais, avec reessai`() {
        val (message, retryable) = messageForRefus("TOO_MANY_ATTEMPTS_TRY_LATER")
        assertEquals("Trop d’essais, réessaie plus tard.", message)
        assertTrue(retryable)
    }

    @Test
    fun `un code inconnu mais lisible se lit tel quel, sans reessai`() {
        val (message, retryable) = messageForRefus("WEAK_PASSWORD")
        assertEquals("SensCritique a refusé la connexion (WEAK_PASSWORD).", message)
        assertFalse(retryable)
    }

    @Test
    fun `un code nul (corps de refus illisible) rend le message generique, avec reessai`() {
        val (message, retryable) = messageForRefus(null)
        assertEquals("SensCritique est injoignable.", message)
        assertTrue(retryable)
    }
}
