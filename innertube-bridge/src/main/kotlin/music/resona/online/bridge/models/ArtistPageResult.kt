/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing an artist page with
 * artist details, songs, albums, singles, and videos
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of an artist page.
 */
data class ArtistPageResult(
    val artist: YTItemResult,
    val description: String? = null,
    val thumbnails: List<String> = emptyList(),
    val shuffleEndpoint: String? = null,
    val radioEndpoint: String? = null,
    val songs: List<YTItemResult> = emptyList(),
    val albums: List<YTItemResult> = emptyList(),
    val singles: List<YTItemResult> = emptyList(),
    val videos: List<YTItemResult> = emptyList()
)