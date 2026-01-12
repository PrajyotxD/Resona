/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Base exception class for all bridge-related errors providing
 * Java-compatible exception handling for Innertube operations
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.exceptions

/**
 * Base exception class for all bridge-related errors.
 * Provides Java-compatible exception handling for Innertube operations.
 */
open class BridgeException(message: String, cause: Throwable? = null) : Exception(message, cause)