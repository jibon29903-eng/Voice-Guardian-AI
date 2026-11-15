package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.StudioProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_and_addConversation_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = JarvisFirestoreRepository(firestore)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile(uid, "Alice")
        }
        assertEquals(uid, profile.userId)

        val msgId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addConversationMessage(
                userId = uid,
                sessionId = "s1",
                role = "user",
                content = "Hello Jarvis",
                language = "en",
                source = "voice"
            )
        }
        assertTrue(msgId.isNotBlank())

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeConversations(uid).first { list -> list.any { it.id == msgId } }
        }
        assertTrue(messages.any { it.id == msgId && it.content == "Hello Jarvis" })
    }

    @Test
    fun saveStudioProject_and_upsertForexWatchItem_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = JarvisFirestoreRepository(firestore)

        val projId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveStudioProject(
                userId = uid,
                project = StudioProject(
                    userId = uid,
                    title = "Landing Page",
                    prompt = "Create a landing page",
                    framework = "html_css_js",
                    htmlCode = "<h1>Hello</h1>",
                    cssCode = "body { color: cyan; }",
                    jsCode = "console.log('ok');",
                    lastDiagnostic = "No errors"
                )
            )
        }
        assertTrue(projId.isNotBlank())

        val pairId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertForexWatchItem(uid, "EUR", "USD", 1.12, "above")
        }
        assertEquals("EUR_USD", pairId)
    }

    @Test
    fun crossUserRead_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = JarvisFirestoreRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveMemory(aliceUid, "Pref", "Dark theme", "preference", true)
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = JarvisFirestoreRepository(firestore)
        var captured: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeMemories(aliceUid).first()
            }
        } catch (t: Throwable) {
            captured = t
        }
        val fsEx = captured?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for cross-user read", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun unauthenticatedRead_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = JarvisFirestoreRepository(firestore)
        var captured: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeConversations("some_user_123").first()
            }
        } catch (t: Throwable) {
            captured = t
        }
        val fsEx = captured?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException for unauthenticated read", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_jarvis@test.com"
        const val BOB_EMAIL = "bob_jarvis@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
