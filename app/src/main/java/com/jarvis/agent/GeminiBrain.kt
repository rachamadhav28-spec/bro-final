package com.jarvis.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiBrain(
    private val apiKeyProvider: () -> String,
    private val systemExecutor: SystemActionExecutor,
    private val githubManager: GitHubManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val systemInstruction = """
        You are J.A.R.V.I.S., an advanced autonomous Android AI Agent & Senior Software Engineer.
        Whenever the user asks you to perform a device action, GitHub action, app automation, or write code, respond strictly in valid JSON format matching this schema:
        {
          "spoken_reply": "Concise Jarvis-style confirmation to speak out loud",
          "action": "NONE | SET_ALARM | SET_TIMER | SEND_GMAIL | OPEN_APP | TOGGLE_CONNECTIVITY | INSTA_DM | CHECK_CLAUDE | GITHUB_CREATE_REPO | GITHUB_PUSH | GITHUB_WORKFLOW",
          "params": {
            "hour": 7,
            "minute": 30,
            "seconds": 60,
            "label": "Workout",
            "recipient": "example@gmail.com",
            "subject": "Hello",
            "body": "Email text",
            "app_name": "YouTube",
            "connectivity_target": "wifi",
            "insta_username": "friend_handle",
            "insta_message": "Hey there!",
            "github_owner": "username",
            "github_repo": "repo-name",
            "github_path": "src/main.py",
            "github_commit": "Feat: Add full module implementation",
            "github_branch": "main",
            "workflow_file": "ci.yml"
          },
          "generated_code": "Full, complete, production-ready long code if user asked to write code or push code to GitHub. Never truncate code."
        }
        CRITICAL RULE: You are strictly forbidden from deleting any GitHub repository, branch, or file.
    """.trimIndent()

    suspend fun processCommand(userCommand: String): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            return@withContext "Sir, please enter your free Gemini API Key in the top configuration panel."
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userCommand)))
            }))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: "{}"
            if (!response.isSuccessful) {
                return@withContext "Gemini API Error (${response.code}): $bodyString"
            }

            val rawText = JSONObject(bodyString)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val parsed = JSONObject(rawText)
            val reply = parsed.optString("spoken_reply", "Task processed, Sir.")
            val action = parsed.optString("action", "NONE")
            val params = parsed.optJSONObject("params") ?: JSONObject()
            val code = parsed.optString("generated_code", "")

            val actionOutcome = executeMappedAction(action, params, code)

            buildString {
                append(reply)
                if (actionOutcome.isNotBlank()) {
                    append("\n\n[EXECUTION LOG]:\n").append(actionOutcome)
                }
                if (code.isNotBlank()) {
                    append("\n\n[GENERATED CODE]:\n```\n").append(code).append("\n```")
                }
            }
        }
    }

    private suspend fun executeMappedAction(action: String, params: JSONObject, code: String): String {
        return try {
            when (action) {
                "SET_ALARM" -> systemExecutor.setAlarm(
                    params.optInt("hour", 8),
                    params.optInt("minute", 0),
                    params.optString("label", "Jarvis Alarm")
                )
                "SET_TIMER" -> systemExecutor.setTimer(
                    params.optInt("seconds", 60),
                    params.optString("label", "Jarvis Timer")
                )
                "SEND_GMAIL" -> systemExecutor.sendGmail(
                    params.optString("recipient", ""),
                    params.optString("subject", "From Jarvis"),
                    params.optString("body", "")
                )
                "OPEN_APP" -> systemExecutor.openApp(params.optString("app_name", ""))
                "TOGGLE_CONNECTIVITY" -> systemExecutor.toggleConnectivity(
                    params.optString("connectivity_target", "wifi")
                )
                "INSTA_DM" -> systemExecutor.sendInstagramDirectMessage(
                    params.optString("insta_username", ""),
                    params.optString("insta_message", "")
                )
                "CHECK_CLAUDE" -> systemExecutor.checkClaudeUsageLimit()
                "GITHUB_CREATE_REPO" -> githubManager.createRepo(
                    repoName = params.optString("github_repo", "jarvis-repo"),
                    description = params.optString("label", "Created by Jarvis Mobile Agent")
                )
                "GITHUB_PUSH" -> githubManager.pushFileToRepo(
                    owner = params.optString("github_owner", ""),
                    repo = params.optString("github_repo", ""),
                    filePath = params.optString("github_path", "output.txt"),
                    fileContent = code.ifBlank { params.optString("body", "# Created by Jarvis") },
                    commitMessage = params.optString("github_commit", "Pushed via Jarvis Mobile Agent"),
                    branch = params.optString("github_branch", "main")
                )
                "GITHUB_WORKFLOW" -> githubManager.triggerWorkflow(
                    owner = params.optString("github_owner", ""),
                    repo = params.optString("github_repo", ""),
                    workflowIdOrFileName = params.optString("workflow_file", "main.yml"),
                    ref = params.optString("github_branch", "main")
                )
                else -> ""
            }
        } catch (e: Exception) {
            "Execution Error: ${e.message}"
        }
    }
}
