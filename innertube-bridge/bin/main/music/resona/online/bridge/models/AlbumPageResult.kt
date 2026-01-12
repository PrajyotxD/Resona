/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing an album page with
 * album details, songs list, and metadata
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of an album page.
 */
data class AlbumPageResult(
    val album: YTItemResult,
    val songs: List<YTItemResult>,
    val description: String? = null,
    val thumbnails: List<String> = emptyList(),
    val year: String? = null,
    val duration: String? = null
)