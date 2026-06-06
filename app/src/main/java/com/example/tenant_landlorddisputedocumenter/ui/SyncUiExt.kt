package com.example.tenant_landlorddisputedocumenter.ui

import androidx.fragment.app.Fragment
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import kotlinx.coroutines.launch

/** Refresh Firestore → Room in the app IO scope without blocking the UI thread. */
fun Fragment.refreshPropertyInBackground(propertyId: String, force: Boolean = false) {
    val container = (requireContext().applicationContext as ProofNestApplication).container
    container.repositoryScope.launch {
        runCatching { container.syncCoordinator.refreshPropertyData(propertyId, force) }
    }
}
