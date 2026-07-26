package com.praval.f1calendar.core

/**
 * One-shot outcome of a repository operation.
 *
 * Cached data is served straight from Room as a [kotlinx.coroutines.flow.Flow], so screens stay
 * populated offline; this type only describes how the *refresh* that sits alongside it went.
 */
sealed interface Res<out T> {
    data object Loading : Res<Nothing>
    data class Success<out T>(val data: T) : Res<T>
    data class Error(val message: String, val cause: Throwable? = null) : Res<Nothing>
}

inline fun <T> Res<T>.onError(block: (String) -> Unit): Res<T> {
    if (this is Res.Error) block(message)
    return this
}

fun <T> Res<T>.dataOrNull(): T? = (this as? Res.Success)?.data

/** Inline so [transform] can suspend — repositories persist inside it. */
inline fun <T, R> Res<T>.map(transform: (T) -> R): Res<R> = when (this) {
    is Res.Success -> Res.Success(transform(data))
    is Res.Error -> this
    Res.Loading -> Res.Loading
}
