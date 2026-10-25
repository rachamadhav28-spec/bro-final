# J.A.R.V.I.S. — Autonomous Android Mobile Agent

A zero-cost, Cyberpunk-styled Android AI Agent powered by **Gemini 2.5 Flash**, **Android AccessibilityService**, and the **GitHub REST API**.

## Features Included
1. **Gemini System Tasks:** Alarms, Timers, Gmail composition, App Launching, and Wi-Fi / Mobile Data Quick Setting toggles.
2. **Long Code Generation:** Generates complete, untruncated production code using Gemini 2.5 Flash.
3. **GitHub Integration (Strict No-Delete Guardrail):** Creates repositories, pushes code files, updates `.github/workflows`, and triggers GitHub Actions while hard-blocking any `HTTP DELETE` call in `GitHubManager.kt`.
4. **In-App UI Automation:** Sends Instagram Direct Messages and scrapes Claude usage limits via `JarvisAccessibilityService.kt`.
5. **Voice & Neon HUD:** Built with Jetpack Compose featuring a rotating Arc Reactor HUD, native `SpeechRecognizer`, and `TextToSpeech`.

## How to Build on GitHub for Free (No Android Studio Needed)
1. Create a new repository on GitHub.
2. Extract this ZIP and upload all files (including the `.github` folder) to your repository.
3. Go to the **Actions** tab in your GitHub repository — the `Build Jarvis Android APK` workflow will automatically compile your app and give you a downloadable `Jarvis-Mobile-Agent-APK` artifact!
