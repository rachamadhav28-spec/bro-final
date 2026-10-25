package com.jarvis.agent

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class GitHubManager(private val tokenProvider: () -> String) {

    // SAFETY INTERCEPTOR: Hard-blocks any HTTP DELETE request to GitHub API
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.method.equals("DELETE", ignoreCase = true)) {
                throw SecurityException("JARVIS SAFETY GUARDRAIL: Delete operations on GitHub are strictly forbidden!")
            }
            chain.proceed(request)
        }
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Creates a new GitHub Repository (No Delete allowed)
     */
    suspend fun createRepo(repoName: String, description: String, isPrivate: Boolean = false): String = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        if (token.isBlank()) return@withContext "Error: GitHub Personal Access Token is missing in Settings."

        val url = "https://api.github.com/user/repos"
        val payload = JSONObject().apply {
            put("name", repoName)
            put("description", description)
            put("private", isPrivate)
            put("auto_init", true)
        }

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { res ->
            if (res.isSuccessful) {
                "Successfully created repository '$repoName'."
            } else {
                "GitHub Repo Creation Failed (${res.code}): ${res.body?.string()}"
            }
        }
    }

    /**
     * Creates or updates a file in a GitHub repository (Supports regular code files and .github/workflows/*.yml)
     */
    suspend fun pushFileToRepo(
        owner: String,
        repo: String,
        filePath: String,
        fileContent: String,
        commitMessage: String,
        branch: String = "main"
    ): String = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        if (token.isBlank()) return@withContext "Error: GitHub Personal Access Token is missing in Settings."

        val url = "https://api.github.com/repos/$owner/$repo/contents/$filePath"

        // Step 1: Check if file already exists to retrieve its SHA for updating
        var existingSha: String? = null
        val getReq = Request.Builder()
            .url("$url?ref=$branch")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        client.newCall(getReq).execute().use { res ->
            if (res.isSuccessful) {
                val bodyStr = res.body?.string() ?: "{}"
                existingSha = JSONObject(bodyStr).optString("sha", null)
            }
        }

        // Step 2: Base64 encode content and PUT to GitHub
        val encodedContent = Base64.encodeToString(fileContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("message", commitMessage)
            put("content", encodedContent)
            put("branch", branch)
            if (!existingSha.isNullOrBlank()) {
                put("sha", existingSha)
            }
        }

        val putReq = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .put(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(putReq).execute().use { res ->
            if (res.isSuccessful) {
                "Successfully pushed '$filePath' to $owner/$repo ($branch)."
            } else {
                "GitHub Push Failed (${res.code}): ${res.body?.string()}"
            }
        }
    }

    /**
     * Triggers a GitHub Actions workflow_dispatch event
     */
    suspend fun triggerWorkflow(
        owner: String,
        repo: String,
        workflowIdOrFileName: String,
        ref: String = "main"
    ): String = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        if (token.isBlank()) return@withContext "Error: GitHub Token missing."

        val url = "https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowIdOrFileName/dispatches"
        val payload = JSONObject().apply {
            put("ref", ref)
        }

        val postReq = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(postReq).execute().use { res ->
            if (res.code == 204 || res.isSuccessful) {
                "Successfully triggered workflow '$workflowIdOrFileName' on $owner/$repo ($ref)."
            } else {
                "Failed to trigger workflow (${res.code}): ${res.body?.string()}"
            }
        }
    }
}
