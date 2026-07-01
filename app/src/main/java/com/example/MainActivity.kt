package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.data.local.IptvDatabase
import com.example.data.repository.IptvRepository
import com.example.player.IptvPlayerComposable
import com.example.ui.IptvViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // PiP Mode detection state
    private val isPipMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core dynamic singletons
        val database = IptvDatabase.getDatabase(applicationContext)
        val repository = IptvRepository(applicationContext, database.iptvDao())

        setContent {
            MyApplicationTheme {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        onSplashFinished = { showSplash = false }
                    )
                } else {
                    val viewModel: IptvViewModel = viewModel(
                        factory = IptvViewModel.Factory(repository)
                    )

                    val activeChannel by viewModel.activeChannel.collectAsStateWithLifecycle()
                    val pipState by isPipMode

                    val currentChannel = activeChannel
                    if (currentChannel != null) {
                        // Play stream full screen
                        IptvPlayerComposable(
                            channel = currentChannel,
                            onBack = { _ ->
                                viewModel.setActiveChannel(null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Render main application shell (only when NOT in PiP stream mode)
                        MainDashboardShell(
                            viewModel = viewModel,
                            onChannelSelect = { channel ->
                                viewModel.setActiveChannel(channel)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode.value = isInPictureInPictureMode
    }
}

enum class NavigationTab(val title: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Filled.Home, "tab_home"),
    BROWSE("Browse", Icons.Filled.LiveTv, "tab_browse"),
    FAVORITES("Favorites", Icons.Filled.Favorite, "tab_favorites"),
    SETTINGS("Settings", Icons.Filled.Settings, "tab_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardShell(
    viewModel: IptvViewModel,
    onChannelSelect: (com.example.data.model.Channel) -> Unit
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }
    val firebaseConnected by viewModel.firebaseConnected.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Tv,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = buildAnnotatedString {
                                append("Pronix ")
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("Tv")
                                }
                            },
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    // Visual status chip representing live Firebase RTDB connection status
                    Surface(
                        color = if (firebaseConnected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (firebaseConnected) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (firebaseConnected) "Cloud Live" else "Local Demo",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (firebaseConnected)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column {
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), thickness = 1.dp)
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    for (tab in NavigationTab.entries) {
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                NavigationTab.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onChannelSelect = onChannelSelect
                )
                NavigationTab.BROWSE -> BrowseScreen(
                    viewModel = viewModel,
                    onChannelSelect = onChannelSelect
                )
                NavigationTab.FAVORITES -> FavoritesScreen(
                    viewModel = viewModel,
                    onChannelSelect = onChannelSelect
                )
                NavigationTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}
