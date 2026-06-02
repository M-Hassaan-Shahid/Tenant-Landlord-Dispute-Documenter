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
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyStatusUi
import kotlinx.coroutines.launch

class PropertyDetailsFragment : Fragment() {

    private var _binding: FragmentPropertyDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PropertyDetailsViewModel
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
        viewModel.loadProperty(propertyId)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { appContainer.syncCoordinator.refreshPropertyData(propertyId) }
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.buttonRoomSetup.setOnClickListener {
            findNavController().navigate(
                PropertyDetailsFragmentDirections.actionPropertyDetailsToRoomSetup(propertyId),
            )
        }

        binding.buttonInspection.setOnClickListener {
            navigateToInspection(propertyId, viewModel.inspectionPhase.value)
        }

        binding.buttonStartMoveOut.setOnClickListener {
            val uid = appContainer.authRepository.currentUserId.value ?: return@setOnClickListener
            viewModel.startMoveOut(uid)
        }

        binding.buttonCompare.setOnClickListener {
            findNavController().navigate(
                PropertyDetailsFragmentDirections.actionPropertyDetailsToCompare(propertyId),
            )
        }

        binding.buttonReport.setOnClickListener {
            findNavController().navigate(
                PropertyDetailsFragmentDirections.actionPropertyDetailsToReport(propertyId),
            )
        }

        binding.buttonDisputes.setOnClickListener {
            findNavController().navigate(
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.property.collect { property ->
                    if (property == null) return@collect
                    binding.textAddress.text = property.address
                    PropertyStatusUi.apply(binding.chipStatus, property.status)
                    binding.textRent.text = "PKR ${property.rent.toInt()}"
                    binding.textDeposit.text = "PKR ${property.deposit.toInt()}"
                    binding.textInviteCode.text = property.inviteCode

                    val start = com.example.tenant_landlorddisputedocumenter.util.DateUtils.formatReadable(property.leaseStartMillis)
                    val end = com.example.tenant_landlorddisputedocumenter.util.DateUtils.formatReadable(property.leaseEndMillis)
                    binding.textLeaseDates.text = "$start - $end"

                    val currentUser = appContainer.authRepository.currentUserId.value
                    val isLandlord = property.landlordId == currentUser

                    binding.cardInvite.visibility = if (isLandlord) View.VISIBLE else View.GONE
                    binding.cardApproval.visibility =
                        if (isLandlord && property.status == PropertyStatus.PENDING_APPROVAL) View.VISIBLE else View.GONE

                    val isTenant = property.tenantId == currentUser
                    binding.cardTenantPending.visibility =
                        if (isTenant && property.status == PropertyStatus.PENDING_APPROVAL) View.VISIBLE else View.GONE

                    binding.buttonRoomSetup.visibility =
                        if (property.status == PropertyStatus.ACTIVE) View.VISIBLE else View.GONE

                    val inspectionPhase = when (property.status) {
                        PropertyStatus.ACTIVE -> InspectionPhase.MOVE_IN
                        PropertyStatus.MOVE_OUT -> InspectionPhase.MOVE_OUT
                        else -> viewModel.inspectionPhase.value
                    }
                    viewModel.setInspectionPhase(inspectionPhase)

                    binding.buttonInspection.visibility = when (property.status) {
                        PropertyStatus.ACTIVE, PropertyStatus.MOVE_OUT -> View.VISIBLE
                        else -> View.GONE
                    }
                    binding.buttonInspection.text = when (property.status) {
                        PropertyStatus.MOVE_OUT -> getString(R.string.tile_move_out)
                        else -> getString(R.string.tile_move_in)
                    }

                    binding.buttonStartMoveOut.visibility =
                        if (isLandlord && property.status == PropertyStatus.OCCUPIED) View.VISIBLE else View.GONE

                    val showCompareAndReport = property.status == PropertyStatus.MOVE_OUT ||
                        property.status == PropertyStatus.CLOSED
                    binding.buttonCompare.visibility = if (showCompareAndReport) View.VISIBLE else View.GONE
                    binding.buttonReport.visibility = if (showCompareAndReport) View.VISIBLE else View.GONE
                    binding.buttonDisputes.visibility = if (showCompareAndReport) View.VISIBLE else View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
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

    private fun navigateToInspection(propertyId: String, phase: InspectionPhase) {
        findNavController().navigate(
            PropertyDetailsFragmentDirections.actionPropertyDetailsToInspection(propertyId, phase.name),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
