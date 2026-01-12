/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing the home page with
 * sections list, chips (filters/moods), and optional continuation token
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of the home page.
 */
data class HomePageResult(
    val sections: List<HomeSectionResult>,
    val chips: List<ChipResult>? = null,
    val continuation: String? = null
)

/**
 * Represents a filter chip (mood/genre) on the home page.
 */
data class ChipResult(
    val title: String,
    val browseId: String?,
    val params: String?
)