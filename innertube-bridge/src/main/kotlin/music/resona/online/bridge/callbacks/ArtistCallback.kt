/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous artist operations providing
 * success and error handling for Java applications
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.ArtistPageResult

/**
 * Callback interface for asynchronous artist operations.
 * Provides success and error handling for Java applications.
 */
interface ArtistCallback {
    /**
     * Called when the artist operation completes successfully.
     * @param result The artist data containing details and content
     */
    fun onSuccess(result: ArtistPageResult)
    
    /**
     * Called when the artist operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}