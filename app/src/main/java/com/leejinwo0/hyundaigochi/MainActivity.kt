package com.leejinwo0.hyundaigochi

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.leejinwo0.hyundaigochi.ui.theme.HyundaiGochiTheme

class MainActivity : ComponentActivity() {

    // 차속 권한 (급가속·급감속 감지용, Android Automotive 표준 권한)
    private val speedPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            GameRuntime.onSpeedPermission(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GameRuntime.start(applicationContext)

        // 화면을 닫아도 기록이 이어지게 서비스를 켬 (이 환경에서 허용되지 않으면 조용히 종료됨)
        try {
            startService(Intent(this, RecorderService::class.java))
        } catch (e: Exception) {
            // 화면이 열려 있는 동안의 기록은 계속됨
        }

        if (checkSelfPermission(GameRuntime.SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            speedPermission.launch(GameRuntime.SPEED_PERMISSION)
        }

        setContent {
            HyundaiGochiTheme {
                GameApp(onRequestSpeed = { speedPermission.launch(GameRuntime.SPEED_PERMISSION) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        GameRuntime.refreshTick() // 쉬는 동안 회복된 컨디션, 지금 시간에 맞는 기분 반영
    }
}
