/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 17-01-2026
 *
 * Description :
 * Java callback interface for explore page requests
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.callbacks

import music.resona.online.bridge.exceptions.BridgeException
import music.resona.online.bridge.models.ExplorePageResult

/**
 * Callback interface for explore page operations.
 */
interface ExplorePageCallback {
    /**
     * Called when explore page is successfully loaded.
     */
    fun onSuccess(result: ExplorePageResult)
    
    /**
     * Called when an error occurs.
     */
    fun onError(error: BridgeException)
}
