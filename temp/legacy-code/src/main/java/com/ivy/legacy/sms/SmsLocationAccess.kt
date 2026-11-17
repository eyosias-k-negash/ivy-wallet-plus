package com.ivy.legacy.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * SMS transactions are captured by a broadcast receiver while the app is usually closed,
 * so their location needs the "Allow all the time" grant on Android 10 and up. With only
 * "While using the app", a location is captured only when the app happens to be open.
 */
object SmsLocationAccess {
    val foregroundPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun hasForeground(context: Context): Boolean =
        foregroundPermissions.any { context.hasPermission(it) }

    fun hasBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    fun hasFullAccess(context: Context): Boolean = hasForeground(context) && hasBackground(context)

    private fun Context.hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
