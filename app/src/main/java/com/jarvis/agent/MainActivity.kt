package com.jarvis.agent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        val requestMicPermission = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {}
        requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)

        val prefs = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
        val systemExecutor = SystemActionExecutor(this)
        val githubManager = GitHubManager { prefs.getString("github_token", "") ?: "" }
        val geminiBrain = GeminiBrain(
            apiKeyProvider = { prefs.getString("gemini_key", "") ?: "" },
            systemExecutor = systemExecutor,
            githubManager = githubManager
        )

        setContent {
            JarvisHudScreen(
                prefs = prefs,
                geminiBrain = geminiBrain,
                onSpeak = { text ->
                    val cleanSpeech = text.substringBefore("[EXECUTION LOG]").substringBefore("[GENERATED CODE]")
                    tts.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_UTTERANCE")
                },
                onOpenAccessibilitySettings = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.setPitch(0.9f)
            tts.setSpeechRate(1.05f)
        }
    }

    override fun onDestroy() {
        tts.shutdown()
        speechRecognizer?.destroy()
        super.onDestroy()
    }
}

@Composable
fun JarvisHudScreen(
    prefs: android.content.SharedPreferences,
    geminiBrain: GeminiBrain,
    onSpeak: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var commandInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var geminiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var githubToken by remember { mutableStateOf(prefs.getString("github_token", "") ?: "") }

    val logs = remember {
        mutableStateListOf(
            "J.A.R.V.I.S. OS v4.2 ONLINE.",
            "GitHub Delete Protection: ENGAGED.",
            "Try: 'Set alarm for 6:30 AM', 'Open Instagram and DM @john Hello', 'Write a full Python FastAPI server and push to my-username/my-repo at app/main.py', or 'Check my Claude limit'."
        )
    }

    val NeonCyan = Color(0xFF00F0FF)
    val DeepSpace = Color(0xFF050B14)
    val PanelDark = Color(0xFF0D1B2A)

    val infiniteTransition = rememberInfiniteTransition(label = "reactor")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "ring_rot"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DeepSpace, Color(0xFF020408))))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "J.A.R.V.I.S. // CORE",
                color = NeonCyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = NeonCyan)
            }
        }

        // Settings Drawer for Free Gemini Key, GitHub PAT, & Accessibility
        if (showSettings) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelDark, RoundedCornerShape(12.dp))
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = {
                        geminiKey = it
                        prefs.edit().putString("gemini_key", it).apply()
                    },
                    label = { Text("Gemini API Key (Free at AI Studio)", color = NeonCyan) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = githubToken,
                    onValueChange = {
                        githubToken = it
                        prefs.edit().putString("github_token", it).apply()
                    },
                    label = { Text("GitHub Personal Access Token (repo & workflow)", color = NeonCyan) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onOpenAccessibilitySettings,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.2f))
                ) {
                    Text("Enable Accessibility Service (For Insta/Claude/Wi-Fi)", color = NeonCyan)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Animated Arc Reactor Core
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .padding(vertical = 8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
                drawCircle(
                    color = NeonCyan.copy(alpha = 0.35f),
                    style = Stroke(width = 6f)
                )
                drawArc(
                    color = NeonCyan,
                    startAngle = 0f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = 12f)
                )
                drawArc(
                    color = NeonCyan,
                    startAngle = 180f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(width = 12f)
                )
            }
            Text(
                text = if (isProcessing) "THINKING" else "ONLINE",
                color = NeonCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        // Terminal Output Log
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(PanelDark.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            items(logs) { entry ->
                Text(
                    text = "> $entry",
                    color = Color(0xFFE0FBFC),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                Divider(color = NeonCyan.copy(alpha = 0.12f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Command Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = { Text("Command Jarvis (Code, GitHub, System, Apps)...", color = Color.Gray) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = NeonCyan.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (commandInput.isNotBlank() && !isProcessing) {
                        val userCmd = commandInput
                        commandInput = ""
                        logs.add("YOU: $userCmd")
                        isProcessing = true
                        scope.launch {
                            val response = geminiBrain.processCommand(userCmd)
                            logs.add("JARVIS: $response")
                            onSpeak(response)
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(NeonCyan.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, NeonCyan, CircleShape)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Execute", tint = NeonCyan)
            }
        }
    }
}
