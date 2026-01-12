/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing a home page section
 * with title and list of items for display
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of a home page section.
 */
data class HomeSectionResult(
    val title: String,
    val items: List<YTItemResult>
)