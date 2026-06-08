package com.example.tenant_landlorddisputedocumenter.ui.property

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.navigation.PropertyDetailsFragmentArgs
import com.example.tenant_landlorddisputedocumenter.navigation.PropertyDetailsFragmentDirections
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentPropertyDetailsBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.Signature
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyFlowPolicy
import com.example.tenant_landlorddisputedocumenter.ui.fadeInSlideUp
import com.example.tenant_landlorddisputedocumenter.ui.guardPropertyAccess
import com.example.tenant_landlorddisputedocumenter.ui.navigateAnimated
import com.example.tenant_landlorddisputedocumenter.ui.refreshPropertyInBackground
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyPrimaryAction
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyRoleUi
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyStatusUi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PropertyDetailsFragment : Fragment() {

    private var _binding: FragmentPropertyDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PropertyDetailsViewModel
    private var lastActionsKey: String? = null
    private val args: PropertyDetailsFragmentArgs by lazy {
        PropertyDetailsFragmentArgs.fromBundle(requireArguments())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPropertyDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, PropertyDetailsViewModelFactory(appContainer))[PropertyDetailsViewModel::class.java]

        val propertyId = args.propertyId
        guardPropertyAccess(propertyId, PropertyFlowPolicy::memberAccess) { }
        viewModel.loadProperty(propertyId)

        refreshPropertyInBackground(propertyId)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.buttonRoomSetup.setOnClickListener {
            findNavController().navigateAnimated(
                PropertyDetailsFragmentDirections.actionPropertyDetailsToRoomSetup(propertyId),
            )
        }

        binding.buttonInspection.setOnClickListener {
            navigateToInspection(propertyId, currentInspectionPhase())
        }

        binding.buttonReviewSign.setOnClickListener {
            navigateToReviewSign(propertyId, currentInspectionPhase())
        }

        binding.buttonStartMoveOut.setOnClickListener {
            val uid = appContainer.authRepository.currentUserId.value ?: return@setOnClickListener
            viewModel.startMoveOut(uid)
        }

        binding.buttonCompare.setOnClickListener {
            findNavController().navigateAnimated(
                PropertyDetailsFragmentDirections.actionPropertyDetailsToCompare(propertyId),
            )
        }

        binding.buttonReport.setOnClickListener {
            findNavController().navigateAnimated(
                PropertyDetailsFragmentDirections.actionPropertyDetailsToReport(propertyId),
            )
        }

        binding.buttonDisputes.setOnClickListener {
            findNavController().navigateAnimated(
                PropertyDetailsFragmentDirections.actionPropertyDetailsToDisputesList(propertyId),
            )
        }

        binding.buttonApprove.setOnClickListener { viewModel.approveTenant() }
        binding.buttonReject.setOnClickListener { viewModel.rejectTenant() }

        binding.buttonCopyInvite.setOnClickListener {
            val code = binding.textInviteCode.text?.toString().orEmpty()
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("invite_code", code))
            binding.buttonCopyInvite.text = getString(R.string.invite_copied)
            binding.buttonCopyInvite.postDelayed({
                if (_binding != null) {
                    binding.buttonCopyInvite.text = getString(R.string.invite_copy)
                }
            }, 1600L)
        }

        val inspectionRepo = appContainer.inspectionRepository
        val authRepo = appContainer.authRepository

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    combine(
                        viewModel.property,
                        inspectionRepo.observeSignatures(propertyId, InspectionPhase.MOVE_IN),
                        inspectionRepo.observeSignatures(propertyId, InspectionPhase.MOVE_OUT),
                        authRepo.currentUserId,
                        inspectionRepo.observeRooms(propertyId).map { it.isNotEmpty() },
                    ) { property, moveInSigs, moveOutSigs, uid, hasRooms ->
                        RoleUiInput(property, moveInSigs, moveOutSigs, uid, hasRooms, hasDisputes = false)
                    },
                    appContainer.disputeRepository.observeForProperty(propertyId).map { it.isNotEmpty() },
                ) { input, hasDisputes ->
                    input.copy(hasDisputes = hasDisputes)
                }.collect { input ->
                    val property = input.property ?: return@collect
                    renderPropertyBasics(property, input.uid)
                    renderRoleActions(
                        property,
                        input.uid,
                        input.moveInSigs,
                        input.moveOutSigs,
                        input.hasRooms,
                        input.hasDisputes,
                    )
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.property,
                    viewModel.partyNames,
                    authRepo.currentUserId,
                ) { property, names, uid -> Triple(property, names, uid) }
                    .collect { (property, names, uid) ->
                        renderParties(property, names, uid)
                    }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingTenant.collect { tenant ->
                    val name = tenant?.displayName?.takeIf { it.isNotBlank() }
                    binding.textApprovalTenant.text = when {
                        name != null && tenant.email.isNotBlank() ->
                            getString(R.string.approval_requested_by_email, name, tenant.email)
                        name != null -> getString(R.string.approval_requested_by, name)
                        else -> getString(
                            R.string.approval_requested_by,
                            getString(R.string.approval_requested_unknown),
                        )
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val busy = state.isLoading
                    binding.buttonApprove.isEnabled = !busy
                    binding.buttonReject.isEnabled = !busy
                    binding.buttonStartMoveOut.isEnabled = !busy
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                    state.actionSuccess?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun shouldShowInviteCard(isLandlord: Boolean, property: Property): Boolean {
        if (!isLandlord) return false
        return when (property.status) {
            PropertyStatus.PENDING, PropertyStatus.REJECTED -> true
            else -> false
        }
    }

    private fun renderPropertyBasics(property: Property, currentUser: String?) {
        binding.textAddress.text = property.address
        PropertyStatusUi.apply(binding.chipStatus, property.status)
        binding.textRent.text = "PKR ${property.rent.toInt()}"
        binding.textDeposit.text = "PKR ${property.deposit.toInt()}"
        binding.textInviteCode.text = property.inviteCode

        val start = com.example.tenant_landlorddisputedocumenter.util.DateUtils.formatReadable(property.leaseStartMillis)
        val end = com.example.tenant_landlorddisputedocumenter.util.DateUtils.formatReadable(property.leaseEndMillis)
        binding.textLeaseDates.text = "$start - $end"

        val isLandlord = property.landlordId == currentUser
        binding.cardInvite.visibility =
            if (shouldShowInviteCard(isLandlord, property)) View.VISIBLE else View.GONE

        if (property.status == PropertyStatus.CLOSED) {
            binding.textFinancialsTitle.setText(R.string.label_financials)
            binding.textFinancialsSubtitle.visibility = View.VISIBLE
            binding.textFinancialsSubtitle.setText(R.string.financials_closed_subtitle)
        } else {
            binding.textFinancialsTitle.setText(R.string.label_financials)
            binding.textFinancialsSubtitle.visibility = View.GONE
        }
        binding.cardApproval.visibility =
            if (isLandlord && property.status == PropertyStatus.PENDING_APPROVAL) View.VISIBLE else View.GONE

        val isTenant = property.tenantId == currentUser
        binding.cardTenantPending.visibility =
            if (isTenant && property.status == PropertyStatus.PENDING_APPROVAL) View.VISIBLE else View.GONE
    }

    private fun renderParties(property: Property?, names: Map<String, String>, currentUser: String?) {
        if (property == null) {
            binding.cardParties.visibility = View.GONE
            return
        }
        // Only worth showing once a tenant exists; before that the invite card covers it.
        val hasTenant = property.tenantId != null
        if (!hasTenant) {
            binding.cardParties.visibility = View.GONE
            return
        }
        binding.cardParties.visibility = View.VISIBLE

        val landlordName = names[property.landlordId]?.takeIf { it.isNotBlank() }
            ?: getString(R.string.party_unknown)
        binding.textPartyLandlord.text = if (property.landlordId == currentUser) {
            getString(R.string.party_landlord_you, landlordName)
        } else {
            getString(R.string.party_landlord, landlordName)
        }

        val tenantId = property.tenantId
        if (tenantId == null) {
            binding.textPartyTenant.text = getString(R.string.party_tenant_none)
        } else {
            val tenantName = names[tenantId]?.takeIf { it.isNotBlank() }
                ?: getString(R.string.party_unknown)
            binding.textPartyTenant.text = if (tenantId == currentUser) {
                getString(R.string.party_tenant_you, tenantName)
            } else {
                getString(R.string.party_tenant, tenantName)
            }
        }
    }

    private fun renderRoleActions(
        property: Property,
        currentUser: String?,
        moveInSigs: List<Signature>,
        moveOutSigs: List<Signature>,
        hasRooms: Boolean,
        hasDisputes: Boolean,
    ) {
        val inspectionPhase = when (property.status) {
            PropertyStatus.ACTIVE -> InspectionPhase.MOVE_IN
            PropertyStatus.MOVE_OUT -> InspectionPhase.MOVE_OUT
            else -> viewModel.inspectionPhase.value
        }
        viewModel.setInspectionPhase(inspectionPhase)

        val roleState = PropertyRoleUi.resolve(
            property,
            currentUser,
            moveInSigs,
            moveOutSigs,
            hasRooms,
            hasDisputes,
        )
        if (roleState == null) {
            binding.cardRoleBanner.visibility = View.GONE
            hideAllActionButtons()
            return
        }

        binding.cardRoleBanner.visibility = View.VISIBLE
        binding.textRoleBannerTitle.setText(roleState.roleBannerTitleRes)
        binding.textRoleBannerBody.setText(roleState.roleBannerBodyRes)

        val actions = roleState.actions
        binding.buttonRoomSetup.visibility =
            if (PropertyPrimaryAction.ROOM_SETUP in actions) View.VISIBLE else View.GONE

        binding.buttonInspection.visibility =
            if (PropertyPrimaryAction.DOCUMENT_MOVE_IN in actions ||
                PropertyPrimaryAction.DOCUMENT_MOVE_OUT in actions
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        binding.buttonInspection.text = when {
            PropertyPrimaryAction.DOCUMENT_MOVE_OUT in actions ->
                getString(R.string.tile_document_move_out)
            else -> getString(R.string.tile_document_move_in)
        }

        binding.buttonReviewSign.visibility =
            if (PropertyPrimaryAction.REVIEW_SIGN_MOVE_IN in actions ||
                PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT in actions
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        binding.buttonReviewSign.text = when {
            PropertyPrimaryAction.REVIEW_SIGN_MOVE_OUT in actions ->
                getString(R.string.tile_review_sign_move_out)
            else -> getString(R.string.tile_review_sign_move_in)
        }

        binding.cardWaitingLandlord.visibility =
            if (PropertyPrimaryAction.WAITING_LANDLORD_DOCUMENT in actions) View.VISIBLE else View.GONE
        roleState.waitingCardTitleRes?.let { binding.textWaitingTitle.setText(it) }
        roleState.waitingCardBodyRes?.let { binding.textWaitingBody.setText(it) }

        binding.buttonStartMoveOut.visibility =
            if (PropertyPrimaryAction.START_MOVE_OUT in actions) View.VISIBLE else View.GONE

        binding.buttonCompare.visibility =
            if (PropertyPrimaryAction.VIEW_COMPARE in actions) View.VISIBLE else View.GONE
        binding.buttonReport.visibility =
            if (PropertyPrimaryAction.VIEW_REPORT in actions) View.VISIBLE else View.GONE
        binding.buttonDisputes.visibility =
            if (PropertyPrimaryAction.VIEW_DISPUTES in actions) View.VISIBLE else View.GONE
        binding.buttonDisputes.text = if (property.status == PropertyStatus.CLOSED) {
            getString(R.string.tile_dispute_history)
        } else {
            getString(R.string.tile_disputes)
        }

        val actionsKey = actions.joinToString { it.name }
        if (actionsKey != lastActionsKey) {
            lastActionsKey = actionsKey
            staggerVisibleActions()
        }
    }

    private fun staggerVisibleActions() {
        listOf(
            binding.buttonRoomSetup,
            binding.buttonInspection,
            binding.buttonReviewSign,
            binding.buttonStartMoveOut,
            binding.buttonCompare,
            binding.buttonReport,
            binding.buttonDisputes,
        )
            .filter { it.visibility == View.VISIBLE }
            .forEachIndexed { index, button -> button.fadeInSlideUp(index * 45L) }
    }

    private fun hideAllActionButtons() {
        binding.buttonRoomSetup.visibility = View.GONE
        binding.buttonInspection.visibility = View.GONE
        binding.buttonReviewSign.visibility = View.GONE
        binding.cardWaitingLandlord.visibility = View.GONE
        binding.buttonStartMoveOut.visibility = View.GONE
        binding.buttonCompare.visibility = View.GONE
        binding.buttonReport.visibility = View.GONE
        binding.buttonDisputes.visibility = View.GONE
    }

    private fun currentInspectionPhase(): InspectionPhase =
        viewModel.inspectionPhase.value

    private fun navigateToInspection(propertyId: String, phase: InspectionPhase) {
        findNavController().navigateAnimated(
            PropertyDetailsFragmentDirections.actionPropertyDetailsToInspection(propertyId, phase.name),
        )
    }

    private fun navigateToReviewSign(propertyId: String, phase: InspectionPhase) {
        findNavController().navigateAnimated(
            PropertyDetailsFragmentDirections.actionPropertyDetailsToReviewSign(propertyId, phase.name),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class RoleUiInput(
        val property: Property?,
        val moveInSigs: List<Signature>,
        val moveOutSigs: List<Signature>,
        val uid: String?,
        val hasRooms: Boolean,
        val hasDisputes: Boolean,
    )
}
