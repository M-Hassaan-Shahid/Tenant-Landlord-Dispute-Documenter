package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.navigation.ReviewSignFragmentArgs
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentReviewSignBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReviewSignFragment : Fragment() {

    private var _binding: FragmentReviewSignBinding? = null
    private val binding get() = _binding!!
    private val args: ReviewSignFragmentArgs by lazy {
        ReviewSignFragmentArgs.fromBundle(requireArguments())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewSignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        val inspectionRepo = appContainer.inspectionRepository
        val propertyRepo = appContainer.propertyRepository
        val authRepo = appContainer.authRepository

        val propertyId = args.propertyId
        val phase = if (args.phase == "MOVE_OUT") InspectionPhase.MOVE_OUT else InspectionPhase.MOVE_IN

        binding.toolbar.title = if (phase == InspectionPhase.MOVE_OUT) "Sign Move-Out Record" else "Sign Move-In Record"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { inspectionRepo.syncForProperty(propertyId) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val uid = authRepo.currentUserId.value ?: return@repeatOnLifecycle
                inspectionRepo.observeSignatures(propertyId, phase).collect { signatures ->
                    val alreadySigned = signatures.any { it.signerUid == uid }
                    binding.signaturePad.isEnabled = !alreadySigned
                    binding.buttonClearSignature.isEnabled = !alreadySigned
                    binding.buttonSubmit.isEnabled = !alreadySigned
                    if (alreadySigned) {
                        binding.buttonSubmit.text = getString(R.string.signature_locked)
                    }
                }
            }
        }

        binding.buttonClearSignature.setOnClickListener {
            binding.signaturePad.clear()
        }

        binding.buttonSubmit.setOnClickListener {
            if (!binding.signaturePad.hasInk) {
                Toast.makeText(requireContext(), "Please draw your signature first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uid = authRepo.currentUserId.value ?: run {
                Toast.makeText(requireContext(), "Not signed in.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val role = authRepo.currentUserRole.value ?: run {
                Toast.makeText(requireContext(), "Could not determine user role.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val signatureBase64 = binding.signaturePad.toPngBase64()
            binding.buttonSubmit.isEnabled = false

            viewLifecycleOwner.lifecycleScope.launch {
                runCatching {
                    val property = propertyRepo.observeProperty(propertyId).first()
                        ?: error("Property not found")

                    val notifyRecipientUid = when (uid) {
                        property.landlordId -> property.tenantId
                        property.tenantId -> property.landlordId
                        else -> null
                    }

                    inspectionRepo.saveSignature(
                        propertyId, uid, role, phase, signatureBase64, notifyRecipientUid,
                    )

                    if (property.tenantId != null) {
                        val fullySigned = inspectionRepo.isPhaseSignedByBoth(
                            propertyId = propertyId,
                            phase = phase,
                            landlordUid = property.landlordId,
                            tenantUid = property.tenantId
                        )
                        if (fullySigned) {
                            val newStatus = when (phase) {
                                InspectionPhase.MOVE_IN -> PropertyStatus.OCCUPIED
                                InspectionPhase.MOVE_OUT -> PropertyStatus.CLOSED
                            }
                            propertyRepo.updateStatus(propertyId, newStatus)
                        }
                    }
                }.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        if (phase == InspectionPhase.MOVE_IN) "Move-in record signed!" else "Move-out record signed!",
                        Toast.LENGTH_LONG
                    ).show()
                    // Pop back to Dashboard, clearing the inspection back-stack
                    findNavController().navigate(R.id.action_review_sign_to_dashboard)
                }.onFailure { e ->
                    binding.buttonSubmit.isEnabled = true
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Populate the summary list of items before signing
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                inspectionRepo.observeAllItems(propertyId).collect { items ->
                    val summary = items.joinToString("\n") { item ->
                        val rating = if (phase == InspectionPhase.MOVE_IN) item.moveInRating else item.moveOutRating
                        "• ${item.name}: ${rating?.name ?: "Not rated"}"
                    }
                    binding.textSummary.text = summary.ifBlank { "No items found for this inspection." }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
