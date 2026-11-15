package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.model.GeneratedImageItem
import com.example.data.model.JarvisLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class GeneratedWebProjectResult(
    val title: String,
    val framework: String,
    val htmlCode: String,
    val cssCode: String,
    val jsCode: String,
    val diagnostic: String
)

data class CodeDiagnosticResult(
    val issueSummary: String,
    val fixedHtml: String,
    val fixedCss: String,
    val fixedJs: String,
    val changeNotes: String
)

data class SynthesizedResearchResult(
    val summary: String,
    val verificationNotes: String
)

class JarvisAiRepository(
    context: Context
) {
    private val appContext = context.applicationContext

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are JARVIS AI – Voice Guardian, a precise, helpful, honest personal assistant. " +
                        "Respond concisely and clearly. Adapt seamlessly to English, Bangla (বাংলা), or Arabic (العربية) as requested. " +
                        "Never fabricate live stock/forex prices, citations, or capabilities. If a user asks for a sensitive action " +
                        "(posting externally, deleting all data, executing financial trades), explicitly ask for confirmation first."
                )
            }
        )
    }

    private val studioSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short project title"),
            "framework" to Schema.enumeration(
                values = listOf("html_css_js", "react_cdn"),
                description = "Project stack"
            ),
            "htmlCode" to Schema.string(description = "Complete body or document HTML markup"),
            "cssCode" to Schema.string(description = "Complete CSS stylesheet"),
            "jsCode" to Schema.string(description = "Complete JavaScript or React JSX/UMD script"),
            "diagnostic" to Schema.string(description = "Summary of architecture and self-check notes")
        )
    )

    private val studioModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = studioSchema
            }
        )
    }

    private val fixSchema = Schema.obj(
        properties = mapOf(
            "issueSummary" to Schema.string(description = "Detected coding/runtime/accessibility issues"),
            "fixedHtml" to Schema.string(description = "Fixed HTML code"),
            "fixedCss" to Schema.string(description = "Fixed CSS code"),
            "fixedJs" to Schema.string(description = "Fixed JavaScript code"),
            "changeNotes" to Schema.string(description = "Concise bullet diff summary of changes made")
        )
    )

    private val codeFixModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = fixSchema
            }
        )
    }

    private val researchSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Well-structured factual summary grounded only in the provided sources"),
            "verificationNotes" to Schema.string(description = "Claim verification status, dates from sources, and any gaps")
        )
    )

    private val researchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = researchSchema
            }
        )
    }

    private val imageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    fun streamConversation(
        userPrompt: String,
        historyPairs: List<Pair<String, String>>,
        language: JarvisLanguage,
        approvedMemories: List<String>
    ): Flow<String> = flow {
        val memoryBlock = if (approvedMemories.isNotEmpty()) {
            "User-approved context memories:\n- " + approvedMemories.joinToString("\n- ") + "\n\n"
        } else ""

        val languageInstruction = when (language) {
            JarvisLanguage.ENGLISH -> "Respond in clear English."
            JarvisLanguage.BANGLA -> "Respond naturally in Bangla (বাংলা)."
            JarvisLanguage.ARABIC -> "Respond naturally in Arabic (العربية)."
        }

        val chatHistory: List<Content> = historyPairs.takeLast(12).map { (role, text) ->
            val mappedRole = if (role == "assistant") "model" else "user"
            content(mappedRole) { text(text) }
        }

        val chat = chatModel.startChat(history = chatHistory)
        val fullPrompt = "$memoryBlock[$languageInstruction]\n$userPrompt"

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chat.sendMessageStream(fullPrompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Gemini returned an empty response.")
        }
    }

    suspend fun generateWebsiteProject(
        description: String,
        framework: String,
        language: JarvisLanguage
    ): GeneratedWebProjectResult = withContext(Dispatchers.IO) {
        val prompt = """
            Build a complete, responsive, mobile-first single-page web app for the following request:
            "$description"
            Target framework: $framework
            If framework is "react_cdn", write clean browser-compatible React 18 code using React.createElement (no JSX transpiler needed) mounted into #root.
            If framework is "html_css_js", write clean semantic HTML5 body content (without <html>/<head> wrappers if possible, or full valid markup), modern CSS3, and vanilla JS.
            User preferred language for visible UI copy: ${language.displayName}.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            studioModel.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Empty AI Studio response.")
        val json = JSONObject(rawJson)
        GeneratedWebProjectResult(
            title = json.optString("title", "Jarvis Web App"),
            framework = json.optString("framework", framework),
            htmlCode = json.optString("htmlCode"),
            cssCode = json.optString("cssCode"),
            jsCode = json.optString("jsCode"),
            diagnostic = json.optString("diagnostic", "Generated cleanly.")
        )
    }

    suspend fun diagnoseAndFixWebsite(
        htmlCode: String,
        cssCode: String,
        jsCode: String,
        errorLog: String
    ): CodeDiagnosticResult = withContext(Dispatchers.IO) {
        val prompt = """
            Inspect the following HTML, CSS, JavaScript, and runtime console error log.
            Identify bugs, syntax issues, null DOM references, layout overflows on mobile, and fix them cleanly.
            Console / Error log:
            $errorLog

            HTML:
            $htmlCode

            CSS:
            $cssCode

            JS:
            $jsCode
        """.trimIndent()

        val response = withTimeout(60_000L) {
            codeFixModel.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Empty code diagnostic response.")
        val json = JSONObject(rawJson)
        CodeDiagnosticResult(
            issueSummary = json.optString("issueSummary", "No critical issues detected."),
            fixedHtml = json.optString("fixedHtml", htmlCode),
            fixedCss = json.optString("fixedCss", cssCode),
            fixedJs = json.optString("fixedJs", jsCode),
            changeNotes = json.optString("changeNotes", "Refined code structure and safety checks.")
        )
    }

    suspend fun synthesizeResearch(
        query: String,
        hits: List<WebSearchHit>,
        language: JarvisLanguage
    ): SynthesizedResearchResult = withContext(Dispatchers.IO) {
        if (hits.isEmpty()) {
            return@withContext SynthesizedResearchResult(
                summary = "No live search sources were returned for \"$query\". Unable to verify claims without external sources.",
                verificationNotes = "0 verified sources retrieved. Try refining the search terms or checking network connectivity."
            )
        }
        val sourcesBlock = hits.mapIndexed { idx, hit ->
            "[${idx + 1}] Title: ${hit.title}\nProvider: ${hit.provider}\nDate/Tag: ${hit.timestamp}\nURL: ${hit.url}\nSnippet: ${hit.snippet}"
        }.joinToString("\n\n")

        val prompt = """
            You are conducting Deep Research in ${language.displayName}.
            Query: "$query"
            STRICT RULES:
            - Use ONLY the retrieved sources below.
            - NEVER invent URLs, statistics, or dates not present in the sources.
            - Cite sources inline like [1], [2].
            - Explicitly note which claims are verified and where sources lack full detail.

            Retrieved Sources:
            $sourcesBlock
        """.trimIndent()

        val response = withTimeout(60_000L) {
            researchModel.generateContent(prompt)
        }
        val rawJson = response.text ?: throw IllegalStateException("Empty research synthesis.")
        val json = JSONObject(rawJson)
        SynthesizedResearchResult(
            summary = json.optString("summary"),
            verificationNotes = json.optString("verificationNotes")
        )
    }

    suspend fun analyzeImageWithPrompt(
        bitmap: Bitmap,
        question: String,
        language: JarvisLanguage,
        contextType: String = "screen"
    ): String = withContext(Dispatchers.IO) {
        val scaled = scaleBitmapSafely(bitmap, maxDim = 1440)
        val roleHint = if (contextType == "whiteboard") {
            "Analyze this digital whiteboard sketch/diagram. Explain the concepts step-by-step as an interactive educational lesson in ${language.displayName}."
        } else {
            "Analyze this app screen or screenshot. Explain the visible UI elements, answer the user's question accurately, and respect privacy (do not guess hidden personal data). Respond in ${language.displayName}."
        }

        val response = withTimeout(60_000L) {
            chatModel.generateContent(
                content {
                    image(scaled)
                    text("$roleHint\n\nUser request: $question")
                }
            )
        }
        response.text?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Model returned an empty visual analysis.")
    }

    suspend fun generateOrEditImage(
        prompt: String,
        sourceBitmap: Bitmap? = null
    ): GeneratedImageItem = withContext(Dispatchers.IO) {
        val response = withTimeout(60_000L) {
            if (sourceBitmap != null) {
                val scaled = scaleBitmapSafely(sourceBitmap, maxDim = 1280)
                imageModel.generateContent(
                    content {
                        image(scaled)
                        text(prompt)
                    }
                )
            } else {
                imageModel.generateContent(prompt)
            }
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        val outputBitmap = imagePart?.image
            ?: throw IllegalStateException("No image was returned by Gemini image model.")

        saveGeneratedBitmap(outputBitmap, prompt, isEdited = sourceBitmap != null)
    }

    fun saveGeneratedBitmap(
        bitmap: Bitmap,
        prompt: String,
        isEdited: Boolean
    ): GeneratedImageItem {
        val dir = File(appContext.filesDir, "generated_images").apply { mkdirs() }
        val id = "img_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val outFile = File(dir, "$id.png")
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
        }
        return GeneratedImageItem(
            id = id,
            prompt = prompt,
            filePath = outFile.absolutePath,
            createdAtMillis = System.currentTimeMillis(),
            isEdited = isEdited
        )
    }

    fun loadBitmapFromFile(filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap", e)
            null
        }
    }

    fun serializeGalleryItems(items: List<GeneratedImageItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("prompt", item.prompt)
                put("filePath", item.filePath)
                put("createdAtMillis", item.createdAtMillis)
                put("isEdited", item.isEdited)
            })
        }
        return arr.toString()
    }

    fun deserializeGalleryItems(jsonStr: String): List<GeneratedImageItem> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val path = obj.optString("filePath")
                if (!File(path).exists()) return@mapNotNull null
                GeneratedImageItem(
                    id = obj.optString("id"),
                    prompt = obj.optString("prompt"),
                    filePath = path,
                    createdAtMillis = obj.optLong("createdAtMillis"),
                    isEdited = obj.optBoolean("isEdited", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun scaleBitmapSafely(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(
            bitmap,
            (w * ratio).toInt().coerceAtLeast(1),
            (h * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private companion object {
        const val TAG = "JarvisAiRepo"
    }
}
