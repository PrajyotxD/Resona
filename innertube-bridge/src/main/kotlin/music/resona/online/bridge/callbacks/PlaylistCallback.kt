/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous playlist operations providing
 * success and error handling for Java applications
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.PlaylistResult

/**
 * Callback interface for asynchronous playlist operations.
 * Provides success and error handling for Java applications.
 */
interface PlaylistCallback {
    /**
     * Called when the playlist operation completes successfully.
     * @param result The playlist data containing details and songs
     */
    fun onSuccess(result: PlaylistResult)
    
    /**
     * Called when the playlist operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}