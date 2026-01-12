/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Callback interface for asynchronous operations that return strings
 * such as playlist creation providing success and error handling
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException

/**
 * Callback interface for asynchronous operations that return strings.
 * Used for operations like playlist creation that return IDs.
 */
interface StringCallback {
    /**
     * Called when the operation completes successfully.
     * @param result The string result (e.g., playlist ID)
     */
    fun onSuccess(result: String)
    
    /**
     * Called when the operation fails.
     * @param error The exception that caused the failure
     */
    fun onError(error: BridgeException)
}