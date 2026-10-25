package com.jarvis.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings

class SystemActionExecutor(private val context: Context) {

    fun setAlarm(hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Alarm set for %02d:%02d (%s).".format(hour, minute, label)
    }

    fun setTimer(seconds: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Timer started for $seconds seconds ($label)."
    }

    fun sendGmail(recipient: String, subject: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return "Opened Gmail composer for $recipient with subject '$subject'."
    }

    fun openApp(appNameOrQuery: String): String {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        val match = packages.firstOrNull {
            val label = pm.getApplicationLabel(it).toString()
            label.contains(appNameOrQuery, ignoreCase = true) ||
                it.packageName.contains(appNameOrQuery, ignoreCase = true)
        }

        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                "Launched ${pm.getApplicationLabel(match)}."
            } else {
                "Found package ${match.packageName}, but it has no launchable screen."
            }
        } else {
            "Could not find an installed app matching '$appNameOrQuery'."
        }
    }

    fun toggleConnectivity(target: String): String {
        val a11y = JarvisAccessibilityService.instance
        if (a11y != null) {
            val tileKeyword = if (target.contains("wifi", ignoreCase = true)) "Internet" else "Mobile data"
            val success = a11y.toggleQuickSettingTile(tileKeyword)
            if (success) return "Toggled $target via Jarvis Accessibility Service."
        }

        val panelAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Settings.Panel.ACTION_INTERNET_CONNECTIVITY
        } else {
            Settings.ACTION_WIFI_SETTINGS
        }
        val intent = Intent(panelAction).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
        return "Opened connectivity panel for $target."
    }

    fun sendInstagramDirectMessage(username: String, message: String): String {
        val uri = Uri.parse("http://instagram.com/_u/$username")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.instagram.android")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(intent)
            val a11y = JarvisAccessibilityService.instance
            if (a11y != null) {
                Thread.sleep(2500)
                a11y.findAndClickNodeByText(a11y.rootInActiveWindow, "Message")
                Thread.sleep(1500)
                a11y.typeAndSendMessage(message)
            } else {
                "Opened @$username on Instagram. Enable Jarvis Accessibility Service in Settings for automatic message typing."
            }
        } catch (e: Exception) {
            "Instagram app not installed or deep-link failed: ${e.message}"
        }
    }

    fun checkClaudeUsageLimit(): String {
        val a11y = JarvisAccessibilityService.instance
        openApp("Claude")
        Thread.sleep(2000)
        return if (a11y != null) {
            val screenText = a11y.scrapeCurrentScreenText()
            "Screen readout from Claude:\n$screenText"
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/settings")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Opened Claude settings. Enable Jarvis Accessibility Service to automatically read usage limits."
        }
    }
}
