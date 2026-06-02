package com.example.tenant_landlorddisputedocumenter.ui.property

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
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentCreatePropertyBinding
import com.example.tenant_landlorddisputedocumenter.util.DateUtils
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class CreatePropertyFragment : Fragment() {

    private var _binding: FragmentCreatePropertyBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PropertyViewModel

    private var leaseStartMillis: Long = MaterialDatePicker.todayInUtcMilliseconds()
    private var leaseEndMillis: Long = leaseStartMillis + TimeUnit.DAYS.toMillis(365)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCreatePropertyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        savedInstanceState?.let {
            leaseStartMillis = it.getLong(KEY_LEASE_START, leaseStartMillis)
            leaseEndMillis = it.getLong(KEY_LEASE_END, leaseEndMillis)
        }

        val container = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, PropertyViewModelFactory(container))[PropertyViewModel::class.java]

        updateLeaseFields()
        binding.inputLeaseStart.setOnClickListener { pickDate(isStart = true) }
        binding.layoutLeaseStart.setEndIconOnClickListener { pickDate(isStart = true) }
        binding.inputLeaseEnd.setOnClickListener { pickDate(isStart = false) }
        binding.layoutLeaseEnd.setEndIconOnClickListener { pickDate(isStart = false) }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.buttonCreate.setOnClickListener {
            if (leaseEndMillis <= leaseStartMillis) {
                Toast.makeText(requireContext(), R.string.lease_end_before_start, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.createProperty(
                address = binding.inputAddress.text.toString(),
                rentStr = binding.inputRent.text.toString(),
                depositStr = binding.inputDeposit.text.toString(),
                leaseStartMillis = leaseStartMillis,
                leaseEndMillis = leaseEndMillis,
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.buttonCreate.isEnabled = !state.isLoading

                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearState()
                    }

                    if (state.successProperty != null) {
                        Toast.makeText(requireContext(), "Property created!", Toast.LENGTH_SHORT).show()
                        viewModel.clearState()
                        findNavController().navigateUp()
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_LEASE_START, leaseStartMillis)
        outState.putLong(KEY_LEASE_END, leaseEndMillis)
    }

    private fun updateLeaseFields() {
        binding.inputLeaseStart.setText(DateUtils.formatReadable(leaseStartMillis))
        binding.inputLeaseEnd.setText(DateUtils.formatReadable(leaseEndMillis))
    }

    private fun pickDate(isStart: Boolean) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStart) getString(R.string.lease_start_hint) else getString(R.string.lease_end_hint))
            .setSelection(if (isStart) leaseStartMillis else leaseEndMillis)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            if (isStart) {
                leaseStartMillis = selection
                if (leaseEndMillis <= leaseStartMillis) {
                    leaseEndMillis = leaseStartMillis + TimeUnit.DAYS.toMillis(365)
                }
            } else {
                leaseEndMillis = selection
            }
            updateLeaseFields()
        }
        picker.show(parentFragmentManager, if (isStart) "lease_start" else "lease_end")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_LEASE_START = "lease_start"
        private const val KEY_LEASE_END = "lease_end"
    }
}
