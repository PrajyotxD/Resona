/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Exception thrown when operations timeout or are cancelled
 * including coroutine cancellation and request timeouts
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.exceptions

/**
 * Exception thrown when operations timeout or are cancelled.
 * This includes coroutine cancellation and request timeouts.
 */
class TimeoutException(message: String, cause: Throwable? = null) : BridgeException(message, cause)