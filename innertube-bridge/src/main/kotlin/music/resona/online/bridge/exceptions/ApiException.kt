/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Exception thrown when YouTube API returns an error response
 * including invalid requests, quota exceeded, and API-specific errors
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.exceptions

/**
 * Exception thrown when YouTube API returns an error response.
 * This includes invalid requests, quota exceeded, and API-specific errors.
 */
class ApiException(message: String, cause: Throwable? = null) : BridgeException(message, cause)