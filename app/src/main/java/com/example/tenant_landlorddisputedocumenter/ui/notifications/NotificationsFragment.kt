package com.example.tenant_landlorddisputedocumenter.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenant_landlorddisputedocumenter.MainActivity
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentNotificationsBinding
import android.widget.Toast
import com.example.tenant_landlorddisputedocumenter.domain.model.AppNotification
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.ui.applyProofNestItemAnimations
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        val authRepo = appContainer.authRepository
        val notifRepo = appContainer.notificationRepository

        val adapter = NotificationAdapter { notification -> onNotificationClicked(notification) }
        binding.recyclerViewNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewNotifications.applyProofNestItemAnimations()
        binding.recyclerViewNotifications.adapter = adapter

        binding.toolbar.setOnMenuItemClickListener { item -> onMenuItem(item, authRepo.currentUserId.value) }

        val uid = authRepo.currentUserId.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { notifRepo.syncForUser(uid) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                notifRepo.observeForUser(uid).collect { notifications ->
                    adapter.submitList(notifications)
                    val empty = notifications.isEmpty()
                    binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.recyclerViewNotifications.visibility = if (empty) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun onMenuItem(item: MenuItem, uid: String?): Boolean {
        if (item.itemId != R.id.action_mark_all_read || uid == null) return false
        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = appContainer.notificationRepository.markAllRead(uid)) {
                is Outcome.Failure -> Toast.makeText(requireContext(), result.userMessage, Toast.LENGTH_SHORT).show()
                is Outcome.Success -> Unit
            }
        }
        return true
    }

    private fun onNotificationClicked(notification: AppNotification) {
        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        val uid = appContainer.authRepository.currentUserId.value ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            appContainer.notificationRepository.markRead(notification.id)
        }
        val propertyId = notification.propertyId ?: return
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_PROPERTY_ID, propertyId)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
