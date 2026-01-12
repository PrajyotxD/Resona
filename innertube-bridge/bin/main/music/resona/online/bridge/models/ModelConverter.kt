/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Utility object for converting Innertube models to Java-compatible
 * bridge models handling all data transformation between libraries
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.models

import com.metrolist.innertube.NewPipeUtils
import com.metrolist.innertube.models.*
import com.metrolist.innertube.pages.*

/**
 * Utility object for converting Innertube models to Java-compatible bridge models.
 * Handles all data transformation between the Kotlin Innertube library and Java bridge.
 */
object ModelConverter {
    
    /**
     * Converts an Innertube Artist to ArtistResult.
     */
    fun convertArtist(artist: Artist): ArtistResult {
        return ArtistResult(
            name = artist.name,
            id = artist.id
        )
    }
    
    /**
     * Converts an Innertube Album to AlbumResult.
     */
    fun convertAlbum(album: Album): AlbumResult {
        return AlbumResult(
            name = album.name,
            id = album.id
        )
    }
    
    /**
     * Converts any YTItem to YTItemResult.
     */
    fun convertYTItem(item: YTItem): YTItemResult {
        return when (item) {
            is SongItem -> YTItemResult(
                id = item.id,
                title = item.title,
                thumbnail = item.thumbnail,
                type = "song",
                artists = item.artists.map { convertArtist(it) },
                album = item.album?.let { convertAlbum(it) },
                duration = item.duration,
                explicit = item.explicit,
                shareLink = item.shareLink,
                chartPosition = item.chartPosition,
                chartChange = item.chartChange
            )
            is AlbumItem -> YTItemResult(
                id = item.id,
                title = item.title,
                thumbnail = item.thumbnail,
                type = "album",
                artists = item.artists?.map { convertArtist(it) } ?: emptyList(),
                explicit = item.explicit,
                shareLink = item.shareLink,
                browseId = item.browseId,
                playlistId = item.playlistId
            )
            is ArtistItem -> YTItemResult(
                id = item.id,
                title = item.title,
                thumbnail = item.thumbnail,
                type = "artist",
                explicit = item.explicit,
                shareLink = item.shareLink
            )
            is PlaylistItem -> YTItemResult(
                id = item.id,
                title = item.title,
                thumbnail = item.thumbnail,
                type = "playlist",
                explicit = item.explicit,
                shareLink = item.shareLink
            )
        }
    }
    
    /**
     * Converts a list of YTItems to YTItemResults.
     */
    fun convertYTItems(items: List<YTItem>): List<YTItemResult> {
        return items.map { convertYTItem(it) }
    }
    
    /**
     * Converts HomePage.Section to HomeSectionResult.
     */
    fun convertHomeSection(section: HomePage.Section): HomeSectionResult {
        return HomeSectionResult(
            title = section.title,
            items = convertYTItems(section.items)
        )
    }
    
    /**
     * Converts HomePage to HomePageResult.
     */
    fun convertHomePage(homePage: HomePage): HomePageResult {
        return HomePageResult(
            sections = homePage.sections.map { convertHomeSection(it) },
            chips = homePage.chips?.map { convertChip(it) },
            continuation = homePage.continuation
        )
    }
    
    /**
     * Converts HomePage.Chip to ChipResult.
     */
    private fun convertChip(chip: HomePage.Chip): ChipResult {
        return ChipResult(
            title = chip.title,
            browseId = chip.endpoint?.browseId,
            params = chip.endpoint?.params
        )
    }
    
    /**
     * Basic conversion for search results - will be enhanced when we examine SearchPage structure.
     */
    fun convertSearchResults(items: List<YTItem>, continuation: String? = null): SearchResult {
        return SearchResult(
            items = convertYTItems(items),
            continuation = continuation
        )
    }
    
    /**
     * Basic conversion for album page - will be enhanced when we examine AlbumPage structure.
     */
    fun convertAlbumBasic(album: YTItem, songs: List<YTItem>): AlbumPageResult {
        return AlbumPageResult(
            album = convertYTItem(album),
            songs = convertYTItems(songs)
        )
    }
    
    /**
     * Basic conversion for artist page - will be enhanced when we examine ArtistPage structure.
     */
    fun convertArtistBasic(artist: YTItem, songs: List<YTItem> = emptyList()): ArtistPageResult {
        return ArtistPageResult(
            artist = convertYTItem(artist),
            songs = convertYTItems(songs)
        )
    }
    
    /**
     * Basic conversion for playlist - will be enhanced when we examine PlaylistPage structure.
     */
    fun convertPlaylistBasic(id: String, title: String, songs: List<YTItem> = emptyList()): PlaylistResult {
        return PlaylistResult(
            id = id,
            title = title,
            songs = convertYTItems(songs)
        )
    }
    
    /**
     * Converts PlayerResponse to PlayerResult.
     */
    fun convertPlayerResponse(playerResponse: com.metrolist.innertube.models.response.PlayerResponse): PlayerResult {
        // Enhanced stream URL extraction with multiple fallback strategies
        val streamUrl = extractStreamUrl(playerResponse)
        
        return PlayerResult(
            videoId = playerResponse.videoDetails?.videoId ?: "",
            title = playerResponse.videoDetails?.title ?: "",
            artist = playerResponse.videoDetails?.author,
            thumbnail = playerResponse.videoDetails?.thumbnail?.thumbnails?.lastOrNull()?.url,
            duration = playerResponse.videoDetails?.lengthSeconds?.toIntOrNull(),
            streamUrl = streamUrl,
            isLive = false, // Live content detection not available in current structure
            isPlayable = playerResponse.playabilityStatus?.status == "OK",
            playabilityStatus = playerResponse.playabilityStatus?.status
        )
    }
    
    /**
     * Enhanced stream URL extraction with NewPipe signature deciphering support.
     * This method now properly handles signatureCipher formats.
     */
    private fun extractStreamUrl(
        playerResponse: com.metrolist.innertube.models.response.PlayerResponse
    ): String? {
        val streamingData = playerResponse.streamingData
        val videoId = playerResponse.videoDetails?.videoId
        
        if (streamingData == null) {
            return null
        }
        
        if (videoId == null) {
            return null
        }
        
        // Strategy 1: Look for audio-only adaptive formats (preferred for music)
        streamingData.adaptiveFormats?.let { formats ->
            // Filter for audio-only formats
            val audioFormats = formats.filter { format ->
                format.isAudio && format.isOriginal
            }
            
            if (audioFormats.isNotEmpty()) {
                // Prefer higher quality audio formats (Opus/WebM preferred)
                val bestAudio = audioFormats.maxByOrNull { format ->
                    format.bitrate + (if (format.mimeType.startsWith("audio/webm")) 10240 else 0)
                }
                
                if (bestAudio != null) {
                    // Try to get URL using NewPipe (handles both direct URLs and signature ciphers)
                    val streamUrl = NewPipeUtils.getStreamUrl(bestAudio, videoId).getOrNull()
                    if (streamUrl != null && streamUrl.isNotEmpty()) {
                        return streamUrl
                    }
                }
            }
            
            // Fallback: Try any adaptive format
            val anyFormat = formats.firstOrNull()
            if (anyFormat != null) {
                val streamUrl = NewPipeUtils.getStreamUrl(anyFormat, videoId).getOrNull()
                if (streamUrl != null && streamUrl.isNotEmpty()) {
                    return streamUrl
                }
            }
        }
        
        // Strategy 2: Try regular formats
        streamingData.formats?.let { formats ->
            val firstFormat = formats.firstOrNull()
            if (firstFormat != null) {
                val streamUrl = NewPipeUtils.getStreamUrl(firstFormat, videoId).getOrNull()
                if (streamUrl != null && streamUrl.isNotEmpty()) {
                    return streamUrl
                }
            }
        }
        
        return null
    }
    
    /**
     * Public wrapper for extractStreamUrl to be used by alternative methods
     */
    fun extractStreamUrlPublic(playerResponse: com.metrolist.innertube.models.response.PlayerResponse): String? {
        return extractStreamUrl(playerResponse)
    }

}