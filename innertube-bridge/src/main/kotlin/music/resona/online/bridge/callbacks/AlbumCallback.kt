/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous album operations providing
 * success and error handling for Java applications
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.AlbumPageResult

/**
 * Callback interface for asynchronous album operations.
 * Provides success and error handling for Java applications.
 */
interface AlbumCallback {
    /**
     * Called when the album operation completes successfully.
     * @param result The album data containing details and songs
     */
    fun onSuccess(result: AlbumPageResult)
    
    /**
     * Called when the album operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}