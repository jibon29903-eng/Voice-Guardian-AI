package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ConversationMessage
import com.example.data.model.ForexWatchItem
import com.example.data.model.ResearchBrief
import com.example.data.model.StudioProject
import com.example.data.model.UserMemoryItem
import com.example.data.model.UserProfile
import com.example.data.model.VoiceCommandEntry
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class JarvisFirestoreRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val docRef = userDoc(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error ->
                logIfFirestoreError(error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun ensureUserProfile(userId: String, displayName: String): UserProfile {
        val docRef = userDoc(userId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                existing.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = userId, userId = userId, displayName = displayName)
            } else {
                val safeName = displayName.take(80).ifBlank { "Commander" }
                val payload = mapOf(
                    "userId" to userId,
                    "displayName" to safeName,
                    "preferredLanguage" to "en",
                    "speechRate" to 1.0,
                    "voiceResponseEnabled" to true,
                    "confirmSensitiveActions" to true,
                    "memoryOptIn" to true,
                    "wakePhraseEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                UserProfile(
                    id = userId,
                    userId = userId,
                    displayName = safeName,
                    preferredLanguage = "en",
                    speechRate = 1.0,
                    voiceResponseEnabled = true,
                    confirmSensitiveActions = true,
                    memoryOptIn = true,
                    wakePhraseEnabled = true,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserProfileSettings(
        userId: String,
        displayName: String? = null,
        preferredLanguage: String? = null,
        speechRate: Double? = null,
        voiceResponseEnabled: Boolean? = null,
        confirmSensitiveActions: Boolean? = null,
        memoryOptIn: Boolean? = null,
        wakePhraseEnabled: Boolean? = null
    ) {
        val docRef = userDoc(userId)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        displayName?.let { updates["displayName"] = it.take(80) }
        preferredLanguage?.let { updates["preferredLanguage"] = it }
        speechRate?.let { updates["speechRate"] = it.coerceIn(0.5, 2.0) }
        voiceResponseEnabled?.let { updates["voiceResponseEnabled"] = it }
        confirmSensitiveActions?.let { updates["confirmSensitiveActions"] = it }
        memoryOptIn?.let { updates["memoryOptIn"] = it }
        wakePhraseEnabled?.let { updates["wakePhraseEnabled"] = it }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeConversations(userId: String): Flow<List<ConversationMessage>> {
        val colRef = userDoc(userId).collection("conversations")
        return colRef.orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ConversationMessage::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun addConversationMessage(
        userId: String,
        sessionId: String,
        role: String,
        content: String,
        language: String,
        source: String
    ): String {
        val docId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = userDoc(userId).collection("conversations").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "sessionId" to sessionId.take(64).ifBlank { "default" },
            "role" to role,
            "content" to content.take(16000),
            "language" to language,
            "source" to source,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun clearConversations(userId: String) {
        val colRef = userDoc(userId).collection("conversations")
        try {
            val docs = colRef.get().await()
            if (docs.isEmpty) return
            val batch = db.batch()
            docs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    fun observeVoiceCommands(userId: String): Flow<List<VoiceCommandEntry>> {
        val colRef = userDoc(userId).collection("voice_commands")
        return colRef.orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(VoiceCommandEntry::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun addVoiceCommand(
        userId: String,
        transcript: String,
        responseSummary: String,
        language: String,
        wakeTriggered: Boolean
    ): String {
        val docId = "cmd_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = userDoc(userId).collection("voice_commands").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "transcript" to transcript.take(1000).ifBlank { "(empty)" },
            "responseSummary" to responseSummary.take(4000),
            "language" to language,
            "wakeTriggered" to wakeTriggered,
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun clearVoiceCommands(userId: String) {
        val colRef = userDoc(userId).collection("voice_commands")
        try {
            val docs = colRef.get().await()
            if (docs.isEmpty) return
            val batch = db.batch()
            docs.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, colRef.path)
            throw e
        }
    }

    fun observeStudioProjects(userId: String): Flow<List<StudioProject>> {
        val colRef = userDoc(userId).collection("studio_projects")
        return colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(StudioProject::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun saveStudioProject(userId: String, project: StudioProject): String {
        val isNew = project.id.isBlank()
        val docId = if (isNew) {
            "proj_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        } else {
            project.id
        }
        val docRef = userDoc(userId).collection("studio_projects").document(docId)
        return try {
            if (isNew) {
                val payload = mapOf(
                    "userId" to userId,
                    "title" to project.title.take(120).ifBlank { "Untitled App" },
                    "prompt" to project.prompt.take(2000).ifBlank { "Generated project" },
                    "framework" to project.framework,
                    "htmlCode" to project.htmlCode.take(60000),
                    "cssCode" to project.cssCode.take(30000),
                    "jsCode" to project.jsCode.take(30000),
                    "lastDiagnostic" to project.lastDiagnostic.take(2000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "title" to project.title.take(120).ifBlank { "Untitled App" },
                    "prompt" to project.prompt.take(2000).ifBlank { "Generated project" },
                    "framework" to project.framework,
                    "htmlCode" to project.htmlCode.take(60000),
                    "cssCode" to project.cssCode.take(30000),
                    "jsCode" to project.jsCode.take(30000),
                    "lastDiagnostic" to project.lastDiagnostic.take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteStudioProject(userId: String, projectId: String) {
        val docRef = userDoc(userId).collection("studio_projects").document(projectId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeResearchBriefs(userId: String): Flow<List<ResearchBrief>> {
        val colRef = userDoc(userId).collection("research_briefs")
        return colRef.orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ResearchBrief::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun saveResearchBrief(
        userId: String,
        query: String,
        summary: String,
        verificationNotes: String,
        sourceTitles: List<String>,
        sourceUrls: List<String>
    ): String {
        val docId = "brief_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = userDoc(userId).collection("research_briefs").document(docId)
        val payload = mapOf(
            "userId" to userId,
            "query" to query.take(500).ifBlank { "Research query" },
            "summary" to summary.take(16000).ifBlank { "No summary" },
            "verificationNotes" to verificationNotes.take(4000),
            "sourceTitles" to sourceTitles.take(20),
            "sourceUrls" to sourceUrls.take(20),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    fun observeForexWatchlist(userId: String): Flow<List<ForexWatchItem>> {
        val colRef = userDoc(userId).collection("forex_watchlist")
        return colRef.orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ForexWatchItem::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun seedDefaultForexWatchlistIfEmpty(userId: String) {
        val colRef = userDoc(userId).collection("forex_watchlist")
        try {
            val snapshot = colRef.limit(1).get().await()
            if (snapshot.isEmpty) {
                val defaults = listOf(
                    "EUR" to "USD",
                    "GBP" to "USD",
                    "USD" to "JPY"
                )
                val batch = db.batch()
                defaults.forEach { (base, quote) ->
                    val docId = "${base}_$quote"
                    val docRef = colRef.document(docId)
                    batch.set(
                        docRef,
                        mapOf(
                            "userId" to userId,
                            "baseCurrency" to base,
                            "quoteCurrency" to quote,
                            "targetAlertRate" to 0.0,
                            "alertDirection" to "none",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colRef.path)
            throw e
        }
    }

    suspend fun upsertForexWatchItem(
        userId: String,
        baseCurrency: String,
        quoteCurrency: String,
        targetAlertRate: Double,
        alertDirection: String
    ): String {
        val cleanBase = baseCurrency.uppercase().take(5)
        val cleanQuote = quoteCurrency.uppercase().take(5)
        val docId = "${cleanBase}_$cleanQuote"
        val docRef = userDoc(userId).collection("forex_watchlist").document(docId)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "targetAlertRate" to targetAlertRate.coerceIn(0.0, 1000000.0),
                        "alertDirection" to alertDirection,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "baseCurrency" to cleanBase,
                        "quoteCurrency" to cleanQuote,
                        "targetAlertRate" to targetAlertRate.coerceIn(0.0, 1000000.0),
                        "alertDirection" to alertDirection,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteForexWatchItem(userId: String, pairId: String) {
        val docRef = userDoc(userId).collection("forex_watchlist").document(pairId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeMemories(userId: String): Flow<List<UserMemoryItem>> {
        val colRef = userDoc(userId).collection("memories")
        return colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserMemoryItem::class.java, ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logIfFirestoreError(error, OperationType.LIST, colRef.path)
                throw error
            }
    }

    suspend fun saveMemory(
        userId: String,
        title: String,
        content: String,
        category: String,
        approved: Boolean = true,
        existingId: String? = null
    ): String {
        val isNew = existingId.isNullOrBlank()
        val docId = existingId?.takeIf { it.isNotBlank() }
            ?: "mem_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = userDoc(userId).collection("memories").document(docId)
        return try {
            if (isNew) {
                docRef.set(
                    mapOf(
                        "userId" to userId,
                        "title" to title.take(120).ifBlank { "Preference" },
                        "content" to content.take(1000).ifBlank { "-" },
                        "category" to category,
                        "approved" to approved,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to title.take(120).ifBlank { "Preference" },
                        "content" to content.take(1000).ifBlank { "-" },
                        "category" to category,
                        "approved" to approved,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteMemory(userId: String, memoryId: String) {
        val docRef = userDoc(userId).collection("memories").document(memoryId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun logIfFirestoreError(error: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        handleFirestoreError(firestoreError ?: error, op, path)
    }
}
