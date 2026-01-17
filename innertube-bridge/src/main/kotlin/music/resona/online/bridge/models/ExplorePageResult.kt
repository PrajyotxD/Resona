/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 17-01-2026
 *
 * Description :
 * Java-compatible data class representing explore page with
 * sections and items for discovering new content
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of explore page.
 */
data class ExplorePageResult(
    val sections: List<HomeSectionResult> = emptyList()
)
