package com.praval.f1calendar.data.remote

import com.praval.f1calendar.core.Res
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs a network call and turns the failure modes that actually happen against Jolpica into
 * messages worth showing a user. Cancellation is rethrown so coroutine scoping keeps working.
 */
suspend fun <T> apiCall(block: suspend () -> T): Res<T> = try {
    Res.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    Res.Error(
        when (e.code()) {
            404 -> "No data published for that season yet."
            429 -> "Too many requests to the F1 data service. Try again in a minute."
            in 500..599 -> "The F1 data service is having trouble. Try again shortly."
            else -> "Request failed (HTTP ${e.code()})."
        },
        e,
    )
} catch (e: IOException) {
    Res.Error("Can't reach the F1 data service. Check your connection.", e)
} catch (e: Exception) {
    Res.Error(e.message ?: "Something went wrong.", e)
}
