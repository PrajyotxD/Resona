/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Java-compatible data class representing a YouTube Music item
 * serving as base for all content types (songs, albums, artists, playlists)
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

/**
 * Java-compatible representation of a YouTube Music item.
 * This is the base class for all content types (songs, albums, artists, playlists).
 */
data class YTItemResult(
    val id: String,
    val title: String,
    val thumbnail: String?,
    val type: String, // "song", "album", "artist", "playlist"
    val artists: List<ArtistResult> = emptyList(),
    val album: AlbumResult? = null,
    val duration: Int? = null, // Duration in seconds
    val explicit: Boolean = false,
    val shareLink: String? = null,
    val browseId: String? = null, // For albums and artists
    val playlistId: String? = null, // For playlists and albums
    val chartPosition: Int? = null,
    val chartChange: String? = null
)