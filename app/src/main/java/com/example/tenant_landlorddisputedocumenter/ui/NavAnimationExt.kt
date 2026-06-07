package com.example.tenant_landlorddisputedocumenter.ui

import androidx.annotation.IdRes
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import com.example.tenant_landlorddisputedocumenter.R

/** Default slide transitions for stack navigation. */
fun NavController.defaultNavOptions(): NavOptions =
    NavOptions.Builder()
        .setEnterAnim(R.anim.slide_in_right)
        .setExitAnim(R.anim.slide_out_left)
        .setPopEnterAnim(R.anim.slide_in_left)
        .setPopExitAnim(R.anim.slide_out_right)
        .build()

fun NavController.navigateAnimated(@IdRes resId: Int, args: android.os.Bundle? = null) {
    navigate(resId, args, defaultNavOptions())
}

fun NavController.navigateAnimated(directions: NavDirections) {
    navigate(directions, defaultNavOptions())
}
