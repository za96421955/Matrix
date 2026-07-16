//<!-- 需要动态请求的权限 -->
//<uses-permission android:name="android.permission.READ_PHONE_STATE" />
//<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
//<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
//
//<!-- 不需要动态请求的权限 -->
//<uses-permission android:name="android.permission.INTERNET" />
//
//// Kotlin 示例代码
//import android.Manifest
//import android.annotation.SuppressLint
//import android.content.Context
//import android.content.pm.PackageManager
//import android.net.wifi.WifiManager
//import android.os.Build
//import android.provider.Settings
//import android.telephony.TelephonyManager
//import java.security.MessageDigest
//import java.util.*
//
//class AndroidFingerprint(private val context: Context) {
//
//    // 收集设备信息
//    @SuppressLint("HardwareIds", "MissingPermission")
//    fun collectDeviceInfo(): Map<String, Any> {
//        val info = mutableMapOf<String, Any>()
//
//        // 1. 设备基本信息
//        info["brand"] = Build.BRAND
//        info["manufacturer"] = Build.MANUFACTURER
//        info["model"] = Build.MODEL
//        info["device"] = Build.DEVICE
//        info["product"] = Build.PRODUCT
//        info["board"] = Build.BOARD
//        info["hardware"] = Build.HARDWARE
//
//        // 2. 系统信息
//        info["sdkVersion"] = Build.VERSION.SDK_INT
//        info["release"] = Build.VERSION.RELEASE
//        info["incremental"] = Build.VERSION.INCREMENTAL
//
//        // 3. Android ID
//        val androidId = Settings.Secure.getString(
//                context.contentResolver,
//                Settings.Secure.ANDROID_ID
//        )
//        info["androidId"] = androidId ?: "unknown"
//
//        // 4. 序列号
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            info["serial"] = Build.getSerial() ?: "unknown"
//        } else {
//            @Suppress("DEPRECATION")
//                    info["serial"] = Build.SERIAL
//        }
//
//        // 5. 屏幕信息
//        val display = context.resources.displayMetrics
//        info["screenWidth"] = display.widthPixels
//        info["screenHeight"] = display.heightPixels
//        info["density"] = display.density
//        info["densityDpi"] = display.densityDpi
//
//        // 6. CPU 信息
//        info["cpuCores"] = Runtime.getRuntime().availableProcessors()
//        info["cpuAbi"] = Build.SUPPORTED_ABIS.joinToString(",")
//
//        // 7. 内存信息
//        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
//        val memoryInfo = android.app.ActivityManager.MemoryInfo()
//        activityManager.getMemoryInfo(memoryInfo)
//        info["totalMemory"] = memoryInfo.totalMem
//        info["availableMemory"] = memoryInfo.availMem
//
//        // 8. 存储信息
//        val statFs = android.os.StatFs(android.os.Environment.getDataDirectory().path)
//        info["totalStorage"] = statFs.totalBytes
//        info["freeStorage"] = statFs.freeBytes
//
//        // 9. 网络信息
//        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
//        val networkInfo = connectivityManager.activeNetworkInfo
//        info["networkType"] = networkInfo?.typeName ?: "unknown"
//
//        // 10. 时区和语言
//        info["timeZone"] = TimeZone.getDefault().id
//        info["language"] = Locale.getDefault().language
//        info["country"] = Locale.getDefault().country
//
//        // 11. Root 检测
//        info["isRooted"] = isRooted()
//
//        return info
//    }
//
//    // Root 检测
//    private fun isRooted(): Boolean {
//        val paths = arrayOf(
//                "/system/app/Superuser.apk",
//                "/sbin/su",
//                "/system/bin/su",
//                "/system/xbin/su",
//                "/data/local/xbin/su",
//                "/data/local/bin/su",
//                "/system/sd/xbin/su",
//                "/system/bin/failsafe/su",
//                "/data/local/su"
//        )
//
//        for (path in paths) {
//            if (java.io.File(path).exists()) {
//                return true
//            }
//        }
//
//        return false
//    }
//
//    // 生成指纹
//    fun generateFingerprint(): String {
//        val info = collectDeviceInfo()
//        val json = com.google.gson.Gson().toJson(info)
//
//        // 计算 SHA256
//        val digest = MessageDigest.getInstance("SHA-256")
//        val hash = digest.digest(json.toByteArray())
//
//        return hash.joinToString("") { "%02x".format(it) }
//    }
//}
//
//
