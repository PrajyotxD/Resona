/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous search operations providing
 * success and error handling for Java applications
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.SearchResult

/**
 * Callback interface for asynchronous search operations.
 * Provides success and error handling for Java applications.
 */
interface SearchCallback {
    /**
     * Called when the search operation completes successfully.
     * @param result The search results containing items and continuation token
     */
    fun onSuccess(result: SearchResult)
    
    /**
     * Called when the search operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}