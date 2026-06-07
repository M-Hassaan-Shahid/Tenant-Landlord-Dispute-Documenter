package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenant_landlorddisputedocumenter.navigation.ReviewSignFragmentArgs
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentReviewSignBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.ui.dispute.showDisputeItemPicker
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyFlowPolicy
import com.example.tenant_landlorddisputedocumenter.ui.guardPropertyAccess
import com.example.tenant_landlorddisputedocumenter.ui.refreshPropertyInBackground
import com.example.tenant_landlorddisputedocumenter.ui.showPhotoViewer
import kotlinx.coroutines.flow.combine
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
        savedInstanceState: Bundle?,
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

        guardPropertyAccess(propertyId, { property, uid ->
            PropertyFlowPolicy.reviewSignAccess(property, uid, phase)
        }) { }

        binding.toolbar.title = if (phase == InspectionPhase.MOVE_OUT) {
            getString(R.string.review_sign_move_out)
        } else {
            getString(R.string.review_sign_move_in)
        }
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.signaturePad.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        val reviewAdapter = ReviewItemAdapter(
            phase = phase,
            scope = viewLifecycleOwner.lifecycleScope,
            loadPhotos = { ids -> inspectionRepo.getPhotos(ids) },
            onPhotoClick = { uri -> showPhotoViewer(uri) },
        )
        binding.recyclerReviewItems.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerReviewItems.adapter = reviewAdapter

        refreshPropertyInBackground(propertyId)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                propertyRepo.observeProperty(propertyId).collect { property ->
                    if (property == null) return@collect
                    val uid = authRepo.currentUserId.value
                    val notReady = when (phase) {
                        InspectionPhase.MOVE_IN ->
                            property.moveInInspectionSubmittedAtMillis == null
                        InspectionPhase.MOVE_OUT ->
                            property.moveOutInspectionSubmittedAtMillis == null
                    }
                    if (notReady) {
                        val message = when (phase) {
                            InspectionPhase.MOVE_IN -> getString(R.string.review_sign_not_ready)
                            InspectionPhase.MOVE_OUT -> getString(R.string.review_sign_move_out_not_ready)
                        }
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        findNavController().navigateUp()
                        return@collect
                    }
                    binding.textReviewIntro.setText(
                        when {
                            property.landlordId == uid -> R.string.review_sign_landlord_intro
                            property.tenantId == uid -> R.string.review_sign_tenant_intro
                            else -> R.string.review_sign_intro
                        },
                    )
                    val showDispute = property.tenantId == uid && when (phase) {
                        InspectionPhase.MOVE_IN ->
                            property.moveInInspectionSubmittedAtMillis != null
                        InspectionPhase.MOVE_OUT ->
                            property.moveOutInspectionSubmittedAtMillis != null
                    }
                    binding.buttonDisputeItem.visibility =
                        if (showDispute) View.VISIBLE else View.GONE
                }
            }
        }

        binding.buttonDisputeItem.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val items = inspectionRepo.observeAllItems(propertyId).first()
                showDisputeItemPicker(propertyId, items) {
                    Toast.makeText(requireContext(), "No checklist items to dispute.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    propertyRepo.observeProperty(propertyId),
                    inspectionRepo.observeSignatures(propertyId, phase),
                    authRepo.currentUserId,
                ) { property, signatures, uid ->
                    Triple(property, signatures, uid)
                }.collect { (property, signatures, uid) ->
                    if (property == null || uid == null) return@collect
                    val alreadySigned = signatures.any { it.signerUid == uid }
                    val fullySigned = property.tenantId?.let { tenantId ->
                        signatures.any { it.signerUid == property.landlordId } &&
                            signatures.any { it.signerUid == tenantId }
                    } ?: false

                    binding.signaturePad.isEnabled = !alreadySigned
                    binding.buttonClearSignature.isEnabled = !alreadySigned
                    binding.buttonSubmit.isEnabled = !alreadySigned
                    binding.buttonDisputeItem.isEnabled = !alreadySigned

                    binding.buttonSubmit.text = when {
                        alreadySigned && fullySigned -> getString(R.string.signature_record_locked)
                        alreadySigned -> getString(R.string.signature_you_signed)
                        else -> getString(R.string.confirm_signature)
                    }
                }
            }
        }

        binding.buttonClearSignature.setOnClickListener {
            binding.signaturePad.clear()
        }

        binding.buttonSubmit.setOnClickListener {
            if (!binding.signaturePad.hasInk) {
                Toast.makeText(requireContext(), R.string.signature_draw_first, Toast.LENGTH_SHORT).show()
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
            binding.buttonSubmit.text = getString(R.string.signature_submitting)

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
                    inspectionRepo.syncSignaturesForProperty(propertyId)

                    if (property.tenantId != null) {
                        val fullySigned = inspectionRepo.isPhaseSignedByBoth(
                            propertyId = propertyId,
                            phase = phase,
                            landlordUid = property.landlordId,
                            tenantUid = property.tenantId,
                        )
                        if (fullySigned) {
                            val newStatus = when (phase) {
                                InspectionPhase.MOVE_IN -> PropertyStatus.OCCUPIED
                                InspectionPhase.MOVE_OUT -> PropertyStatus.CLOSED
                            }
                            when (val statusResult = propertyRepo.updateStatus(propertyId, newStatus, uid)) {
                                is Outcome.Failure ->
                                    error(statusResult.userMessage ?: "Could not update property status.")
                                is Outcome.Success -> Unit
                            }
                        }
                    }
                }.onSuccess {
                    Toast.makeText(
                        requireContext(),
                        if (phase == InspectionPhase.MOVE_IN) {
                            getString(R.string.signature_move_in_saved)
                        } else {
                            getString(R.string.signature_move_out_saved)
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                    findNavController().navigate(R.id.action_review_sign_to_dashboard)
                }.onFailure { e ->
                    binding.buttonSubmit.isEnabled = true
                    binding.buttonSubmit.text = getString(R.string.confirm_signature)
                    Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    inspectionRepo.observeAllItems(propertyId),
                    inspectionRepo.observeRooms(propertyId),
                ) { items, rooms ->
                    val roomNames = rooms.associate { it.id to it.name }
                    items.map { item ->
                        ReviewItemRow(item, roomNames[item.roomId] ?: "Room")
                    }
                }.collect { rows ->
                    reviewAdapter.submitList(rows)
                    val empty = rows.isEmpty()
                    binding.textReviewEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.recyclerReviewItems.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
