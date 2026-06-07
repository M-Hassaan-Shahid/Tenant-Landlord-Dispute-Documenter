package com.example.tenant_landlorddisputedocumenter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.fragment.app.FragmentManager
import com.example.tenant_landlorddisputedocumenter.ui.applyAppBarStatusBarInset
import com.example.tenant_landlorddisputedocumenter.ui.applyBottomNavInset
import com.example.tenant_landlorddisputedocumenter.ui.navigateAnimated
import com.example.tenant_landlorddisputedocumenter.ui.pulse
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.tenant_landlorddisputedocumenter.databinding.ActivityMainBinding
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer
import com.example.tenant_landlorddisputedocumenter.ui.auth.LoginActivity
import com.example.tenant_landlorddisputedocumenter.navigation.PropertyDetailsFragmentArgs
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROPERTY_ID = "extra_property_id"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var container: ServiceContainer
    private var pendingPropertyId: String? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) registerFcmToken()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        container = (application as ProofNestApplication).container

        if (container.firebaseAuth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.applyBottomNavInset()

        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: androidx.fragment.app.Fragment,
                    v: View,
                    savedInstanceState: Bundle?,
                ) {
                    v.applyAppBarStatusBarInset()
                }
            },
            true,
        )

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.apply {
            isItemActiveIndicatorEnabled = false
            setupWithNavController(navController)
        }
        var lastTopLevelId = R.id.navigation_dashboard
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val topLevel = destination.id in setOf(
                R.id.navigation_dashboard,
                R.id.navigation_notifications,
                R.id.navigation_profile,
            )
            if (topLevel && destination.id != lastTopLevelId) {
                binding.navHostFragment.pulse(1.02f)
                lastTopLevelId = destination.id
            }
        }
        requestNotificationPermissionIfNeeded()
        registerFcmToken()
        bindNotificationBadge()
        consumeDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        val propertyId = intent?.getStringExtra(EXTRA_PROPERTY_ID) ?: return
        intent.removeExtra(EXTRA_PROPERTY_ID)
        if (::navController.isInitialized) {
            navigateToProperty(propertyId)
        } else {
            pendingPropertyId = propertyId
        }
    }

    private fun navigateToProperty(propertyId: String) {
        val args = PropertyDetailsFragmentArgs(propertyId).toBundle()
        navController.navigateAnimated(R.id.navigation_property_details, args)
    }

    private fun bindNotificationBadge() {
        val uid = container.firebaseAuth.currentUser?.uid ?: return
        val bottomNav: BottomNavigationView = binding.bottomNavigation
        val badge = bottomNav.getOrCreateBadge(R.id.navigation_notifications).apply {
            badgeGravity = BadgeDrawable.TOP_END
            horizontalOffset = 2
            verticalOffset = 2
            maxCharacterCount = 2
            isVisible = false
        }
        var lastCount = 0
        lifecycleScope.launch {
            container.notificationRepository.observeUnreadCount(uid).collect { count ->
                if (count > 0) {
                    badge.isVisible = true
                    badge.number = count.coerceAtMost(99)
                    if (count > lastCount) {
                        binding.bottomNavigation.pulse(1.04f)
                    }
                } else {
                    badge.isVisible = false
                    badge.clearNumber()
                }
                lastCount = count
            }
        }
    }

    private fun registerFcmToken() {
        val uid = container.firebaseAuth.currentUser?.uid ?: return
        lifecycleScope.launch {
            runCatching { container.syncCoordinator.registerFcmToken(uid) }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        if (::container.isInitialized && container.firebaseAuth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
            return
        }
        pendingPropertyId?.let { id ->
            pendingPropertyId = null
            navigateToProperty(id)
        }
        if (!::container.isInitialized) return
        val uid = container.firebaseAuth.currentUser?.uid ?: return
        lifecycleScope.launch {
            runCatching { container.inspectionRepository.syncPendingUploads() }
            runCatching { container.notificationRepository.syncForUser(uid) }
            runCatching {
                val properties = container.propertyRepository.observeForUser(uid).first()
                container.notificationRepository.ensureLeaseEndingReminders(uid, properties)
            }
        }
    }
}
