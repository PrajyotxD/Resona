/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Exception thrown when network-related errors occur during Innertube operations
 * including connection timeouts, DNS failures, and HTTP errors
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.exceptions

/**
 * Exception thrown when network-related errors occur during Innertube operations.
 * This includes connection timeouts, DNS resolution failures, and HTTP errors.
 */
class NetworkException(message: String, cause: Throwable? = null) : BridgeException(message, cause)