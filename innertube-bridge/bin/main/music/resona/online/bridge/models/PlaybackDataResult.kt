/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 19-12-2025
 *
 * Description :
 * Java-compatible data class representing complete playback data
 * including stream URL, format info, and metadata
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of playback data with stream URL.
 * Contains everything needed to play a song/video.
 */
data class PlaybackDataResult(
    // Video identification
    val videoId: String,
    val title: String,
    val artist: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null, // Duration in seconds
    
    // Stream information
    val streamUrl: String,
    val streamExpiresInSeconds: Int,
    
    // Audio format details
    val mimeType: String,
    val bitrate: Int,
    val audioQuality: String? = null,
    val audioSampleRate: Int? = null,
    val audioChannels: Int? = null,
    val contentLength: Long? = null,
    
    // Audio config (normalization)
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null,
    
    // Playability status
    val isPlayable: Boolean = true,
    val playabilityStatus: String? = null
)
