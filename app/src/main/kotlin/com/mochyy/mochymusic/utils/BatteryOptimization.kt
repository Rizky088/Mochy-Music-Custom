package com.mochyy.mochymusic.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimization {
    /** true when Android already allows Mochy to run unrestricted in the background */
    fun isIgnoring(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** opens Android's direct ignore-battery-optimizations prompt, with safe OEM fallbacks */
    @SuppressLint("BatteryLife")
    fun request(context: Context) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(flags)
        val launched = runCatching { context.startActivity(direct) }.isSuccess
        if (launched) return

        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(flags)
        if (runCatching { context.startActivity(listIntent) }.isSuccess) return

        val appDetails = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(flags)
        runCatching { context.startActivity(appDetails) }
    }

    /** true on Android Go devices or phones with ~4 GB RAM or less (totalMem <= 4.6 GB) */
    fun isLowMemoryDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem > 0L && info.totalMem <= 4_600_000_000L
    }
}
