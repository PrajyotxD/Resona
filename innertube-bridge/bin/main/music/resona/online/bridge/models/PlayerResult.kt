/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing player information
 * including video details, stream URL, and playability status
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of player information.
 */
data class PlayerResult(
    val videoId: String,
    val title: String,
    val artist: String? = null,
    val thumbnail: String? = null,
    val duration: Int? = null, // Duration in seconds
    val streamUrl: String? = null,
    val isLive: Boolean = false,
    val isPlayable: Boolean = true,
    val playabilityStatus: String? = null
)