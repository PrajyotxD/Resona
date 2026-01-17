/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Main entry point for the Innertube Java Bridge providing static methods
 * for Java applications to access YouTube Music functionality without Kotlin dependencies
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge

import com.metrolist.innertube.YouTube
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import music.resona.online.bridge.exceptions.ExceptionConverter
import music.resona.online.bridge.models.HomePageResult
import music.resona.online.bridge.models.ModelConverter
import music.resona.online.bridge.models.SearchResult
import music.resona.online.bridge.models.PlaybackDataResult
import music.resona.online.bridge.callbacks.*
import music.resona.online.bridge.callbacks.BooleanCallback
import music.resona.online.bridge.callbacks.PlaybackDataCallback
import music.resona.online.bridge.utils.StreamingUtils
import java.util.concurrent.Executors
import java.net.InetSocketAddress

/**
 * Main entry point for the Innertube Java Bridge.
 * Provides static methods for Java applications to access Innertube functionality
 * without requiring Kotlin coroutines or direct Kotlin dependencies.
 */
object InnertubeBridge {
    
    private val backgroundExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "InnertubeBridge-${System.currentTimeMillis()}").apply {
            isDaemon = true
        }
    }
    
    /**
     * Initialize the bridge with default configuration.
     * This method should be called before using any other bridge methods.
     */
    @JvmStatic
    fun initialize() {
        // Bridge initialization - YouTube is an object, no instance needed
        // Set default locale if not already set
        if (YouTube.locale.gl.isEmpty()) {
            YouTube.locale = com.metrolist.innertube.models.YouTubeLocale(
                gl = "US",      // country code
                hl = "en-US"    // language tag
            )
        }
        // Disable login requirement for browse requests (use visitor data for anonymous access)
        YouTube.useLoginForBrowse = false
    }
    
    /**
     * Initialize the bridge with custom locale configuration.
     * This method should be called before using any other bridge methods.
     * 
     * @param country ISO 3166-1 alpha-2 country code (e.g., "US", "GB", "IN") 
     * @param languageTag BCP 47 language tag (e.g., "en-US", "en-GB", "hi-IN")
     */
    @JvmStatic
    fun initialize(country: String, languageTag: String) {
        YouTube.locale = com.metrolist.innertube.models.YouTubeLocale(
            gl = country,      // geolocation country code
            hl = languageTag   // host language tag
        )
        // Disable login requirement for browse requests (use visitor data for anonymous access)
        YouTube.useLoginForBrowse = false
    }
    
    /**
     * Get the bridge version for compatibility checking.
     */
    @JvmStatic
    fun getVersion(): String {
        return "1.0.0"
    }
    
    // ========== BRIDGE CONFIGURATION METHODS ==========
    
    /**
     * Sets the locale for YouTube Music requests.
     * This affects the language and region of content returned.
     */
    @JvmStatic
    fun setLocale(language: String, country: String) {
        YouTube.locale = com.metrolist.innertube.models.YouTubeLocale(language, country)
    }
    
    /**
     * Gets the current locale configuration.
     * Returns a string in format "language-country" (e.g., "en-US").
     */
    @JvmStatic
    fun getLocale(): String {
        return "${YouTube.locale.hl}-${YouTube.locale.gl}"
    }
    
    /**
     * Sets proxy configuration for network requests.
     * Use this method to configure proxy settings for the bridge.
     */
    @JvmStatic
    fun setProxy(proxyHost: String, proxyPort: Int) {
        YouTube.proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(proxyHost, proxyPort))
    }
    
    /**
     * Sets proxy configuration with authentication.
     * Use this method to configure proxy settings with authentication for the bridge.
     */
    @JvmStatic
    fun setProxyWithAuth(proxyHost: String, proxyPort: Int, proxyAuth: String) {
        YouTube.proxy = java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(proxyHost, proxyPort))
        YouTube.proxyAuth = proxyAuth
    }
    
    /**
     * Clears proxy configuration.
     * Removes any proxy settings and uses direct connection.
     */
    @JvmStatic
    fun clearProxy() {
        YouTube.proxy = null
        YouTube.proxyAuth = null
    }
    
    /**
     * Sets whether to use login for browse operations.
     * When enabled, browse operations will use authenticated requests.
     */
    @JvmStatic
    fun setUseLoginForBrowse(useLogin: Boolean) {
        YouTube.useLoginForBrowse = useLogin
    }
    
    /**
     * Gets whether login is used for browse operations.
     */
    @JvmStatic
    fun getUseLoginForBrowse(): Boolean {
        return YouTube.useLoginForBrowse
    }
    
    /**
     * Gets the current bridge configuration as a formatted string.
     * Useful for debugging and logging configuration state.
     */
    @JvmStatic
    fun getConfiguration(): String {
        return buildString {
            appendLine("InnertubeBridge Configuration:")
            appendLine("  Version: ${getVersion()}")
            appendLine("  Locale: ${getLocale()}")
            appendLine("  Proxy: ${if (YouTube.proxy != null) "Configured" else "None"}")
            appendLine("  Authentication: ${if (YouTube.cookie != null) "Authenticated" else "Anonymous"}")
            appendLine("  Use Login for Browse: ${YouTube.useLoginForBrowse}")
            appendLine("  Visitor Data: ${if (YouTube.visitorData != null) "Set" else "None"}")
        }
    }
    
    // ========== ADVANCED BRIDGE FEATURES ==========
    
    /**
     * Gets search suggestions synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getSearchSuggestionsSync(query: String): Array<String> {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.searchSuggestions(query).getOrThrow()
                result.queries.toTypedArray()
            }
        }
    }
    
    /**
     * Gets search suggestions asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getSearchSuggestionsAsync(query: String, callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.searchSuggestions(query).getOrThrow()
                val suggestions = result.queries.joinToString("\n")
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(suggestions)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Performs search with continuation token synchronously.
     * Used for pagination of search results.
     */
    @JvmStatic
    fun searchWithContinuationSync(continuation: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.searchContinuation(continuation).getOrThrow()
                ModelConverter.convertSearchResults(result.items, result.continuation)
            }
        }
    }
    
    /**
     * Performs search with continuation token asynchronously.
     * Used for pagination of search results.
     */
    @JvmStatic
    fun searchWithContinuationAsync(continuation: String, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.searchContinuation(continuation).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items, result.continuation)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Performs advanced search with custom filter synchronously.
     * Supports all YouTube Music search filters.
     */
    @JvmStatic
    fun searchWithFilterSync(query: String, filter: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val searchFilter = when (filter.lowercase()) {
                    "song", "songs" -> YouTube.SearchFilter.FILTER_SONG
                    "album", "albums" -> YouTube.SearchFilter.FILTER_ALBUM
                    "artist", "artists" -> YouTube.SearchFilter.FILTER_ARTIST
                    "playlist", "playlists" -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                    "video", "videos" -> YouTube.SearchFilter.FILTER_VIDEO
                    "community_playlist", "community_playlists" -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                    else -> YouTube.SearchFilter.FILTER_SONG // Default to songs
                }
                val result = YouTube.search(query, searchFilter).getOrThrow()
                ModelConverter.convertSearchResults(result.items, result.continuation)
            }
        }
    }
    
    /**
     * Performs advanced search with custom filter asynchronously.
     * Supports all YouTube Music search filters.
     */
    @JvmStatic
    fun searchWithFilterAsync(query: String, filter: String, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val searchFilter = when (filter.lowercase()) {
                    "song", "songs" -> YouTube.SearchFilter.FILTER_SONG
                    "album", "albums" -> YouTube.SearchFilter.FILTER_ALBUM
                    "artist", "artists" -> YouTube.SearchFilter.FILTER_ARTIST
                    "playlist", "playlists" -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                    "video", "videos" -> YouTube.SearchFilter.FILTER_VIDEO
                    "community_playlist", "community_playlists" -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                    else -> YouTube.SearchFilter.FILTER_SONG // Default to songs
                }
                val result = YouTube.search(query, searchFilter).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items, result.continuation)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets available search filters.
     * Returns an array of supported filter names.
     */
    @JvmStatic
    fun getAvailableSearchFilters(): Array<String> {
        return arrayOf("song", "album", "artist", "playlist", "video", "community_playlist")
    }
    
    /**
     * Browses content with custom parameters synchronously.
     * Used for browsing specific content categories.
     */
    @JvmStatic
    fun browseWithParamsSync(browseId: String, params: String?): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.browse(browseId, params).getOrThrow()
                // Convert browse result to HomePageResult format
                HomePageResult(
                    sections = result.items.map { section ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = section.title ?: "Browse Results",
                            items = ModelConverter.convertYTItems(section.items)
                        )
                    }
                )
            }
        }
    }
    
    /**
     * Browses content with custom parameters asynchronously.
     * Used for browsing specific content categories.
     */
    @JvmStatic
    fun browseWithParamsAsync(browseId: String, params: String?, callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.browse(browseId, params).getOrThrow()
                val bridgeResult = HomePageResult(
                    sections = result.items.map { section ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = section.title ?: "Browse Results",
                            items = ModelConverter.convertYTItems(section.items)
                        )
                    }
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Uploads custom thumbnail for playlist synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun uploadCustomThumbnailSync(playlistId: String, imageData: ByteArray): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.uploadCustomThumbnailLink(playlistId, imageData).getOrThrow()
                "Upload successful"
            }
        }
    }
    
    /**
     * Uploads custom thumbnail for playlist asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun uploadCustomThumbnailAsync(playlistId: String, imageData: ByteArray, callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YouTube.uploadCustomThumbnailLink(playlistId, imageData).getOrThrow()
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess("Upload successful")
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets detailed media information synchronously.
     * Provides more comprehensive media details than getMediaInfoSync.
     */
    @JvmStatic
    fun getDetailedMediaInfoSync(videoId: String): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.getMediaInfo(videoId).getOrThrow()
                buildString {
                    appendLine("Media Information:")
                    appendLine("  Title: ${result.title ?: "Unknown"}")
                    appendLine("  Author: ${result.author ?: "Unknown"}")
                    appendLine("  View Count: ${result.viewCount ?: "Unknown"}")
                    appendLine("  Upload Date: ${result.uploadDate ?: "Unknown"}")
                    appendLine("  Description: ${result.description?.take(100) ?: "No description"}...")
                }
            }
        }
    }
    
    /**
     * Gets detailed media information asynchronously.
     * Provides more comprehensive media details than getMediaInfoAsync.
     */
    @JvmStatic
    fun getDetailedMediaInfoAsync(videoId: String, callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.getMediaInfo(videoId).getOrThrow()
                val mediaInfo = buildString {
                    appendLine("Media Information:")
                    appendLine("  Title: ${result.title ?: "Unknown"}")
                    appendLine("  Author: ${result.author ?: "Unknown"}")
                    appendLine("  View Count: ${result.viewCount ?: "Unknown"}")
                    appendLine("  Upload Date: ${result.uploadDate ?: "Unknown"}")
                    appendLine("  Description: ${result.description?.take(100) ?: "No description"}...")
                }
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(mediaInfo)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== SYNCHRONOUS SEARCH FUNCTIONALITY ==========
    
    /**
     * Performs a synchronous search for songs.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun searchSongsSync(query: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                ModelConverter.convertSearchResults(result.items, result.continuation)
            }
        }
    }
    
    /**
     * Performs a synchronous search for albums.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun searchAlbumsSync(query: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrThrow()
                ModelConverter.convertSearchResults(result.items, result.continuation)
            }
        }
    }
    
    /**
     * Performs a synchronous search for artists.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun searchArtistsSync(query: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow()
                ModelConverter.convertSearchResults(result.items, result.continuation)
            }
        }
    }
    
    /**
     * Performs a synchronous search for playlists.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun searchPlaylistsSync(query: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrThrow()
                ModelConverter.convertSearchResults(result.items, result.continuation)
            }
        }
    }
    
    /**
     * Performs a synchronous general search (all content types).
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun searchSync(query: String): SearchResult {
        return searchSongsSync(query) // Default to songs for general search
    }
    
    // ========== SYNCHRONOUS HOME PAGE FUNCTIONALITY ==========
    
    /**
     * Gets the home page feed synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getHomeSync(): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.home().getOrThrow()
                ModelConverter.convertHomePage(result)
            }
        }
    }
    
    /**
     * Gets the home page feed with continuation token synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getHomeContinuationSync(continuation: String): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.home(continuation).getOrThrow()
                ModelConverter.convertHomePage(result)
            }
        }
    }
    
    // ========== SYNCHRONOUS ALBUM FUNCTIONALITY ==========
    
    /**
     * Gets album details and songs synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getAlbumSync(browseId: String): music.resona.online.bridge.models.AlbumPageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.album(browseId).getOrThrow()
                ModelConverter.convertAlbumBasic(result.album, result.songs)
            }
        }
    }
    
    /**
     * Gets album songs only synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getAlbumSongsSync(playlistId: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.albumSongs(playlistId).getOrThrow()
                ModelConverter.convertSearchResults(result)
            }
        }
    }
    
    // ========== SYNCHRONOUS ARTIST FUNCTIONALITY ==========
    
    /**
     * Gets artist details and content synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getArtistSync(browseId: String): music.resona.online.bridge.models.ArtistPageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.artist(browseId).getOrThrow()
                ModelConverter.convertArtistBasic(result.artist, result.sections, result.description)
            }
        }
    }
    
    // ========== SYNCHRONOUS PLAYER FUNCTIONALITY ==========
    
    /**
     * Gets player information for a video synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getPlayerInfoSync(videoId: String): music.resona.online.bridge.models.PlayerResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                // Try multiple clients and strategies for better stream URL extraction
                var bestResult: com.metrolist.innertube.models.response.PlayerResponse? = null
                var lastException: Exception? = null
                
                // Strategy 1: Try MOBILE (Android) client (often has better stream URLs)
                try {
                    val result = YouTube.player(videoId, client = com.metrolist.innertube.models.YouTubeClient.MOBILE).getOrThrow()
                    val converted = ModelConverter.convertPlayerResponse(result)
                    if (converted.streamUrl != null && converted.streamUrl.isNotEmpty()) {
                        println("DEBUG: MOBILE client succeeded")
                        return@runBlocking converted
                    }
                    bestResult = result
                } catch (e: Exception) {
                    println("DEBUG: MOBILE client failed: ${e.message}")
                    lastException = e
                }
                
                // Strategy 2: Try ANDROID_VR_1_43_32 (uses non-adaptive bitrate, good for music)
                try {
                    val result = YouTube.player(videoId, client = com.metrolist.innertube.models.YouTubeClient.ANDROID_VR_1_43_32).getOrThrow()
                    val converted = ModelConverter.convertPlayerResponse(result)
                    if (converted.streamUrl != null && converted.streamUrl.isNotEmpty()) {
                        println("DEBUG: ANDROID_VR_1_43_32 client succeeded")
                        return@runBlocking converted
                    }
                    if (bestResult == null) bestResult = result
                } catch (e: Exception) {
                    println("DEBUG: ANDROID_VR_1_43_32 client failed: ${e.message}")
                    lastException = e
                }
                
                // Strategy 3: Try WEB_REMIX (YouTube Music web client)
                try {
                    val result = YouTube.player(videoId, client = com.metrolist.innertube.models.YouTubeClient.WEB_REMIX).getOrThrow()
                    val converted = ModelConverter.convertPlayerResponse(result)
                    if (converted.streamUrl != null && converted.streamUrl.isNotEmpty()) {
                        println("DEBUG: WEB_REMIX client succeeded")
                        return@runBlocking converted
                    }
                    if (bestResult == null) bestResult = result
                } catch (e: Exception) {
                    println("DEBUG: WEB_REMIX client failed: ${e.message}")
                    lastException = e
                }
                
                // Strategy 4: Try WEB (Regular YouTube web client)
                try {
                    val result = YouTube.player(videoId, client = com.metrolist.innertube.models.YouTubeClient.WEB).getOrThrow()
                    val converted = ModelConverter.convertPlayerResponse(result)
                    if (converted.streamUrl != null && converted.streamUrl.isNotEmpty()) {
                        println("DEBUG: WEB client succeeded")
                        return@runBlocking converted
                    }
                    if (bestResult == null) bestResult = result
                } catch (e: Exception) {
                    println("DEBUG: WEB client failed: ${e.message}")
                    lastException = e
                }
                
                // Strategy 5: Try IOS client
                try {
                    val result = YouTube.player(videoId, client = com.metrolist.innertube.models.YouTubeClient.IOS).getOrThrow()
                    val converted = ModelConverter.convertPlayerResponse(result)
                    if (converted.streamUrl != null && converted.streamUrl.isNotEmpty()) {
                        println("DEBUG: IOS client succeeded")
                        return@runBlocking converted
                    }
                    if (bestResult == null) bestResult = result
                } catch (e: Exception) {
                    println("DEBUG: IOS client failed: ${e.message}")
                    lastException = e
                }
                
                // If we get here, all clients failed to provide a stream URL
                // Return the best result we got (even without stream URL) or throw the last exception
                if (bestResult != null) {
                    println("DEBUG: Returning result without stream URL")
                    return@runBlocking ModelConverter.convertPlayerResponse(bestResult)
                } else {
                    throw lastException ?: Exception("All YouTube clients failed to get player info")
                }
            }
        }
    }
    
    /**
     * Gets player information for a video with playlist context synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getPlayerInfoWithPlaylistSync(videoId: String, playlistId: String): music.resona.online.bridge.models.PlayerResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.player(videoId, playlistId, com.metrolist.innertube.models.YouTubeClient.WEB_REMIX).getOrThrow()
                ModelConverter.convertPlayerResponse(result)
            }
        }
    }
    
    /**
     * Alternative method to get playable stream URL using different approach.
     * This method tries to get stream URL through alternative YouTube endpoints.
     */
    @JvmStatic
    fun getAlternativeStreamUrlSync(videoId: String): String? {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                // Try getting stream URL through different methods
                
                // Method 1: Try with different client configurations
                val clients = listOf(
                    com.metrolist.innertube.models.YouTubeClient.MOBILE,
                    com.metrolist.innertube.models.YouTubeClient.ANDROID_VR_1_43_32,
                    com.metrolist.innertube.models.YouTubeClient.IOS,
                    com.metrolist.innertube.models.YouTubeClient.WEB_REMIX,
                    com.metrolist.innertube.models.YouTubeClient.WEB,
                    com.metrolist.innertube.models.YouTubeClient.IPADOS
                )
                
                for (client in clients) {
                    try {
                        val result = YouTube.player(videoId, client = client).getOrThrow()
                        val streamUrl = ModelConverter.extractStreamUrlPublic(result)
                        if (streamUrl != null && streamUrl.isNotEmpty()) {
                            println("DEBUG: Alternative method succeeded with client: $client")
                            return@runBlocking streamUrl
                        }
                    } catch (e: Exception) {
                        println("DEBUG: Alternative method failed with client $client: ${e.message}")
                    }
                }
                
                println("DEBUG: All alternative methods failed")
                return@runBlocking null
            }
        }
    }
    
    // ========== STREAMING FUNCTIONALITY (NEW - Based on Metrolist) ==========
    
    /**
     * Gets complete playback data with working stream URL synchronously.
     * This method uses NewPipe integration for signature deciphering and
     * implements multiple client fallback strategy to ensure working stream URLs.
     * 
     * This is the RECOMMENDED method for getting stream URLs as it:
     * - Decipher signature ciphers using NewPipe
     * - Try multiple YouTube clients (ANDROID_VR, WEB_REMIX, IOS, etc.)
     * - Validates stream URLs before returning
     * - Returns complete format information (bitrate, codec, etc.)
     * 
     * @param videoId The YouTube video ID
     * @return PlaybackDataResult containing stream URL and metadata
     */
    @JvmStatic
    fun getPlaybackDataSync(videoId: String): PlaybackDataResult {
        return getPlaybackDataWithQualitySync(videoId, "HIGH", false)
    }
    
    /**
     * Gets playback data with specific audio quality preference.
     * 
     * @param videoId The YouTube video ID
     * @param audioQuality Audio quality: "LOW", "AUTO", or "HIGH"
     * @param isNetworkMetered Whether the network connection is metered (affects AUTO quality)
     * @return PlaybackDataResult containing stream URL and metadata
     */
    @JvmStatic
    fun getPlaybackDataWithQualitySync(videoId: String, audioQuality: String, isNetworkMetered: Boolean): PlaybackDataResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val quality = when (audioQuality.uppercase()) {
                    "LOW" -> StreamingUtils.AudioQuality.LOW
                    "AUTO" -> StreamingUtils.AudioQuality.AUTO
                    "HIGH" -> StreamingUtils.AudioQuality.HIGH
                    else -> StreamingUtils.AudioQuality.HIGH
                }
                
                val playbackData = StreamingUtils.getPlaybackData(
                    videoId = videoId,
                    playlistId = null,
                    audioQuality = quality,
                    isNetworkMetered = isNetworkMetered
                ).getOrThrow()
                
                // Convert to bridge model
                PlaybackDataResult(
                    videoId = playbackData.videoDetails?.videoId ?: videoId,
                    title = playbackData.videoDetails?.title ?: "",
                    artist = playbackData.videoDetails?.author,
                    thumbnail = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                    duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull(),
                    streamUrl = playbackData.streamUrl,
                    streamExpiresInSeconds = playbackData.streamExpiresInSeconds,
                    mimeType = playbackData.mimeType,
                    bitrate = playbackData.bitrate,
                    audioQuality = playbackData.format.audioQuality,
                    audioSampleRate = playbackData.format.audioSampleRate,
                    audioChannels = playbackData.format.audioChannels,
                    contentLength = playbackData.contentLength,
                    loudnessDb = playbackData.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                    isPlayable = true,
                    playabilityStatus = "OK"
                )
            }
        }
    }
    
    /**
     * Gets playback data with playlist context for better recommendations.
     * 
     * @param videoId The YouTube video ID
     * @param playlistId The playlist ID for context
     * @return PlaybackDataResult containing stream URL and metadata
     */
    @JvmStatic
    fun getPlaybackDataWithPlaylistSync(videoId: String, playlistId: String): PlaybackDataResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val playbackData = StreamingUtils.getPlaybackData(
                    videoId = videoId,
                    playlistId = playlistId,
                    audioQuality = StreamingUtils.AudioQuality.HIGH,
                    isNetworkMetered = false
                ).getOrThrow()
                
                PlaybackDataResult(
                    videoId = playbackData.videoDetails?.videoId ?: videoId,
                    title = playbackData.videoDetails?.title ?: "",
                    artist = playbackData.videoDetails?.author,
                    thumbnail = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                    duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull(),
                    streamUrl = playbackData.streamUrl,
                    streamExpiresInSeconds = playbackData.streamExpiresInSeconds,
                    mimeType = playbackData.mimeType,
                    bitrate = playbackData.bitrate,
                    audioQuality = playbackData.format.audioQuality,
                    audioSampleRate = playbackData.format.audioSampleRate,
                    audioChannels = playbackData.format.audioChannels,
                    contentLength = playbackData.contentLength,
                    loudnessDb = playbackData.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                    isPlayable = true,
                    playabilityStatus = "OK"
                )
            }
        }
    }
    
    /**
     * Gets just the stream URL without full metadata (faster).
     * 
     * @param videoId The YouTube video ID
     * @return Stream URL string
     */
    @JvmStatic
    fun getStreamUrlSync(videoId: String): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                StreamingUtils.getStreamUrl(videoId).getOrThrow()
            }
        }
    }
    
    /**
     * Gets playback data asynchronously.
     * Executes on IO dispatcher and delivers callback on Main dispatcher.
     * 
     * @param videoId The YouTube video ID
     * @param callback Callback to receive the result
     */
    @JvmStatic
    fun getPlaybackDataAsync(videoId: String, callback: PlaybackDataCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playbackData = StreamingUtils.getPlaybackData(
                    videoId = videoId,
                    playlistId = null,
                    audioQuality = StreamingUtils.AudioQuality.HIGH,
                    isNetworkMetered = false
                ).getOrThrow()
                
                val result = PlaybackDataResult(
                    videoId = playbackData.videoDetails?.videoId ?: videoId,
                    title = playbackData.videoDetails?.title ?: "",
                    artist = playbackData.videoDetails?.author,
                    thumbnail = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                    duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull(),
                    streamUrl = playbackData.streamUrl,
                    streamExpiresInSeconds = playbackData.streamExpiresInSeconds,
                    mimeType = playbackData.mimeType,
                    bitrate = playbackData.bitrate,
                    audioQuality = playbackData.format.audioQuality,
                    audioSampleRate = playbackData.format.audioSampleRate,
                    audioChannels = playbackData.format.audioChannels,
                    contentLength = playbackData.contentLength,
                    loudnessDb = playbackData.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                    isPlayable = true,
                    playabilityStatus = "OK"
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(result)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets playback data with quality preference asynchronously.
     * 
     * @param videoId The YouTube video ID
     * @param audioQuality Audio quality: "LOW", "AUTO", or "HIGH"
     * @param isNetworkMetered Whether the network connection is metered
     * @param callback Callback to receive the result
     */
    @JvmStatic
    fun getPlaybackDataWithQualityAsync(videoId: String, audioQuality: String, isNetworkMetered: Boolean, callback: PlaybackDataCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val quality = when (audioQuality.uppercase()) {
                    "LOW" -> StreamingUtils.AudioQuality.LOW
                    "AUTO" -> StreamingUtils.AudioQuality.AUTO
                    "HIGH" -> StreamingUtils.AudioQuality.HIGH
                    else -> StreamingUtils.AudioQuality.HIGH
                }
                
                val playbackData = StreamingUtils.getPlaybackData(
                    videoId = videoId,
                    playlistId = null,
                    audioQuality = quality,
                    isNetworkMetered = isNetworkMetered
                ).getOrThrow()
                
                val result = PlaybackDataResult(
                    videoId = playbackData.videoDetails?.videoId ?: videoId,
                    title = playbackData.videoDetails?.title ?: "",
                    artist = playbackData.videoDetails?.author,
                    thumbnail = playbackData.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
                    duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull(),
                    streamUrl = playbackData.streamUrl,
                    streamExpiresInSeconds = playbackData.streamExpiresInSeconds,
                    mimeType = playbackData.mimeType,
                    bitrate = playbackData.bitrate,
                    audioQuality = playbackData.format.audioQuality,
                    audioSampleRate = playbackData.format.audioSampleRate,
                    audioChannels = playbackData.format.audioChannels,
                    contentLength = playbackData.contentLength,
                    loudnessDb = playbackData.audioConfig?.loudnessDb,
                    perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                    isPlayable = true,
                    playabilityStatus = "OK"
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(result)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== SYNCHRONOUS PLAYLIST FUNCTIONALITY ==========
    
    /**
     * Gets playlist details and songs synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getPlaylistSync(playlistId: String): music.resona.online.bridge.models.PlaylistResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.playlist(playlistId).getOrThrow()
                ModelConverter.convertPlaylistBasic(
                    id = result.playlist.id,
                    title = result.playlist.title,
                    songs = result.songs
                )
            }
        }
    }
    
    /**
     * Creates a new playlist synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Returns the created playlist ID.
     */
    @JvmStatic
    fun createPlaylistSync(title: String): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.createPlaylist(title)
            }
        }
    }
    
    // ========== SYNCHRONOUS EXPLORE FUNCTIONALITY ==========
    
    /**
     * Gets the explore page with new releases and recommendations synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getExploreSync(): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.explore().getOrThrow()
                // Convert ExplorePage to HomePageResult format
                HomePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "New Release Albums",
                            items = ModelConverter.convertYTItems(result.newReleaseAlbums)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Mood & Genres",
                            items = result.moodAndGenres.map { moodGenre ->
                                music.resona.online.bridge.models.YTItemResult(
                                    id = moodGenre.endpoint?.browseId ?: "",
                                    title = moodGenre.title,
                                    thumbnail = "",
                                    type = "genre"
                                )
                            }
                        )
                    )
                )
            }
        }
    }
    
    /**
     * Gets the explore page synchronously with ExplorePageResult.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getExplorePage(): music.resona.online.bridge.models.ExplorePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.explore().getOrThrow()
                music.resona.online.bridge.models.ExplorePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "New Release Albums",
                            items = ModelConverter.convertYTItems(result.newReleaseAlbums)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Mood & Genres",
                            items = result.moodAndGenres.map { moodGenre ->
                                music.resona.online.bridge.models.YTItemResult(
                                    id = moodGenre.endpoint?.browseId ?: "",
                                    title = moodGenre.title,
                                    thumbnail = "",
                                    type = "genre"
                                )
                            }
                        )
                    )
                )
            }
        }
    }
    
    /**
     * Gets next page with continuation token synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getNextPage(continuation: String): music.resona.online.bridge.models.NextPageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = null,
                    playlistId = null,
                    playlistSetVideoId = null,
                    index = null,
                    params = null
                )
                val result = YouTube.next(endpoint, continuation).getOrThrow()
                music.resona.online.bridge.models.NextPageResult(
                    items = result.items.map { item ->
                        music.resona.online.bridge.models.YTItemResult(
                            id = item.id,
                            title = item.title,
                            thumbnail = item.thumbnail,
                            type = "song",
                            artists = item.artists.map { artist ->
                                music.resona.online.bridge.models.ArtistResult(
                                    id = artist.id ?: "",
                                    name = artist.name
                                )
                            },
                            album = item.album?.let {
                                music.resona.online.bridge.models.AlbumResult(
                                    id = it.id,
                                    name = it.name
                                )
                            },
                            duration = item.duration
                        )
                    },
                    continuation = result.continuation
                )
            }
        }
    }
    
    /**
     * Gets charts data synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getChartsSync(): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.getChartsPage().getOrThrow()
                HomePageResult(
                    sections = result.sections.map { section ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = section.title,
                            items = ModelConverter.convertYTItems(section.items)
                        )
                    }
                )
            }
        }
    }
    
    /**
     * Gets new release albums synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getNewReleaseAlbumsSync(): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.newReleaseAlbums().getOrThrow()
                ModelConverter.convertSearchResults(result)
            }
        }
    }
    
    /**
     * Gets mood and genres synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getMoodAndGenresSync(): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.moodAndGenres().getOrThrow()
                HomePageResult(
                    sections = result.map { moodGenre ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = moodGenre.title,
                            items = moodGenre.items.map { item ->
                                music.resona.online.bridge.models.YTItemResult(
                                    id = item.endpoint?.browseId ?: "",
                                    title = item.title,
                                    thumbnail = "",
                                    type = "genre_item"
                                )
                            }
                        )
                    }
                )
            }
        }
    }
    
    // ========== SYNCHRONOUS ADVANCED FUNCTIONALITY ==========
    
    /**
     * Gets lyrics for a video synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getLyricsSync(browseId: String, params: String?): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val endpoint = com.metrolist.innertube.models.BrowseEndpoint(browseId, params)
                YouTube.lyrics(endpoint).getOrThrow() ?: "No lyrics available"
            }
        }
    }
    
    /**
     * Gets transcript for a video synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getTranscriptSync(videoId: String): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.transcript(videoId).getOrThrow()
            }
        }
    }
    
    /**
     * Gets related content for a video synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getRelatedContentSync(browseId: String): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val endpoint = com.metrolist.innertube.models.BrowseEndpoint(browseId, null)
                val result = YouTube.related(endpoint).getOrThrow()
                HomePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Songs",
                            items = ModelConverter.convertYTItems(result.songs)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Albums",
                            items = ModelConverter.convertYTItems(result.albums)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Artists",
                            items = ModelConverter.convertYTItems(result.artists)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Playlists",
                            items = ModelConverter.convertYTItems(result.playlists)
                        )
                    )
                )
            }
        }
    }
    
    /**
     * Gets queue for videos synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getQueueSync(videoIds: Array<String>): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.queue(videoIds.toList()).getOrThrow()
                ModelConverter.convertSearchResults(result)
            }
        }
    }
    
    /**
     * Gets next songs in queue synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getNextSongsSync(videoId: String, playlistId: String?): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = playlistId
                )
                val result = YouTube.next(endpoint).getOrThrow()
                ModelConverter.convertSearchResults(result.items)
            }
        }
    }
    
    /**
     * Gets radio/automix queue for a video synchronously.
     * This generates a continuous mix of similar songs based on the seed video.
     * Perfect for creating endless playback queues.
     * 
     * @param videoId The seed video ID to generate radio from
     * @param playlistId Optional playlist context for better recommendations
     * @return SearchResult containing the generated radio queue
     */
    @JvmStatic
    fun getRadioQueueSync(videoId: String, playlistId: String?): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = playlistId,
                    params = "wAEB" // Radio/automix parameter
                )
                val result = YouTube.next(endpoint).getOrThrow()
                ModelConverter.convertSearchResults(result.items)
            }
        }
    }
    
    /**
     * Gets continuation of radio/next queue synchronously.
     * Use this to load more songs when approaching the end of the queue.
     * 
     * @param videoId Current video ID
     * @param playlistId Optional playlist ID
     * @param continuation Continuation token from previous getRadioQueue or getNextSongs call
     * @return SearchResult containing additional songs
     */
    @JvmStatic
    fun getQueueContinuationSync(videoId: String, playlistId: String?, continuation: String): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = playlistId
                )
                val result = YouTube.next(endpoint, continuation).getOrThrow()
                ModelConverter.convertSearchResults(result.items)
            }
        }
    }
    
    /**
     * Gets media information for a video synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getMediaInfoSync(videoId: String): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.getMediaInfo(videoId).getOrThrow()
                "Title: ${result.title ?: "Unknown"}, Author: ${result.author ?: "Unknown"}"
            }
        }
    }
    
    // ========== SYNCHRONOUS LIBRARY FUNCTIONALITY ==========
    
    /**
     * Gets user library content synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getLibrarySync(): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.library("FEmusic_library_landing").getOrThrow()
                // Convert library result to HomePageResult format
                HomePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Library",
                            items = ModelConverter.convertYTItems(result.items)
                        )
                    )
                )
            }
        }
    }
    
    /**
     * Gets user's liked songs synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getLikedSongsSync(): SearchResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.library("FEmusic_liked_videos").getOrThrow()
                ModelConverter.convertSearchResults(result.items)
            }
        }
    }
    
    /**
     * Gets user's music history synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getMusicHistorySync(): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.musicHistory().getOrThrow()
                HomePageResult(
                    sections = result.sections?.map { section ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = section.title,
                            items = ModelConverter.convertYTItems(section.songs)
                        )
                    } ?: emptyList()
                )
            }
        }
    }
    
    /**
     * Gets user's recent activity synchronously.
     * Executes on background thread to avoid blocking the main thread.
     */
    @JvmStatic
    fun getRecentActivitySync(): HomePageResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.libraryRecentActivity().getOrThrow()
                HomePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Recent Activity",
                            items = ModelConverter.convertYTItems(result.items)
                        )
                    )
                )
            }
        }
    }
    
    // ========== SYNCHRONOUS AUTHENTICATION FUNCTIONALITY ==========
    
    /**
     * Sets authentication cookie synchronously.
     * This method configures the YouTube client with authentication credentials.
     */
    @JvmStatic
    fun setCookieSync(cookie: String) {
        YouTube.cookie = cookie
    }
    
    /**
     * Gets current authentication cookie synchronously.
     * Returns null if no cookie is set.
     */
    @JvmStatic
    fun getCookieSync(): String? {
        return YouTube.cookie
    }
    
    /**
     * Sets visitor data synchronously.
     * Visitor data is used for anonymous YouTube Music access.
     */
    @JvmStatic
    fun setVisitorDataSync(visitorData: String) {
        YouTube.visitorData = visitorData
    }
    
    /**
     * Gets current visitor data synchronously.
     * Returns null if no visitor data is set.
     */
    @JvmStatic
    fun getVisitorDataSync(): String? {
        return YouTube.visitorData
    }
    
    /**
     * Fetches new visitor data from YouTube Music synchronously.
     * This generates a new visitor data token for anonymous access.
     * Executes on background thread to avoid blocking the main thread.
     * Automatically sets the fetched visitor data.
     */
    @JvmStatic
    fun fetchVisitorDataSync(): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val visitorData = YouTube.visitorData().getOrThrow()
                YouTube.visitorData = visitorData
                visitorData
            }
        }
    }
    
    /**
     * Fetches new visitor data from YouTube Music asynchronously.
     * This generates a new visitor data token for anonymous access.
     * Executes on background thread and delivers callbacks on main thread.
     * Automatically sets the fetched visitor data.
     */
    @JvmStatic
    fun fetchVisitorDataAsync(callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val visitorData = YouTube.visitorData().getOrThrow()
                YouTube.visitorData = visitorData
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(visitorData)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets the current visitor data value.
     * Returns null if visitor data has not been set.
     */
    @JvmStatic
    fun getVisitorData(): String? {
        return YouTube.visitorData
    }
    
    /**
     * Sets data sync ID synchronously.
     * Data sync ID is used for synchronizing user data across sessions.
     */
    @JvmStatic
    fun setDataSyncIdSync(dataSyncId: String) {
        YouTube.dataSyncId = dataSyncId
    }
    
    /**
     * Gets current data sync ID synchronously.
     * Returns null if no data sync ID is set.
     */
    @JvmStatic
    fun getDataSyncIdSync(): String? {
        return YouTube.dataSyncId
    }
    
    /**
     * Gets account information synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication cookie to be set.
     */
    @JvmStatic
    fun getAccountInfoSync(): String {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.accountInfo().getOrThrow()
                "${result.name} (${result.email})"
            }
        }
    }
    
    /**
     * Gets structured account information synchronously.
     * Returns AccountInfoResult with name, email, channel handle, and thumbnail URL.
     * Requires authentication cookie to be set.
     */
    @JvmStatic
    fun getAccountDetailsSync(): music.resona.online.bridge.models.AccountInfoResult {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                val result = YouTube.accountInfo().getOrThrow()
                music.resona.online.bridge.models.AccountInfoResult(
                    result.name,
                    result.email,
                    result.channelHandle,
                    result.thumbnailUrl
                )
            }
        }
    }
    
    /**
     * Checks if the user is currently authenticated synchronously.
     * Returns true if authentication cookie is set and valid.
     */
    @JvmStatic
    fun isAuthenticatedSync(): Boolean {
        return YouTube.cookie != null && YouTube.cookie!!.isNotEmpty()
    }
    
    /**
     * Sets complete authentication session synchronously.
     * This method sets all authentication-related data at once.
     */
    @JvmStatic
    fun setAuthenticationSessionSync(cookie: String, visitorData: String?, dataSyncId: String?) {
        YouTube.cookie = cookie
        visitorData?.let { YouTube.visitorData = it }
        dataSyncId?.let { YouTube.dataSyncId = it }
    }
    
    /**
     * Gets complete authentication session data synchronously.
     * Returns a formatted string with all authentication data.
     */
    @JvmStatic
    fun getAuthenticationSessionSync(): String {
        return buildString {
            appendLine("Authentication Session:")
            appendLine("  Cookie: ${if (YouTube.cookie != null) "Set (${YouTube.cookie!!.length} chars)" else "None"}")
            appendLine("  Visitor Data: ${YouTube.visitorData ?: "None"}")
            appendLine("  Data Sync ID: ${YouTube.dataSyncId ?: "None"}")
            appendLine("  Authenticated: ${isAuthenticatedSync()}")
        }
    }
    
    /**
     * Clears authentication data synchronously.
     * This effectively logs out the user.
     */
    @JvmStatic
    fun logoutSync() {
        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
    }
    
    /**
     * Validates current authentication synchronously.
     * Attempts to fetch account info to verify authentication is working.
     * Returns true if authentication is valid, false otherwise.
     */
    @JvmStatic
    fun validateAuthenticationSync(): Boolean {
        return try {
            if (!isAuthenticatedSync()) {
                false
            } else {
                runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                    YouTube.accountInfo().isSuccess
                }
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Switches to a different user account synchronously.
     * This method allows switching between multiple user accounts.
     */
    @JvmStatic
    fun switchAccountSync(cookie: String, visitorData: String?, dataSyncId: String?) {
        // Clear current session
        logoutSync()
        // Set new session
        setAuthenticationSessionSync(cookie, visitorData, dataSyncId)
    }
    
    // ========== SYNCHRONOUS INTERACTION FUNCTIONALITY ==========
    
    /**
     * Likes a video/song synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun likeVideoSync(videoId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.likeVideo(videoId, true).isSuccess
            }
        }
    }
    
    /**
     * Unlikes a video/song synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun unlikeVideoSync(videoId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.likeVideo(videoId, false).isSuccess
            }
        }
    }
    
    /**
     * Likes a playlist synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun likePlaylistSync(playlistId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.likePlaylist(playlistId, true).isSuccess
            }
        }
    }
    
    /**
     * Unlikes a playlist synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun unlikePlaylistSync(playlistId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.likePlaylist(playlistId, false).isSuccess
            }
        }
    }
    
    /**
     * Subscribes to a channel synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun subscribeChannelSync(channelId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.subscribeChannel(channelId, true).isSuccess
            }
        }
    }
    
    /**
     * Unsubscribes from a channel synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun unsubscribeChannelSync(channelId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.subscribeChannel(channelId, false).isSuccess
            }
        }
    }
    
    /**
     * Adds a song to a playlist synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun addToPlaylistSync(playlistId: String, videoId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.addToPlaylist(playlistId, videoId).isSuccess
            }
        }
    }
    
    /**
     * Removes a song from a playlist synchronously.
     * Executes on background thread to avoid blocking the main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun removeFromPlaylistSync(playlistId: String, videoId: String, setVideoId: String): Boolean {
        return ExceptionConverter.convertExceptions {
            runBlocking(backgroundExecutor.asCoroutineDispatcher()) {
                YouTube.removeFromPlaylist(playlistId, videoId, setVideoId).isSuccess
            }
        }
    }
    
    // ========== ASYNCHRONOUS SEARCH FUNCTIONALITY ==========
    
    /**
     * Performs an asynchronous search for songs.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun searchSongsAsync(query: String, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items, result.continuation)
                
                // Deliver callback on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                // Deliver error callback on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Performs an asynchronous search for albums.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun searchAlbumsAsync(query: String, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ALBUM).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items, result.continuation)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Performs an asynchronous search for artists.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun searchArtistsAsync(query: String, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_ARTIST).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items, result.continuation)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Performs an asynchronous search for playlists.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun searchPlaylistsAsync(query: String, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items, result.continuation)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Performs an asynchronous general search (defaults to songs).
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun searchAsync(query: String, callback: SearchCallback) {
        searchSongsAsync(query, callback)
    }
    
    // ========== ASYNCHRONOUS HOME PAGE FUNCTIONALITY ==========
    
    /**
     * Gets the home page feed asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getHomeAsync(callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.home().getOrThrow()
                val bridgeResult = ModelConverter.convertHomePage(result)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS ALBUM FUNCTIONALITY ==========
    
    /**
     * Gets album details and songs asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getAlbumAsync(browseId: String, callback: AlbumCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.album(browseId).getOrThrow()
                val bridgeResult = ModelConverter.convertAlbumBasic(result.album, result.songs)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS ARTIST FUNCTIONALITY ==========
    
    /**
     * Gets artist details and content asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getArtistAsync(browseId: String, callback: ArtistCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.artist(browseId).getOrThrow()
                val bridgeResult = ModelConverter.convertArtistBasic(result.artist, result.sections, result.description)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS PLAYER FUNCTIONALITY ==========
    
    /**
     * Gets player information for a video asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getPlayerInfoAsync(videoId: String, callback: PlayerCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.player(videoId, client = com.metrolist.innertube.models.YouTubeClient.WEB_REMIX).getOrThrow()
                val bridgeResult = ModelConverter.convertPlayerResponse(result)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS PLAYLIST FUNCTIONALITY ==========
    
    /**
     * Gets playlist details and songs asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getPlaylistAsync(playlistId: String, callback: PlaylistCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.playlist(playlistId).getOrThrow()
                val bridgeResult = ModelConverter.convertPlaylistBasic(
                    id = result.playlist.id,
                    title = result.playlist.title,
                    songs = result.songs
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Creates a new playlist asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun createPlaylistAsync(title: String, callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val playlistId = YouTube.createPlaylist(title)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(playlistId)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS AUTHENTICATION FUNCTIONALITY ==========
    
    /**
     * Gets account information asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication cookie to be set.
     */
    @JvmStatic
    fun getAccountInfoAsync(callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.accountInfo().getOrThrow()
                val accountInfo = "${result.name} (${result.email})"
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(accountInfo)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Validates current authentication asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Attempts to fetch account info to verify authentication is working.
     */
    @JvmStatic
    fun validateAuthenticationAsync(callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val isValid = if (!isAuthenticatedSync()) {
                    false
                } else {
                    YouTube.accountInfo().isSuccess
                }
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(isValid)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Sets complete authentication session asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * This method sets all authentication-related data at once.
     */
    @JvmStatic
    fun setAuthenticationSessionAsync(cookie: String, visitorData: String?, dataSyncId: String?, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YouTube.cookie = cookie
                visitorData?.let { YouTube.visitorData = it }
                dataSyncId?.let { YouTube.dataSyncId = it }
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(true)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets complete authentication session data asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Returns a formatted string with all authentication data.
     */
    @JvmStatic
    fun getAuthenticationSessionAsync(callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionInfo = buildString {
                    appendLine("Authentication Session:")
                    appendLine("  Cookie: ${if (YouTube.cookie != null) "Set (${YouTube.cookie!!.length} chars)" else "None"}")
                    appendLine("  Visitor Data: ${YouTube.visitorData ?: "None"}")
                    appendLine("  Data Sync ID: ${YouTube.dataSyncId ?: "None"}")
                    appendLine("  Authenticated: ${isAuthenticatedSync()}")
                }
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(sessionInfo)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Switches to a different user account asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * This method allows switching between multiple user accounts.
     */
    @JvmStatic
    fun switchAccountAsync(cookie: String, visitorData: String?, dataSyncId: String?, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clear current session
                logoutSync()
                // Set new session
                setAuthenticationSessionSync(cookie, visitorData, dataSyncId)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(true)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS LIBRARY AND HISTORY FUNCTIONALITY ==========
    
    /**
     * Gets user's liked songs asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getLikedSongsAsync(callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.library("FEmusic_liked_videos").getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets user's music history asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getMusicHistoryAsync(callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.musicHistory().getOrThrow()
                val bridgeResult = HomePageResult(
                    sections = result.sections?.map { section ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = section.title,
                            items = ModelConverter.convertYTItems(section.songs)
                        )
                    } ?: emptyList()
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets user's recent activity asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getRecentActivityAsync(callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.libraryRecentActivity().getOrThrow()
                val bridgeResult = HomePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Recent Activity",
                            items = ModelConverter.convertYTItems(result.items)
                        )
                    )
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS INTERACTION FUNCTIONALITY ==========
    
    /**
     * Likes a video/song asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun likeVideoAsync(videoId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.likeVideo(videoId, true).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Unlikes a video/song asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun unlikeVideoAsync(videoId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.likeVideo(videoId, false).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Likes a playlist asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun likePlaylistAsync(playlistId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.likePlaylist(playlistId, true).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Unlikes a playlist asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun unlikePlaylistAsync(playlistId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.likePlaylist(playlistId, false).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Subscribes to a channel asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun subscribeChannelAsync(channelId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.subscribeChannel(channelId, true).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Unsubscribes from a channel asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun unsubscribeChannelAsync(channelId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.subscribeChannel(channelId, false).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Adds a song to a playlist asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun addToPlaylistAsync(playlistId: String, videoId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.addToPlaylist(playlistId, videoId).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Removes a song from a playlist asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     * Requires authentication.
     */
    @JvmStatic
    fun removeFromPlaylistAsync(playlistId: String, videoId: String, setVideoId: String, callback: music.resona.online.bridge.callbacks.BooleanCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = YouTube.removeFromPlaylist(playlistId, videoId, setVideoId).isSuccess
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(success)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS EXPLORE AND CHARTS FUNCTIONALITY ==========
    
    /**
     * Gets charts data asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getChartsAsync(callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.getChartsPage().getOrThrow()
                val bridgeResult = HomePageResult(
                    sections = result.sections.map { section ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = section.title,
                            items = ModelConverter.convertYTItems(section.items)
                        )
                    }
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets new release albums asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getNewReleaseAlbumsAsync(callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.newReleaseAlbums().getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets mood and genres asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getMoodAndGenresAsync(callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.moodAndGenres().getOrThrow()
                val bridgeResult = HomePageResult(
                    sections = result.map { moodGenre ->
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = moodGenre.title,
                            items = moodGenre.items.map { item ->
                                music.resona.online.bridge.models.YTItemResult(
                                    id = item.endpoint?.browseId ?: "",
                                    title = item.title,
                                    thumbnail = "",
                                    type = "genre_item"
                                )
                            }
                        )
                    }
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets the explore page with new releases and recommendations asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getExploreAsync(callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.explore().getOrThrow()
                val bridgeResult = HomePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "New Release Albums",
                            items = ModelConverter.convertYTItems(result.newReleaseAlbums)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Mood & Genres",
                            items = result.moodAndGenres.map { moodGenre ->
                                music.resona.online.bridge.models.YTItemResult(
                                    id = moodGenre.endpoint?.browseId ?: "",
                                    title = moodGenre.title,
                                    thumbnail = "",
                                    type = "genre"
                                )
                            }
                        )
                    )
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets the explore page asynchronously with ExplorePageCallback.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getExploreAsync(callback: ExplorePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.explore().getOrThrow()
                val bridgeResult = music.resona.online.bridge.models.ExplorePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "New Release Albums",
                            items = ModelConverter.convertYTItems(result.newReleaseAlbums)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Mood & Genres",
                            items = result.moodAndGenres.map { moodGenre ->
                                music.resona.online.bridge.models.YTItemResult(
                                    id = moodGenre.endpoint?.browseId ?: "",
                                    title = moodGenre.title,
                                    thumbnail = "",
                                    type = "genre"
                                )
                            }
                        )
                    )
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets next page with continuation token asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getNextAsync(continuation: String, callback: NextPageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Call innertube with continuation and empty endpoint
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = null,
                    playlistId = null,
                    playlistSetVideoId = null,
                    index = null,
                    params = null
                )
                val result = YouTube.next(endpoint, continuation).getOrThrow()
                val bridgeResult = music.resona.online.bridge.models.NextPageResult(
                    items = result.items.map { item ->
                        music.resona.online.bridge.models.YTItemResult(
                            id = item.id,
                            title = item.title,
                            thumbnail = item.thumbnail,
                            type = "song",
                            artists = item.artists.map { artist ->
                                music.resona.online.bridge.models.ArtistResult(
                                    id = artist.id ?: "",
                                    name = artist.name
                                )
                            },
                            album = item.album?.let {
                                music.resona.online.bridge.models.AlbumResult(
                                    id = it.id,
                                    name = it.name
                                )
                            },
                            duration = item.duration
                        )
                    },
                    continuation = result.continuation
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    // ========== ASYNCHRONOUS ADVANCED FUNCTIONALITY ==========
    
    /**
     * Gets lyrics for a video asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getLyricsAsync(browseId: String, params: String?, callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endpoint = com.metrolist.innertube.models.BrowseEndpoint(browseId, params)
                val lyrics = YouTube.lyrics(endpoint).getOrThrow() ?: "No lyrics available"
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(lyrics)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets transcript for a video asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getTranscriptAsync(videoId: String, callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transcript = YouTube.transcript(videoId).getOrThrow()
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(transcript)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets related content for a video asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getRelatedContentAsync(browseId: String, callback: HomePageCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endpoint = com.metrolist.innertube.models.BrowseEndpoint(browseId, null)
                val result = YouTube.related(endpoint).getOrThrow()
                val bridgeResult = HomePageResult(
                    sections = listOf(
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Songs",
                            items = ModelConverter.convertYTItems(result.songs)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Albums",
                            items = ModelConverter.convertYTItems(result.albums)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Artists",
                            items = ModelConverter.convertYTItems(result.artists)
                        ),
                        music.resona.online.bridge.models.HomeSectionResult(
                            title = "Related Playlists",
                            items = ModelConverter.convertYTItems(result.playlists)
                        )
                    )
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets queue for videos asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getQueueAsync(videoIds: Array<String>, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.queue(videoIds.toList()).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets next songs in queue asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getNextSongsAsync(videoId: String, playlistId: String?, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = playlistId
                )
                val result = YouTube.next(endpoint).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets radio/automix queue for a video asynchronously.
     * This generates a continuous mix of similar songs based on the seed video.
     * Perfect for creating endless playback queues.
     * Executes on background thread and delivers callbacks on main thread.
     * 
     * @param videoId The seed video ID to generate radio from
     * @param playlistId Optional playlist context for better recommendations
     * @param callback Callback to receive the radio queue result
     */
    @JvmStatic
    fun getRadioQueueAsync(videoId: String, playlistId: String?, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = playlistId,
                    params = "wAEB" // Radio/automix parameter
                )
                val result = YouTube.next(endpoint).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets continuation of radio/next queue asynchronously.
     * Use this to load more songs when approaching the end of the queue.
     * Executes on background thread and delivers callbacks on main thread.
     * 
     * @param videoId Current video ID
     * @param playlistId Optional playlist ID
     * @param continuation Continuation token from previous call
     * @param callback Callback to receive additional songs
     */
    @JvmStatic
    fun getQueueContinuationAsync(videoId: String, playlistId: String?, continuation: String, callback: SearchCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val endpoint = com.metrolist.innertube.models.WatchEndpoint(
                    videoId = videoId,
                    playlistId = playlistId
                )
                val result = YouTube.next(endpoint, continuation).getOrThrow()
                val bridgeResult = ModelConverter.convertSearchResults(result.items)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(bridgeResult)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
    
    /**
     * Gets media information for a video asynchronously.
     * Executes on background thread and delivers callbacks on main thread.
     */
    @JvmStatic
    fun getMediaInfoAsync(videoId: String, callback: StringCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = YouTube.getMediaInfo(videoId).getOrThrow()
                val mediaInfo = "Title: ${result.title ?: "Unknown"}, Author: ${result.author ?: "Unknown"}"
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onSuccess(mediaInfo)
                }
            } catch (throwable: Throwable) {
                val bridgeException = ExceptionConverter.convertToBridgeException(throwable)
                
                CoroutineScope(Dispatchers.Main).launch {
                    callback.onError(bridgeException)
                }
            }
        }
    }
}