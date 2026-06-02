package com.example.tenant_landlorddisputedocumenter.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collect a [Flow] while the activity is at least [STARTED]. Cancels on STOP and re-collects on
 * the next START. Use everywhere instead of GlobalScope or manual lifecycle wiring.
 */
inline fun <T> AppCompatActivity.collectOnStart(
    flow: Flow<T>,
    crossinline block: suspend (T) -> Unit,
): Job = lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        flow.collect { block(it) }
    }
}
