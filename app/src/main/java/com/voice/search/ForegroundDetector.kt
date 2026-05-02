package com.voice.search

import android.app.usage.UsageStatsManager
import android.content.Context

object ForegroundDetector {
    private const val TVBOX_PACKAGE = "com.mygithub0.tvbox0.osdkitkat"

    fun isTvBoxInForeground(context: Context): Boolean {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 5000

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, beginTime, endTime
            )

            val foreground = usageStats
                .filter { it.lastTimeUsed > 0 }
                .maxByOrNull { it.lastTimeUsed }

            foreground?.packageName == TVBOX_PACKAGE
        } catch (e: Exception) {
            false
        }
    }

    fun waitForTvBoxForeground(context: Context, timeoutMs: Long = 10_000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isTvBoxInForeground(context)) return true
            Thread.sleep(500)
        }
        return false
    }
}