package com.example.tenant_landlorddisputedocumenter.ui

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyFlowPolicy
import kotlinx.coroutines.launch

/** Verifies the current user may access a property screen; navigates up on denial. */
fun Fragment.guardPropertyAccess(
    propertyId: String,
    accessCheck: (Property?, String?) -> PropertyFlowPolicy.Access,
    onGranted: (Property) -> Unit,
) {
    val container = (requireContext().applicationContext as ProofNestApplication).container
    viewLifecycleOwner.lifecycleScope.launch {
        val uid = container.authRepository.currentUserId.value
        val property = container.propertyRepository.getProperty(propertyId)
        val access = accessCheck(property, uid)
        if (!access.allowed || property == null) {
            Toast.makeText(
                requireContext(),
                access.denialMessage ?: "Access denied.",
                Toast.LENGTH_LONG,
            ).show()
            findNavController().navigateUp()
            return@launch
        }
        onGranted(property)
    }
}
