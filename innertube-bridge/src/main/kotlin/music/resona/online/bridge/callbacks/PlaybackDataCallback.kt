/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 19-12-2025
 *
 * Description :
 * Callback interface for asynchronous playback data operations
 * providing success and error handling for Java applications
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.PlaybackDataResult

/**
 * Callback interface for asynchronous playback data operations.
 * Provides success and error handling for streaming URL extraction.
 */
interface PlaybackDataCallback {
    /**
     * Called when the playback data extraction completes successfully.
     * @param result The playback data containing stream URL and metadata
     */
    fun onSuccess(result: PlaybackDataResult)
    
    /**
     * Called when the playback data extraction fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}
