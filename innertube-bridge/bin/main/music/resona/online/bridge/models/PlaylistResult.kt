/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing a playlist with
 * metadata, songs list, and management properties
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of a playlist.
 */
data class PlaylistResult(
    val id: String,
    val title: String,
    val description: String? = null,
    val thumbnail: String? = null,
    val author: ArtistResult? = null,
    val year: String? = null,
    val songCount: Int = 0,
    val duration: String? = null,
    val songs: List<YTItemResult> = emptyList(),
    val continuation: String? = null,
    val editable: Boolean = false
)