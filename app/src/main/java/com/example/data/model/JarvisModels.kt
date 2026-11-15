package com.example.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class JarvisLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val bcp47Tag: String
) {
    ENGLISH("en", "English", "English", "en-US"),
    BANGLA("bn", "Bangla", "বাংলা", "bn-BD"),
    ARABIC("ar", "Arabic", "العربية", "ar-SA");

    companion object {
        fun fromCode(code: String): JarvisLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val preferredLanguage: String = "en",
    val speechRate: Double = 1.0,
    val voiceResponseEnabled: Boolean = true,
    val confirmSensitiveActions: Boolean = true,
    val memoryOptIn: Boolean = true,
    val wakePhraseEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ConversationMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "default",
    val role: String = "user",
    val content: String = "",
    val language: String = "en",
    val source: String = "chat",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class VoiceCommandEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val transcript: String = "",
    val responseSummary: String = "",
    val language: String = "en",
    val wakeTriggered: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StudioProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val prompt: String = "",
    val framework: String = "html_css_js",
    val htmlCode: String = "",
    val cssCode: String = "",
    val jsCode: String = "",
    val lastDiagnostic: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ResearchBrief(
    @DocumentId val id: String = "",
    val userId: String = "",
    val query: String = "",
    val summary: String = "",
    val verificationNotes: String = "",
    val sourceTitles: List<String> = emptyList(),
    val sourceUrls: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ForexWatchItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val baseCurrency: String = "EUR",
    val quoteCurrency: String = "USD",
    val targetAlertRate: Double = 0.0,
    val alertDirection: String = "none",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val pairLabel: String get() = "$baseCurrency/$quoteCurrency"
}

@IgnoreExtraProperties
data class UserMemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "preference",
    val approved: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class DataFreshness {
    REALTIME_API,
    CACHED_DELAYED,
    UNAVAILABLE
}

data class ForexHistoryPoint(
    val dateLabel: String,
    val rate: Double
)

data class ForexQuote(
    val pair: String,
    val base: String,
    val quote: String,
    val rate: Double?,
    val previousRate: Double?,
    val timestampLabel: String,
    val providerName: String,
    val freshness: DataFreshness,
    val history: List<ForexHistoryPoint> = emptyList(),
    val errorNote: String? = null
) {
    val delta: Double?
        get() = if (rate != null && previousRate != null) rate - previousRate else null
    val deltaPercent: Double?
        get() = if (rate != null && previousRate != null && previousRate != 0.0) {
            ((rate - previousRate) / previousRate) * 100.0
        } else null
}

data class GeneratedImageItem(
    val id: String,
    val prompt: String,
    val filePath: String,
    val createdAtMillis: Long,
    val isEdited: Boolean = false
)

enum class WhiteboardTool {
    PEN,
    RECTANGLE,
    CIRCLE,
    ARROW,
    TEXT
}

data class WhiteboardStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

data class WhiteboardShape(
    val tool: WhiteboardTool,
    val start: Offset,
    val end: Offset,
    val color: Color,
    val strokeWidth: Float,
    val text: String = ""
)

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Streaming(val partialText: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
