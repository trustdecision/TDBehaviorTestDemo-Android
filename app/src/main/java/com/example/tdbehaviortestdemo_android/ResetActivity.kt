package com.TrustDecision.TDBehaviorTestDemo

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

/**
 * 为无障碍自动化提供确定性的页面重置入口。
 *
 * Android 不允许普通无障碍应用强制停止其他应用，按键精灵的 KillApp 在
 * android_acc 模式下因此无法可靠工作。启动本 Activity 会清空本 Demo 的任务栈，
 * 再创建全新的 MainActivity；进程可以继续存在，但页面和输入状态与冷启动一致。
 */
class ResetActivity : Activity() {
    companion object {
        const val EXTRA_RESET_UI = "com.TrustDecision.TDBehaviorTestDemo.RESET_UI"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 先结束旧任务中的登录、注册、抢票和隐私页面，再创建全新任务。
        // makeRestartActivityTask 同时携带 NEW_TASK 与 CLEAR_TASK，语义等同于
        // 从桌面冷启动 MainActivity，但不依赖无障碍进程拥有 force-stop 权限。
        finishAffinity()
        startActivity(
            Intent.makeRestartActivityTask(
                ComponentName(this, MainActivity::class.java)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_RESET_UI, true)
            }
        )
        finish()
    }
}
