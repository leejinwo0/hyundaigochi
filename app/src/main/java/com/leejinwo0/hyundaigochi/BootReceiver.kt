package com.leejinwo0.hyundaigochi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// 차 시스템이 켜질 때 기록 서비스를 시작 (허용되지 않으면 조용히 건너뜀)
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            context.startForegroundService(Intent(context, RecorderService::class.java))
        } catch (e: Exception) {
            // 허용되지 않는 환경
        }
    }
}
