package com.senzaproject.gamespace.core.monitoring

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Collects the numbers picked by [selector] into a list that keeps only the newest [capacity] values. */
fun <T> Flow<T>.historyOf(capacity: Int, selector: (T) -> Float?): Flow<List<Float>> {
    val upstream = this
    return flow {
        var history = emptyList<Float>()
        upstream.collect { value ->
            val number = selector(value)
            if (number != null) {
                history = appendCapped(history, number, capacity)
                emit(history)
            }
        }
    }
}
