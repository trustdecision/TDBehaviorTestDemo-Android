package com.TrustDecision.TDBehaviorTestDemo

/**
 * Demo 可配置参数统一入口。
 *
 * 对外演示或切换环境时只修改本文件，避免 SDK 初始化、风险查询和页面代码
 * 分别维护同一参数而产生不一致。
 */
internal object DemoConfig {
    const val PARTNER = "LPZ13"
    const val PARTNER_KEY = "3df50dab532e46ad8d93d01d9df7e4de"
    const val APP_KEY = "5978b5f9fd6a8bf5de02f0054d0dccec"
    const val COUNTRY = "CN"

    const val BEHAVIOR_HOST = ""
    const val BEHAVIOR_CONFIG_URL = ""
    const val BEHAVIOR_UPLOAD_URL = ""
    const val BEHAVIOR_HTTP_TIMEOUT_SECONDS = 15.0f
    const val BEHAVIOR_COLLECT_TIMEOUT_SECONDS = 0.5f
    const val BEHAVIOR_KEEP_NAVI_INFO = false

    const val PRIVACY_POLICY_URL = "https://trustdecision.com/privacy-policy"
    const val HTTP_TIMEOUT_MS = 15_000

    val RISK_URL = "https://${COUNTRY}.apitd.net/device/v3"
}
