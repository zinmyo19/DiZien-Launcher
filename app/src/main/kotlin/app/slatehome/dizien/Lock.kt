package app.slatehome.dizien

import android.accessibilityservice.AccessibilityService
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Optional "lock the screen" helper for the double-tap gesture. It listens to no events and reads no screen content;
 * the only thing it can do here is ask Android to lock the screen (Android 9+).
 */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); inst = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { }
    override fun onInterrupt() { }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    companion object { @Volatile var inst: LockService? = null }
}

/** Device-admin fallback (Android 7 and 8): only the "lock the screen" policy is requested. */
class LockAdmin : DeviceAdminReceiver()

object ScreenLock {
    private fun adminComp(ctx: Context) = ComponentName(ctx, LockAdmin::class.java)
    fun a11yComp(ctx: Context) = ComponentName(ctx, LockService::class.java)

    fun a11yEnabled(ctx: Context): Boolean {
        if (LockService.inst != null) return true
        val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = a11yComp(ctx)
        return s.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
    fun adminActive(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(adminComp(ctx))
    } catch (_: Throwable) { false }
    fun ready(ctx: Context) = (android.os.Build.VERSION.SDK_INT >= 28 && a11yEnabled(ctx)) || adminActive(ctx)

    fun removeAdmin(ctx: Context) {
        try { (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).removeActiveAdmin(adminComp(ctx)) } catch (_: Throwable) { }
    }
    fun adminIntent(ctx: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp(ctx))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Only used to lock the screen when you double-tap Home. DiZien Launcher cannot wipe, unlock or read anything.")

    /** Locks the screen with the best method that is switched on. Returns false if none is. */
    fun lock(ctx: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            val s = LockService.inst
            if (s != null && s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) return true
        }
        if (adminActive(ctx)) {
            return try {
                (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow(); true
            } catch (_: Throwable) { false }
        }
        return false
    }
}
