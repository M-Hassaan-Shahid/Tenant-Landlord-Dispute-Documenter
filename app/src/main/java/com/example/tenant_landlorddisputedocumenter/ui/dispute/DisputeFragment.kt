package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.navigation.DisputeFragmentArgs
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentDisputeBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyFlowPolicy
import com.example.tenant_landlorddisputedocumenter.ui.fadeInSlideUp
import com.example.tenant_landlorddisputedocumenter.ui.guardPropertyAccess
import com.example.tenant_landlorddisputedocumenter.ui.pulse
import com.example.tenant_landlorddisputedocumenter.ui.inspection.CameraCaptureActivity
import com.example.tenant_landlorddisputedocumenter.ui.showPhotoViewer
import android.net.Uri
import kotlinx.coroutines.launch

class DisputeFragment : Fragment() {

    private var _binding: FragmentDisputeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DisputeViewModel
    private val args: DisputeFragmentArgs by lazy {
        DisputeFragmentArgs.fromBundle(requireArguments())
    }
    private lateinit var propertyId: String
    private lateinit var itemId: String
    private var disputePhase: InspectionPhase = InspectionPhase.MOVE_IN

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val photoId = result.data?.getStringExtra(CameraCaptureActivity.RESULT_PHOTO_ID)
        val photoUri = result.data?.getStringExtra(CameraCaptureActivity.RESULT_PHOTO_URI)
        if (photoId != null) {
            viewModel.setCounterPhoto(photoId)
            binding.placeholderEvidence.visibility = View.GONE
            binding.imageEvidence.visibility = View.VISIBLE
            photoUri?.let {
                binding.imageEvidence.tag = it
                Glide.with(this).load(it).into(binding.imageEvidence)
            }
            binding.imageEvidence.fadeInSlideUp()
            binding.imageEvidence.pulse(1.04f)
            Toast.makeText(requireContext(), "Counter-evidence photo attached.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDisputeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, DisputeViewModelFactory(appContainer))[DisputeViewModel::class.java]

        propertyId = args.propertyId
        itemId = args.itemId
        val itemName = args.itemName

        guardPropertyAccess(propertyId, PropertyFlowPolicy::memberAccess) { }
        viewModel.loadForProperty(propertyId)
        viewModel.targetItemId = itemId

        binding.textItemName.text = getString(R.string.dispute_item_label, itemName)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.propertyRepository.observeProperty(propertyId).collect { property ->
                    disputePhase = when (property?.status) {
                        com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus.MOVE_OUT ->
                            InspectionPhase.MOVE_OUT
                        else -> InspectionPhase.MOVE_IN
                    }
                }
            }
        }

        binding.buttonAttachPhoto.setOnClickListener {
            val intent = Intent(requireContext(), CameraCaptureActivity::class.java).apply {
                putExtra(CameraCaptureActivity.EXTRA_PROPERTY_ID, propertyId)
                putExtra(CameraCaptureActivity.EXTRA_ITEM_ID, itemId)
                putExtra(CameraCaptureActivity.EXTRA_PHASE, disputePhase.name)
                putExtra(CameraCaptureActivity.EXTRA_CAPTURE_MODE, CameraCaptureActivity.MODE_DISPUTE_EVIDENCE)
            }
            captureLauncher.launch(intent)
        }

        binding.imageEvidence.setOnClickListener {
            val drawable = binding.imageEvidence.drawable ?: return@setOnClickListener
            if (binding.imageEvidence.visibility != View.VISIBLE) return@setOnClickListener
            val tag = binding.imageEvidence.tag as? String
            showPhotoViewer(tag?.let { Uri.parse(it) })
        }

        binding.buttonSubmitDispute.setOnClickListener {
            viewModel.submitDispute(binding.inputDisputeReason.text.toString())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.buttonSubmitDispute.isEnabled = !state.isLoading
                    binding.buttonAttachPhoto.isEnabled = !state.isLoading
                    binding.buttonSubmitDispute.text = if (state.isLoading) {
                        getString(R.string.dispute_submitting)
                    } else {
                        getString(R.string.action_submit_dispute)
                    }
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                    if (state.submitted) {
                        binding.buttonSubmitDispute.pulse()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.dispute_after_submit_body),
                            Toast.LENGTH_LONG,
                        ).show()
                        viewModel.clearMessages()
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
