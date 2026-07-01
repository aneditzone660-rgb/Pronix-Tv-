package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.IptvDao
import com.example.data.local.FavoriteChannelEntity
import com.example.data.local.RecentChannelEntity
import com.example.data.model.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IptvRepository(
    private val context: Context,
    private val iptvDao: IptvDao
) {
    private val tag = "IptvRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    // Exposed Flows from Firebase
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _banners = MutableStateFlow<List<Banner>>(emptyList())
    val banners: StateFlow<List<Banner>> = _banners.asStateFlow()

    private val _notices = MutableStateFlow<List<Notice>>(emptyList())
    val notices: StateFlow<List<Notice>> = _notices.asStateFlow()

    private val _appUpdate = MutableStateFlow<AppUpdate?>(null)
    val appUpdate: StateFlow<AppUpdate?> = _appUpdate.asStateFlow()

    private val _firebaseConnected = MutableStateFlow(false)
    val firebaseConnected: StateFlow<Boolean> = _firebaseConnected.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Local DB Flows
    val favorites: Flow<List<FavoriteChannelEntity>> = iptvDao.getAllFavorites()
    val recents: Flow<List<RecentChannelEntity>> = iptvDao.getAllRecents()

    init {
        initializeFirebaseSafely()
        setupFirebaseAuth()
        loadData()
    }

    private fun initializeFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:1234567890:android:abcdef")
                    .setApiKey("placeholder_api_key")
                    .setDatabaseUrl("https://placeholder-database.firebaseio.com")
                    .setProjectId("placeholder-project-id")
                    .build()
                FirebaseApp.initializeApp(context, options)
                Log.d(tag, "Initialized FirebaseApp with fallback placeholder options")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize FirebaseApp with fallback options", e)
        }
    }

    private fun setupFirebaseAuth() {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) {
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        Log.d(tag, "Firebase anonymous auth successful")
                    }
                    .addOnFailureListener { e ->
                        Log.w(tag, "Firebase anonymous auth failed", e)
                    }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up Firebase Auth (missing google-services.json?)", e)
        }
    }

    fun loadData() {
        _isLoading.value = true
        scope.launch {
            var firebaseDataLoaded = false
            try {
                val database = FirebaseDatabase.getInstance()
                val dbRef = database.reference

                val connectedRef = database.getReference(".info/connected")
                connectedRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val connected = snapshot.getValue(Boolean::class.java) ?: false
                        _firebaseConnected.value = connected
                        Log.d(tag, "Firebase connection state: $connected")
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.w(tag, "Listener cancelled", error.toException())
                    }
                })

                dbRef.child("categories").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<Category>()
                        for (child in snapshot.children) {
                            child.getValue(Category::class.java)?.let { list.add(it) }
                        }
                        if (list.isNotEmpty()) {
                            _categories.value = list
                            firebaseDataLoaded = true
                        }
                        checkLoadingStatus()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(tag, "Categories fetch error", error.toException())
                        checkLoadingStatus()
                    }
                })

                dbRef.child("channels").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<Channel>()
                        for (child in snapshot.children) {
                            child.getValue(Channel::class.java)?.let { list.add(it) }
                        }
                        if (list.isNotEmpty()) {
                            _channels.value = list
                            firebaseDataLoaded = true
                        }
                        checkLoadingStatus()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(tag, "Channels fetch error", error.toException())
                        checkLoadingStatus()
                    }
                })

                dbRef.child("banners").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<Banner>()
                        for (child in snapshot.children) {
                            child.getValue(Banner::class.java)?.let { list.add(it) }
                        }
                        if (list.isNotEmpty()) {
                            _banners.value = list
                        }
                        checkLoadingStatus()
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(tag, "Banners fetch error", error.toException())
                        checkLoadingStatus()
                    }
                })

                dbRef.child("notices").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val list = mutableListOf<Notice>()
                        for (child in snapshot.children) {
                            child.getValue(Notice::class.java)?.let { list.add(it) }
                        }
                        _notices.value = list
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(tag, "Notices fetch error", error.toException())
                    }
                })

                dbRef.child("appUpdate").addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        _appUpdate.value = snapshot.getValue(AppUpdate::class.java)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(tag, "AppUpdate fetch error", error.toException())
                    }
                })

            } catch (e: Exception) {
                Log.e(tag, "Firebase database error (missing config / offline)", e)
            }

            kotlinx.coroutines.delay(2000)
            if (!firebaseDataLoaded && _channels.value.isEmpty()) {
                Log.i(tag, "Populating premium Demo Data fallback...")
                loadDemoData()
            }
            _isLoading.value = false
        }
    }

    private fun checkLoadingStatus() {
        if (_channels.value.isNotEmpty() || _categories.value.isNotEmpty()) {
            _isLoading.value = false
        }
    }

    private fun loadDemoData() {
        val demoCategories = listOf(
            Category("cat_movies", "Movies & Entertainment", ""),
            Category("cat_news", "World News Live", ""),
            Category("cat_sports", "Sports Highlights", ""),
            Category("cat_science", "Science & Nature", ""),
            Category("cat_music", "Lo-Fi & Music", "")
        )

        val demoChannels = listOf(
            Channel(
                id = "ch_nasatv",
                name = "NASA Live HD",
                streamUrl = "https://ntv1.nasatv.live/live/nasatv1/playlist.m3u8",
                logoUrl = "https://www.nasa.gov/wp-content/uploads/2015/06/nasa_logo_html.png",
                category = "cat_science",
                isFeatured = true,
                description = "NASA Television provides real-time coverage of agency activities, live space missions, educational programming, and stunning space vistas."
            ),
            Channel(
                id = "ch_bbb",
                name = "Big Buck Bunny Movie Channel",
                streamUrl = "https://test-streams.mux.dev/x36xhg/playlist.m3u8",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_Buck_Bunny_Main_Poster.jpg",
                category = "cat_movies",
                isFeatured = true,
                description = "A classic high-definition test stream featuring the beloved giant rabbit Big Buck Bunny. Perfect for showcasing network capabilities and smooth player transitions."
            ),
            Channel(
                id = "ch_sintel",
                name = "Sintel HD (Open Source Cinema)",
                streamUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                logoUrl = "https://durian.blender.org/wp-content/uploads/2010/05/sintel_poster_v2_small.jpg",
                category = "cat_movies",
                isFeatured = false,
                description = "An HLS film stream of Sintel, an independent open-source cinema project. Showcases stunning visuals, rich contrast, and multiple audio-subtitle layers."
            ),
            Channel(
                id = "ch_tears",
                name = "Tears of Steel (Sci-Fi Direct)",
                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                logoUrl = "https://mango.blender.org/wp-content/uploads/2012/09/cropped-header21.jpg",
                category = "cat_movies",
                isFeatured = false,
                description = "Set in an alternate future, Tears of Steel blends live action footage with stunning CGI visual effects in HLS format."
            ),
            Channel(
                id = "ch_jazeera",
                name = "Al Jazeera English Live",
                streamUrl = "https://live-aljazeera.akamaized.net/mxtvgo/aljazeeraeng/playlist.m3u8",
                logoUrl = "https://www.aljazeera.com/wp-content/themes/aje-theme/assets/img/logo-aje.svg",
                category = "cat_news",
                isFeatured = true,
                description = "Live 24-hour global news network from Al Jazeera, providing objective reporting, deep analyses, and direct on-the-scene journalism from across the globe."
            ),
            Channel(
                id = "ch_euronews",
                name = "Euronews English Live Feed",
                streamUrl = "https://euronews-eng-p3-m3u8.hexaglobe.net/playlist.m3u8",
                logoUrl = "https://www.euronews.com/favicon.ico",
                category = "cat_news",
                isFeatured = false,
                description = "Euronews broadcasts international news with a European perspective. Live feed streaming the latest news, economy, science, and cultural updates."
            )
        )

        val demoBanners = listOf(
            Banner(
                id = "b1",
                title = "NASA Space Station Walk Live",
                imageUrl = "https://www.nasa.gov/wp-content/uploads/2021/11/iss066e081373.jpg",
                actionUrl = "",
                channelId = "ch_nasatv"
            ),
            Banner(
                id = "b2",
                title = "Big Buck Bunny Cinema Showcase",
                imageUrl = "https://peach.blender.org/wp-content/uploads/title_an_v2.jpg",
                actionUrl = "",
                channelId = "ch_bbb"
            )
        )

        val demoNotices = listOf(
            Notice(
                id = "n1",
                title = "Welcome to Pronix Tv!",
                message = "Explore dynamic HLS live streaming. Note: Configure your Firebase Realtime Database to load your custom channel play lists dynamically.",
                createdAt = System.currentTimeMillis(),
                type = "info"
            )
        )

        val demoAppUpdate = AppUpdate(
            latestVersionCode = 1,
            latestVersionName = "1.0.0",
            isForceUpdate = false,
            updateUrl = "",
            releaseNotes = "Initial dynamic release. High stability player, Room DB local state caching, and full landscape orientation."
        )

        _categories.value = demoCategories
        _channels.value = demoChannels
        _banners.value = demoBanners
        _notices.value = demoNotices
        _appUpdate.value = demoAppUpdate
    }

    // Room Favorites
    suspend fun addFavorite(channel: Channel) {
        withContext(Dispatchers.IO) {
            iptvDao.insertFavorite(
                FavoriteChannelEntity(
                    id = channel.id,
                    name = channel.name,
                    streamUrl = channel.streamUrl,
                    logoUrl = channel.logoUrl,
                    category = channel.category,
                    isFeatured = channel.isFeatured,
                    description = channel.description,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun removeFavorite(channelId: String) {
        withContext(Dispatchers.IO) {
            iptvDao.deleteFavoriteById(channelId)
        }
    }

    fun isFavorite(channelId: String): Flow<Boolean> {
        return iptvDao.isFavorite(channelId)
    }

    // Room History / Continue Watching
    suspend fun recordWatchHistory(channel: Channel, playbackPosition: Long = 0L) {
        withContext(Dispatchers.IO) {
            iptvDao.insertRecent(
                RecentChannelEntity(
                    id = channel.id,
                    name = channel.name,
                    streamUrl = channel.streamUrl,
                    logoUrl = channel.logoUrl,
                    category = channel.category,
                    isFeatured = channel.isFeatured,
                    description = channel.description,
                    watchedAt = System.currentTimeMillis(),
                    playbackPosition = playbackPosition
                )
            )
        }
    }

    suspend fun removeRecent(channelId: String) {
        withContext(Dispatchers.IO) {
            iptvDao.deleteRecentById(channelId)
        }
    }

    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            iptvDao.clearHistory()
        }
    }
}
