/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous operations that return boolean values
 * such as like/unlike operations providing success and error handling
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException

/**
 * Callback interface for asynchronous operations that return boolean values.
 * Used for operations like like/unlike, subscribe/unsubscribe that return success status.
 */
interface BooleanCallback {
    /**
     * Called when the operation completes successfully.
     * @param success True if the operation was successful, false otherwise
     */
    fun onSuccess(success: Boolean)
    
    /**
     * Called when the operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}