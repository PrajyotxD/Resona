/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 17-01-2026
 *
 * Description :
 * Java callback interface for next page/continuation requests
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.NextPageResult

/**
 * Callback interface for next page operations.
 */
interface NextPageCallback {
    /**
     * Called when next page is successfully loaded.
     */
    fun onSuccess(result: NextPageResult)
    
    /**
     * Called when an error occurs.
     */
    fun onError(error: BridgeException)
}
