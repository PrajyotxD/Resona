/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Exception thrown when authentication-related errors occur
 * including invalid credentials, expired sessions, and authorization failures
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.exceptions

/**
 * Exception thrown when authentication-related errors occur.
 * This includes invalid credentials, expired sessions, and authorization failures.
 */
class AuthenticationException(message: String, cause: Throwable? = null) : BridgeException(message, cause)