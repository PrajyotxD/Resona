/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous home page operations providing
 * success and error handling for Java applications
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.HomePageResult

/**
 * Callback interface for asynchronous home page operations.
 * Provides success and error handling for Java applications.
 */
interface HomePageCallback {
    /**
     * Called when the home page operation completes successfully.
     * @param result The home page data containing sections and continuation token
     */
    fun onSuccess(result: HomePageResult)
    
    /**
     * Called when the home page operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}