package com.voice.search

import android.content.ComponentName
import android.content.Context
import android.content.Intent

object TvBoxHelper {
    private const val TVBOX_PACKAGE = "com.mygithub0.tvbox0.osdkitkat"
    private const val SEARCH_RECEIVER = "com.github.tvbox.osc.receiver.SearchReceiver"

    fun launchTvBox(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(TVBOX_PACKAGE)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            context.startActivity(intent)
        }
    }

    fun isTvBoxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TVBOX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun sendSearchBroadcast(context: Context, keyword: String) {
        val intent = Intent().apply {
            component = ComponentName(TVBOX_PACKAGE, SEARCH_RECEIVER)
            putExtra("title", keyword)
        }
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // TVBox可能未安装或Receiver未注册，静默处理
        }
    }
}