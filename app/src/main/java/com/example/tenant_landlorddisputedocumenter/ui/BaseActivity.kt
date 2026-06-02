package com.example.tenant_landlorddisputedocumenter.ui

import androidx.appcompat.app.AppCompatActivity
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.di.ServiceContainer

/**
 * Common base for every screen. Exposes the app-wide [ServiceContainer] so child activities can
 * pull repositories without going through Hilt.
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val container: ServiceContainer
        get() = (application as ProofNestApplication).container
}
