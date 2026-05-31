package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(prompt: String, systemInstruction: String? = null): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured.")
            return@withContext "AI 密钥未配置，请在 AI Studio 的 Secrets 面板中设置 GEMINI_API_KEY。"
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val mediaType = "application/json".toMediaType()

        try {
            val root = JSONObject()
            val contentsArray = JSONArray()
            val contentObj = JSONObject()
            val partsArray = JSONArray()
            val partObj = JSONObject()
            partObj.put("text", prompt)
            partsArray.put(partObj)
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
            root.put("contents", contentsArray)

            if (systemInstruction != null) {
                val sysObj = JSONObject()
                val sysPartsArray = JSONArray()
                val sysPartObj = JSONObject()
                sysPartObj.put("text", systemInstruction)
                sysPartsArray.put(sysPartObj)
                sysObj.put("parts", sysPartsArray)
                root.put("systemInstruction", sysObj)
            }

            val config = JSONObject()
            config.put("temperature", 0.7)
            root.put("generationConfig", config)

            val requestBody = root.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "Unsuccessful response from Gemini: Code ${response.code}, Body $bodyString")
                return@withContext "AI 助手暂时无法响应（错误码 ${response.code}）"
            }

            val responseJson = JSONObject(bodyString)
            val candidates = responseJson.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val responseContent = candidate.optJSONObject("content")
                val responseParts = responseContent?.optJSONArray("parts")
                if (responseParts != null && responseParts.length() > 0) {
                    val firstPart = responseParts.getJSONObject(0)
                    return@withContext firstPart.optString("text", "无内容返回")
                }
            }
            "AI 助手暂未返回有效文本。"
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini API request", e)
            "AI 助手请求异常: ${e.message}"
        }
    }
}
