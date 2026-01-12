/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous player operations providing
 * success and error handling for Java applications
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.PlayerResult

/**
 * Callback interface for asynchronous player operations.
 * Provides success and error handling for Java applications.
 */
interface PlayerCallback {
    /**
     * Called when the player operation completes successfully.
     * @param result The player information including stream URL and metadata
     */
    fun onSuccess(result: PlayerResult)
    
    /**
     * Called when the player operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}