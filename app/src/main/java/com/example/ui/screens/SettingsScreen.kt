package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.IptvViewModel

@Composable
fun SettingsScreen(
    viewModel: IptvViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val firebaseConnected by viewModel.firebaseConnected.collectAsStateWithLifecycle()
    val appUpdate by viewModel.appUpdate.collectAsStateWithLifecycle()

    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSchemaDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Logo Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_splash_logo),
                    contentDescription = "Pronix Tv Logo",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Pronix Tv",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "v1.0.0 Stable (Native Android Media3)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // Section 1: System Status
        Text(
            text = "System Integration",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Connection item
                ListItem(
                    headlineContent = { Text("Firebase RTDB Connection") },
                    supportingContent = {
                        Text(
                            if (firebaseConnected) "Connected (Live sync active)"
                            else "Disconnected (Demo Mode active)"
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = if (firebaseConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            contentDescription = null,
                            tint = if (firebaseConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                )

                Divider()

                // Fallback Force Update Alert
                val update = appUpdate
                if (update != null) {
                    ListItem(
                        headlineContent = { Text("Available Update: ${update.latestVersionName}") },
                        supportingContent = { Text(update.releaseNotes) },
                        leadingContent = {
                            Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Button(onClick = {
                                Toast.makeText(context, "Redirecting to update...", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Update")
                            }
                        }
                    )
                } else {
                    ListItem(
                        headlineContent = { Text("App Version Status") },
                        supportingContent = { Text("Your application is up to date.") },
                        leadingContent = {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                }
            }
        }

        // Section 2: General Options
        Text(
            text = "Controls & Caches",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Clear History
                ListItem(
                    headlineContent = { Text("Clear Watching History") },
                    supportingContent = { Text("Deletes all Continue Watching caches") },
                    leadingContent = {
                        Icon(Icons.Filled.History, contentDescription = null)
                    },
                    trailingContent = {
                        TextButton(
                            onClick = {
                                viewModel.clearHistory()
                                Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Divider()

                // Firebase Schema Structure
                ListItem(
                    headlineContent = { Text("Firebase Schema Integration") },
                    supportingContent = { Text("View JSON layout for Claude Admin panel") },
                    leadingContent = {
                        Icon(Icons.Filled.Code, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showSchemaDialog = true }
                )
            }
        }

        // Section 3: Legal & Information
        Text(
            text = "Information & Support",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(8.dp)) {
                // About App
                ListItem(
                    headlineContent = { Text("About Pronix Tv") },
                    supportingContent = { Text("Development particulars and player capabilities") },
                    leadingContent = {
                        Icon(Icons.Filled.Info, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showAboutDialog = true }
                )

                Divider()

                // Privacy Policy
                ListItem(
                    headlineContent = { Text("Privacy Policy") },
                    supportingContent = { Text("Streaming policies, telemetry, and terms of service") },
                    leadingContent = {
                        Icon(Icons.Filled.Security, contentDescription = null)
                    },
                    modifier = Modifier.clickable { showPrivacyDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(60.dp))
    }

    // ABOUT DIALOG
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Pronix Tv") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pronix Tv is a production-ready, ultra-optimized Native IPTV Player crafted with Jetpack Compose & AndroidX Media3 ExoPlayer.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Supported Streaming Protocols: HLS (.m3u8), MPEG-TS (.ts), MP4, MKV, and dynamic M3U playlists.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Key Features incorporated: Adaptive bitrate streaming (up to 4K), automatic reconnection loops, custom buffer optimizations, hardware decoding, and full Picture-in-Picture capability.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // PRIVACY POLICY DIALOG
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Last updated: July 2026",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your privacy is our priority. Pronix Tv works primarily as a client-side player. We do NOT host, collect, or store any streaming links or personal credentials. Any dynamic M3U content loaded is handled directly on your device.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Firebase Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Firebase Analytics and Crashlytics are fully incorporated solely to monitor application performance and record playback errors to improve stream connectivity logic.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Local Cache",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Your bookmark history and continue watching states are persisted locally in a secure, on-device Room SQLite database. You can flush this cache instantly at any time from this Settings page.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("I Understand")
                }
            }
        )
    }

    // FIREBASE SCHEMA DIALOG FOR THE CLAUDE ADMIN PANEL
    if (showSchemaDialog) {
        AlertDialog(
            onDismissRequest = { showSchemaDialog = false },
            title = { Text("Firebase Database Schema") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Use this clean JSON structure in your Claude-built Admin Panel. Your admin panel should write to these exact Firebase Realtime Database nodes:",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Surface(
                        color = Color.Black,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = """
{
  "categories": {
    "cat_movies": {
      "id": "cat_movies",
      "name": "Movies",
      "iconUrl": "https://..."
    }
  },
  "channels": {
    "ch_id": {
      "id": "ch_id",
      "name": "Live TV",
      "streamUrl": "https://...",
      "logoUrl": "https://...",
      "category": "cat_movies",
      "isFeatured": true,
      "description": "Premium Cinema stream"
    }
  },
  "banners": {
    "b1": {
      "id": "b1",
      "title": "Movie Tonight",
      "imageUrl": "https://...",
      "channelId": "ch_id"
    }
  },
  "notices": {
    "n1": {
      "id": "n1",
      "title": "Welcome Alert",
      "message": "Dynamic announcement text",
      "createdAt": 1782923915000,
      "type": "info"
    }
  },
  "appUpdate": {
    "latestVersionCode": 1,
    "latestVersionName": "1.0.0",
    "isForceUpdate": false,
    "updateUrl": "https://...",
    "releaseNotes": "Bugfixes and performance updates"
  }
}
                            """.trimIndent(),
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSchemaDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}
