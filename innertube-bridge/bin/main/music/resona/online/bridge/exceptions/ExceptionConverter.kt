/***
 * ------------------------------------------------------------
 * Author      : PrajyotxD
 * Created On  : 18-12-2025
 *
 * Description :
 * Utility object for converting Kotlin/Ktor exceptions to Java-compatible
 * bridge exceptions while preserving error details and context
 *
 * Module      : innertube-bridge
 * ------------------------------------------------------------
 */
package music.resona.online.bridge.exceptions

import io.ktor.client.plugins.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Utility object for converting Kotlin/Ktor exceptions to Java-compatible bridge exceptions.
 * Preserves error details and provides meaningful error messages for Java callers.
 */
object ExceptionConverter {
    
    /**
     * Converts any throwable to an appropriate BridgeException.
     * This is the main entry point for exception conversion in the bridge.
     */
    fun convertToBridgeException(throwable: Throwable): BridgeException {
        return when (throwable) {
            // Already a bridge exception - pass through
            is BridgeException -> throwable
            
            // Coroutine cancellation and timeouts
            is TimeoutCancellationException -> TimeoutException(
                "Operation timed out: ${throwable.message ?: "Request exceeded timeout limit"}", 
                throwable
            )
            is CancellationException -> TimeoutException(
                "Operation was cancelled: ${throwable.message ?: "Request was cancelled"}", 
                throwable
            )
            
            // Network-related exceptions
            is ConnectException -> NetworkException(
                "Failed to connect to server: ${throwable.message ?: "Connection refused"}", 
                throwable
            )
            is UnknownHostException -> NetworkException(
                "Unable to resolve host: ${throwable.message ?: "DNS resolution failed"}", 
                throwable
            )
            is SocketTimeoutException -> NetworkException(
                "Network timeout: ${throwable.message ?: "Socket operation timed out"}", 
                throwable
            )
            
            // Ktor client exceptions
            is ClientRequestException -> when (throwable.response.status.value) {
                401 -> AuthenticationException(
                    "Authentication failed: ${throwable.message ?: "Invalid credentials"}", 
                    throwable
                )
                403 -> AuthenticationException(
                    "Access forbidden: ${throwable.message ?: "Insufficient permissions"}", 
                    throwable
                )
                in 400..499 -> ApiException(
                    "Client error (${throwable.response.status.value}): ${throwable.message ?: "Bad request"}", 
                    throwable
                )
                else -> ApiException(
                    "HTTP error (${throwable.response.status.value}): ${throwable.message ?: "Request failed"}", 
                    throwable
                )
            }
            is ServerResponseException -> ApiException(
                "Server error (${throwable.response.status.value}): ${throwable.message ?: "Internal server error"}", 
                throwable
            )
            is RedirectResponseException -> NetworkException(
                "Redirect error (${throwable.response.status.value}): ${throwable.message ?: "Too many redirects"}", 
                throwable
            )
            
            // Generic network exceptions from Ktor
            is ResponseException -> NetworkException(
                "Network response error: ${throwable.message ?: "Invalid response received"}", 
                throwable
            )
            
            // Fallback for any other exception
            else -> BridgeException(
                "Unexpected error: ${throwable.message ?: throwable.javaClass.simpleName}", 
                throwable
            )
        }
    }
    
    /**
     * Wraps a suspend function call and converts any exceptions to BridgeExceptions.
     * This is a utility function for use in bridge methods.
     */
    inline fun <T> convertExceptions(block: () -> T): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            throw convertToBridgeException(throwable)
        }
    }
    
    /**
     * Async version of convertExceptions for use with coroutines.
     */
    suspend inline fun <T> convertExceptionsAsync(crossinline block: suspend () -> T): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            throw convertToBridgeException(throwable)
        }
    }
}