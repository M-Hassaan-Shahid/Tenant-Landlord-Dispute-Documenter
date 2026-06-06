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
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import com.example.tenant_landlorddisputedocumenter.navigation.DashboardFragmentDirections
import com.example.tenant_landlorddisputedocumenter.ui.util.PropertyRoleUi
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
            viewModel.sync(force = true)
        }
        binding.toolbar.post {
            val toolbarBottom = binding.toolbar.bottom
            val offsetEnd = toolbarBottom + (48 * resources.displayMetrics.density).toInt()
            binding.swipeRefresh.setProgressViewOffset(false, toolbarBottom, offsetEnd)
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
                    val isLandlord = role == UserRole.LANDLORD
                    if (role != null) {
                        binding.fabAdd.contentDescription = getString(PropertyRoleUi.dashboardFabActionRes(isLandlord))
                        binding.textEmptySubtitle.text = getString(PropertyRoleUi.dashboardEmptySubtitleRes(isLandlord))
                    }
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
                    container.authRepository.currentUserRole,
                    viewModel.properties,
                    container.notificationRepository.observeUnreadCount(currentUserId),
                ) { user, role, props, unread ->
                    RoleDashboardState(user, role, props, unread)
                }.collect { state ->
                    val user = state.user
                    val role = state.role
                    val props = state.properties
                    val unread = state.unread
                    val firstName = user?.displayName?.substringBefore(' ')?.ifBlank { "there" } ?: "there"
                    val greeting = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                        in 5..11 -> "Good morning"
                        in 12..16 -> "Good afternoon"
                        in 17..20 -> "Good evening"
                        else -> "Hello"
                    }
                    binding.textWelcomeTitle.text = "$greeting, $firstName"
                    val roleLine = when (role) {
                        UserRole.LANDLORD -> getString(R.string.dashboard_role_landlord)
                        UserRole.TENANT -> getString(R.string.dashboard_role_tenant)
                        null -> ""
                    }
                    val countLine = if (unread > 0) {
                        getString(R.string.dashboard_welcome_subtitle, props.size, unread)
                    } else {
                        resources.getQuantityString(R.plurals.dashboard_property_count, props.size, props.size)
                    }
                    binding.textWelcomeSubtitle.text = if (roleLine.isBlank()) countLine else "$roleLine · $countLine"
                }
            }
        }
    }

    private data class RoleDashboardState(
        val user: com.example.tenant_landlorddisputedocumenter.domain.model.User?,
        val role: UserRole?,
        val properties: List<com.example.tenant_landlorddisputedocumenter.domain.model.Property>,
        val unread: Int,
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
