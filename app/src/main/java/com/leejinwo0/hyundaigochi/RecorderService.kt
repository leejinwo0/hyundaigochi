package com.leejinwo0.hyundaigochi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * 화면이 닫혀 있어도 주행 기록이 이어지게 하는 서비스.
 * [확인 필요] Pleos 기획 가이드는 상시 백그라운드 구동을 가급적 피하라고 안내하므로,
 * 이 서비스가 차량에서 허용되는지는 실제 기기에서 확인해야 함. 허용되지 않으면 조용히 종료됨.
 */
class RecorderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        GameRuntime.start(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        GameRuntime.start(applicationContext)
        return START_STICKY
    }

    private fun goForeground() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "주행 기록", NotificationManager.IMPORTANCE_MIN)
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("기름만 줘도 잘 커요")
                .setContentText("달린 기록을 모으고 있어요")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // 이 환경에서 허용되지 않으면 서비스를 끝냄 (화면이 열려 있는 동안의 기록은 계속됨)
            stopSelf()
        }
    }

    companion object {
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 1001
    }
}
