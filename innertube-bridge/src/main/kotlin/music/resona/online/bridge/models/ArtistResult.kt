/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing an artist with
 * name and optional ID for bridge operations
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of an artist.
 */
data class ArtistResult(
    val name: String,
    val id: String?
)