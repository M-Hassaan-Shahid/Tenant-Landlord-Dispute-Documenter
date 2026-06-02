package com.example.tenant_landlorddisputedocumenter.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentDashboardBinding
import com.example.tenant_landlorddisputedocumenter.navigation.DashboardFragmentDirections
import android.widget.Toast
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: PropertyAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val container = (requireContext().applicationContext as ProofNestApplication).container
        val factory = DashboardViewModelFactory(container)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]

        val currentUserId = container.authRepository.currentUserId.value ?: ""
        
        adapter = PropertyAdapter(currentUserId) { property ->
            val action = DashboardFragmentDirections.actionDashboardToPropertyDetails(property.id)
            findNavController().navigate(action)
        }
        
        binding.recyclerViewProperties.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewProperties.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.proofnest_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.sync()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefresh.isRefreshing = state.isLoading
                    state.error?.let { message ->
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }

        binding.fabAdd.isEnabled = false
        binding.fabAdd.setOnClickListener {
            when (container.authRepository.currentUserRole.value) {
                com.example.tenant_landlorddisputedocumenter.domain.model.UserRole.TENANT ->
                    findNavController().navigate(R.id.action_dashboard_to_join_property)
                com.example.tenant_landlorddisputedocumenter.domain.model.UserRole.LANDLORD ->
                    findNavController().navigate(R.id.action_dashboard_to_create_property)
                null -> Unit
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.authRepository.currentUserRole.collect { role ->
                    binding.fabAdd.isEnabled = role != null
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.properties.collect { props ->
                    adapter.submitList(props)
                    val empty = props.isEmpty()
                    binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.cardWelcome.visibility = if (empty) View.GONE else View.VISIBLE
                    binding.recyclerViewProperties.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    container.authRepository.observeCurrentProfile(),
                    viewModel.properties,
                    container.notificationRepository.observeUnreadCount(currentUserId),
                ) { user, props, unread ->
                    Triple(user, props, unread)
                }.collect { (user, props, unread) ->
                    val firstName = user?.displayName?.substringBefore(' ')?.ifBlank { "there" } ?: "there"
                    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                        in 5..11 -> "Good morning"
                        in 12..16 -> "Good afternoon"
                        in 17..20 -> "Good evening"
                        else -> "Hello"
                    }
                    binding.textWelcomeTitle.text = "$greeting, $firstName"
                    binding.textWelcomeSubtitle.text = if (unread > 0) {
                        getString(R.string.dashboard_welcome_subtitle, props.size, unread)
                    } else {
                        getString(R.string.dashboard_welcome_subtitle_none, props.size)
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
