package com.guiderun.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Fragment 多权限申请封装。
 *
 * 包装 [ActivityResultContracts.RequestMultiplePermissions]，简化"检查 → 申请 → 回调"流程。
 * 必须在 Fragment.onCreate()（onStart 之前）实例化，确保 ActivityResultLauncher 注册在正确的生命周期节点。
 *
 * 使用示例：
 *   private lateinit var permissionHelper: PermissionHelper
 *   override fun onCreate(savedInstanceState: Bundle?) {
 *       super.onCreate(savedInstanceState)
 *       permissionHelper = PermissionHelper(this) { allGranted ->
 *           if (allGranted) startLocationUpdates()
 *       }
 *   }
 *   fun checkAndRequest() {
 *       if (permissionHelper.areGranted(*AppPermissions.LOCATION)) {
 *           startLocationUpdates()
 *       } else {
 *           permissionHelper.request(*AppPermissions.LOCATION)
 *       }
 *   }
 */
class PermissionHelper(
    fragment: Fragment,
    private val onResult: (allGranted: Boolean, results: Map<String, Boolean>) -> Unit,
) {
    private val launcher: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            onResult(results.values.all { it }, results)
        }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun areGranted(context: Context, vararg permissions: String): Boolean =
        permissions.all { isGranted(context, it) }

    fun request(vararg permissions: String) {
        launcher.launch(arrayOf(*permissions))
    }
}

/**
 * 项目中所有权限声明的集中管理。
 *
 * 双端权限分组：
 * - [BLIND_CORE]：视障端启动时一次性申请（定位 + 录音 + 通知 + 拨号）
 * - [VOLUNTEER_CORE]：志愿者端启动时一次性申请（定位 + 通知 + 拨号，不含录音）
 * 后台定位（BACKGROUND_LOCATION）需用户单独授予，在 BlindRunning.onResume 单独申请。
 */
object AppPermissions {
    const val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    const val COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    const val CALL_PHONE = Manifest.permission.CALL_PHONE
    @SuppressLint("InlinedApi")
    const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    val LOCATION = arrayOf(FINE_LOCATION, COARSE_LOCATION)

    val BLIND_CORE = arrayOf(FINE_LOCATION, COARSE_LOCATION, RECORD_AUDIO, POST_NOTIFICATIONS, CALL_PHONE)
    val VOLUNTEER_CORE = arrayOf(FINE_LOCATION, COARSE_LOCATION, POST_NOTIFICATIONS, CALL_PHONE)
}
