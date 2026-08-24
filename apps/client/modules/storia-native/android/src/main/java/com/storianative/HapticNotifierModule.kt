package com.storianative

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import java.util.UUID

private const val CHANNEL_ID = "storia_new_message"
private const val CHANNEL_NAME = "새 메시지"
private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 8825

/**
 * iOS HapticNotifierModule.swift(RCTBridgeModule)와 대응되는 Android 구현. 클래식
 * ReactContextBaseJavaModule 패턴이라 New Architecture(Fabric/TurboModules) 환경에서도
 * 인터롭 레이어로 그대로 동작하지만, 이 머신엔 Android SDK/에뮬레이터가 없어 실제 빌드·
 * 링크·동작은 미검증 — iOS 쪽과 동일한 상황.
 *
 * UINotificationFeedbackGenerator 대신 Vibrator, UNUserNotificationCenter 대신
 * NotificationManagerCompat을 쓴다. iOS는 포그라운드일 때 로컬 알림이 기본 억제되어
 * delegate로 opt-in해야 했지만, Android는 채널 importance를 HIGH로 두면 포그라운드에서도
 * 헤즈업 배너가 기본으로 뜬다.
 */
class HapticNotifierModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "HapticNotifier"

    @ReactMethod
    fun notify(title: String, body: String) {
        vibrate()
        requestAuthorizationIfNeeded { granted ->
            if (granted) showNotification(title, body)
        }
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = reactContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            reactContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }

    /**
     * iOS의 `requestAuthorizationIfNeeded`와 동일한 타이밍 — 앱 시작 시 미리 묻지 않고,
     * 첫 알림이 실제로 필요해진 이 시점에 lazy하게 묻는다. API 33 미만은 런타임 알림
     * 권한 자체가 없어 항상 허용으로 취급한다.
     */
    private fun requestAuthorizationIfNeeded(completion: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            completion(true)
            return
        }

        val alreadyGranted = ContextCompat.checkSelfPermission(
            reactContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            completion(true)
            return
        }

        // RN 네이티브 모듈이 시스템 권한 다이얼로그를 띄우는 표준 방법: currentActivity를
        // PermissionAwareActivity로 캐스팅해 requestPermissions를 호출하고, 결과는
        // PermissionListener 콜백으로 받는다 (JS 쪽 코드는 전혀 몰라도 됨).
        val activity = reactContext.currentActivity as? PermissionAwareActivity
        if (activity == null) {
            // 포그라운드 Activity가 없으면 다이얼로그를 띄울 방법이 없음 — iOS의
            // default 분기(completion(false))와 동일하게 조용히 스킵.
            completion(false)
            return
        }

        activity.requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE,
            PermissionListener { requestCode, _, grantResults ->
                if (requestCode != NOTIFICATION_PERMISSION_REQUEST_CODE) return@PermissionListener false
                val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
                completion(granted)
                true
            }
        )
    }

    private fun showNotification(title: String, body: String) {
        ensureChannel()

        val notification = NotificationCompat.Builder(reactContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(reactContext.applicationInfo.icon)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(reactContext).notify(UUID.randomUUID().hashCode(), notification)
        } catch (e: SecurityException) {
            // 방금 권한을 확인했더라도 그 사이 취소되는 경쟁 상태가 있을 수 있음 — 조용히 무시
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = reactContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}
