/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 17-01-2026
 *
 * Description :
 * Java-compatible data class representing next page result
 * with continuation support for paginated content
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of next page result.
 */
data class NextPageResult(
    val items: List<YTItemResult> = emptyList(),
    val continuation: String? = null
)
