package com.guiderun.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Wraps Fragment's registerForActivityResult for multiple permissions.
 * Must be instantiated in Fragment.onCreate() (before onStart).
 *
 * Usage:
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

object AppPermissions {
    const val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    const val COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
    const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    const val CALL_PHONE = Manifest.permission.CALL_PHONE
    const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

    val LOCATION = arrayOf(FINE_LOCATION, COARSE_LOCATION)

    val BLIND_CORE = arrayOf(FINE_LOCATION, COARSE_LOCATION, RECORD_AUDIO, POST_NOTIFICATIONS)
    val VOLUNTEER_CORE = arrayOf(FINE_LOCATION, COARSE_LOCATION, POST_NOTIFICATIONS, CALL_PHONE)
}
