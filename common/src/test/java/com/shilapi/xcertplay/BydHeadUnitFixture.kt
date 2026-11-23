package com.shilapi.xcertplay

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.robolectric.Shadows.shadowOf

/** Robolectric is no BYD head unit, and the DiLink 4 cluster routes need one (#1327). */
internal fun installBydCarSettings(context: Context) {
    shadowOf(context.packageManager).installPackage(PackageInfo().apply {
        packageName = "com.byd.carsettings"
        applicationInfo = ApplicationInfo().apply {
            packageName = "com.byd.carsettings"
            flags = ApplicationInfo.FLAG_SYSTEM
        }
    })
}
