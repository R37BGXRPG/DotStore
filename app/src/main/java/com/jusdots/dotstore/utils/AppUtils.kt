package com.jusdots.dotstore.utils

import android.content.Context
import android.content.pm.PackageManager

object AppUtils {
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        val baseName = packageName.substringAfterLast(".")
        
        val variations = listOf(
            packageName,
            "com.shubh.$baseName",
            "com.jusdots.$baseName",
            "com.shubh.${baseName.replace("dot", "dot_")}",
            "com.jusdots.${baseName.replace("dot", "dot_")}",
            "com.shubh.${baseName.replace("dot", "")}",
            "com.jusdots.${baseName.replace("dot", "")}"
        ).distinct()

        for (variation in variations) {
            if (isPackageInstalled(context, variation)) return true
        }

        return false
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        
        // Method 1: getPackageInfo (Standard)
        try {
            pm.getPackageInfo(packageName, 0)
            return true
        } catch (e: Exception) {}
        
        // Method 2: Launch Intent check
        if (pm.getLaunchIntentForPackage(packageName) != null) return true

        // Method 3: Deep Scan
        try {
            val apps = pm.getInstalledPackages(0)
            if (apps.any { it.packageName == packageName }) return true
            
            // Method 4: Keyword Search Fallback (Extremely Aggressive)
            val base = packageName.substringAfterLast(".").lowercase()
            if (apps.any { 
                val p = it.packageName.lowercase()
                p.contains(base) && (p.contains("shubh") || p.contains("jusdots"))
            }) return true
        } catch (e: Exception) {}

        return false
    }

    fun getInstalledVersion(context: Context, packageName: String): String? {
        val pm = context.packageManager
        val baseName = packageName.substringAfterLast(".")
        
        val variations = listOf(
            packageName,
            "com.shubh.$baseName",
            "com.jusdots.$baseName",
            "com.shubh.${baseName.replace("dot", "dot_")}",
            "com.jusdots.${baseName.replace("dot", "dot_")}",
            "com.shubh.${baseName.replace("dot", "")}",
            "com.jusdots.${baseName.replace("dot", "")}"
        ).distinct()

        for (variation in variations) {
            try {
                val version = pm.getPackageInfo(variation, 0).versionName
                if (version != null) return version
            } catch (e: Exception) {}
        }
        
        return null
    }

    fun isVersionSame(v1: String?, v2: String?): Boolean {
        if (v1 == null || v2 == null) return v1 == v2
        val normalize = { v: String -> 
            v.trim().lowercase().removePrefix("v").split(".").take(2).joinToString(".")
        }
        return normalize(v1) == normalize(v2)
    }

    fun openApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        }
    }
}
