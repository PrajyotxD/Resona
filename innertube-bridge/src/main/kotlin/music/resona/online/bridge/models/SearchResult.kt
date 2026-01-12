/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing search results with
 * items list and optional continuation token for pagination
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of search results.
 */
data class SearchResult(
    val items: List<YTItemResult>,
    val continuation: String? = null
)