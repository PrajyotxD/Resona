/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 19-12-2025
 *
 * Description :
 * Streaming utility based on Metrolist's YTPlayerUtils logic.
 * Handles stream URL extraction with signature deciphering,
 * multiple client fallback, and URL validation.
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.utils

import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Utility object for extracting playable stream URLs from YouTube player responses.
 * Based on Metrolist's YTPlayerUtils implementation with signature deciphering support.
 */
object StreamingUtils {
    
    private val httpClient = OkHttpClient.Builder()
        .proxy(YouTube.proxy)
        .build()
    
    /**
     * The main client is used for metadata and initial streams.
     * ANDROID_VR_1_43_32 provides correct metadata and good audio quality.
     */
    private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_1_43_32
    
    /**
     * Clients used for fallback streams in case the main client's streams don't work.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        ANDROID_VR_1_61_48,
        WEB_REMIX,
        ANDROID_CREATOR,
        IPADOS,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        TVHTML5,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        WEB,
        WEB_CREATOR
    )
    
    /**
     * Audio quality preference for stream selection.
     */
    enum class AudioQuality {
        LOW,    // Prefer lower bitrate (data saving)
        AUTO,   // Automatic based on network
        HIGH    // Prefer highest bitrate (best quality)
    }
    
    /**
     * Data class representing playback data with stream URL and metadata.
     */
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val mimeType: String,
        val bitrate: Int,
        val contentLength: Long?
    )
    
    /**
     * Gets playback data with working stream URL using multiple client fallback strategy.
     * This is the main method for obtaining playable stream URLs.
     */
    suspend fun getPlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality = AudioQuality.HIGH,
        isNetworkMetered: Boolean = false
    ): Result<PlaybackData> = withContext(Dispatchers.IO) {
        runCatching {
            // Get signature timestamp for deobfuscation
            val signatureTimestamp = getSignatureTimestampOrNull(videoId)
            
            val isLoggedIn = YouTube.cookie != null
            
            // Get main player response for metadata
            val mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp).getOrThrow()
            val audioConfig = mainPlayerResponse.playerConfig?.audioConfig
            val videoDetails = mainPlayerResponse.videoDetails
            
            var format: PlayerResponse.StreamingData.Format? = null
            var streamUrl: String? = null
            var streamExpiresInSeconds: Int? = null
            var streamPlayerResponse: PlayerResponse? = null
            
            // Try main client first, then fallback clients
            for (clientIndex in (-1 until STREAM_FALLBACK_CLIENTS.size)) {
                // Reset for each client
                format = null
                streamUrl = null
                streamExpiresInSeconds = null
                
                // Decide which client to use
                val client: YouTubeClient
                if (clientIndex == -1) {
                    client = MAIN_CLIENT
                    streamPlayerResponse = mainPlayerResponse
                } else {
                    client = STREAM_FALLBACK_CLIENTS[clientIndex]
                    
                    // Skip if client requires login but user is not logged in
                    if (client.loginRequired && !isLoggedIn) {
                        continue
                    }
                    
                    streamPlayerResponse = YouTube.player(videoId, playlistId, client, signatureTimestamp).getOrNull()
                }
                
                // Process current client response
                if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                    format = findBestFormat(streamPlayerResponse, audioQuality, isNetworkMetered)
                    
                    if (format == null) {
                        continue
                    }
                    
                    streamUrl = extractStreamUrl(format, videoId)
                    if (streamUrl == null) {
                        continue
                    }
                    
                    streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                    if (streamExpiresInSeconds == null) {
                        continue
                    }
                    
                    // Skip validation for last client to ensure we return something
                    if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                        break
                    }
                    
                    // Validate stream URL
                    if (validateStreamUrl(streamUrl)) {
                        break
                    }
                }
            }
            
            // Check if we got everything needed
            if (streamPlayerResponse == null || streamPlayerResponse.playabilityStatus.status != "OK") {
                throw Exception("Playability status: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
            
            // Default to 6 hours (21600 seconds) if expiration time is not provided
            if (streamExpiresInSeconds == null) {
                streamExpiresInSeconds = 21600
            }
            
            if (format == null) {
                throw Exception("Could not find suitable audio format")
            }
            
            if (streamUrl == null) {
                throw Exception("Could not extract stream URL")
            }
            
            PlaybackData(
                audioConfig = audioConfig,
                videoDetails = videoDetails,
                format = format,
                streamUrl = streamUrl,
                streamExpiresInSeconds = streamExpiresInSeconds,
                mimeType = format.mimeType,
                bitrate = format.bitrate,
                contentLength = format.contentLength
            )
        }
    }
    
    /**
     * Finds the best audio format based on quality preference and network conditions.
     */
    private fun findBestFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        isNetworkMetered: Boolean
    ): PlayerResponse.StreamingData.Format? {
        // Filter for audio-only, original quality formats
        val audioFormats = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?: return null
        
        // Select format based on quality preference
        // Opus (audio/webm) codec gets bonus score as it provides better quality
        return audioFormats.maxByOrNull {
            it.bitrate * when (audioQuality) {
                AudioQuality.AUTO -> if (isNetworkMetered) -1 else 1
                AudioQuality.HIGH -> 1
                AudioQuality.LOW -> -1
            } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) // Prefer opus stream
        }
    }
    
    /**
     * Extracts stream URL from format, handling signature deciphering if needed.
     */
    private fun extractStreamUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String
    ): String? {
        return NewPipeUtils.getStreamUrl(format, videoId).getOrNull()
    }
    
    /**
     * Validates if a stream URL is accessible by making a HEAD request.
     */
    private fun validateStreamUrl(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .head()
                .url(url)
                .build()
            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets signature timestamp for video URL deobfuscation.
     */
    private fun getSignatureTimestampOrNull(videoId: String): Int? {
        return NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
    }
    
    /**
     * Simplified method to get just the stream URL without full metadata.
     */
    suspend fun getStreamUrl(
        videoId: String,
        audioQuality: AudioQuality = AudioQuality.HIGH
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            getPlaybackData(videoId, null, audioQuality, false).getOrThrow().streamUrl
        }
    }
}
