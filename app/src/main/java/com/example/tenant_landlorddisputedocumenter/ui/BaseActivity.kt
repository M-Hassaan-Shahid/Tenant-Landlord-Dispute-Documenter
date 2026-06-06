package com.example.tenant_landlorddisputedocumenter.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer

/**
 * Common base for every screen. Exposes the app-wide [ServiceContainer] so child activities can
 * pull repositories without going through Hilt.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val container: ServiceContainer
        get() = (application as ProofNestApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        window.decorView.applyAppBarStatusBarInset()
    }
}
