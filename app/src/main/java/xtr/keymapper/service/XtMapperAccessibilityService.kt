package xtr.keymapper.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import xtr.keymapper.accessibility.AccessibilityInputDispatcher

class XtMapperAccessibilityService : AccessibilityService() {

    private val mHandler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "XtMapperAccessibilityService connected")

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 50
        serviceInfo = info
    }

    override fun onDestroy() {
        Log.i(TAG, "XtMapperAccessibilityService destroyed")
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrEmpty() && pkg != lastForegroundPackage) {
                lastForegroundPackage = pkg
                Log.d(TAG, "Foreground app changed: $pkg")
                foregroundAppListener?.invoke(pkg)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "XtMapperAccessibilityService onInterrupt")
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        Log.d(TAG, "onKeyEvent: keyCode=${event.keyCode}, action=${event.action}, hasDispatcher=${inputDispatcher != null}")
        val dispatcher = inputDispatcher
        if (dispatcher != null) {
            val handled = dispatcher.handleKeyEvent(event)
            if (handled) {
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    fun dispatchTouch(x: Float, y: Float, action: Int, pointerId: Int) {
        if (action == 0) {
            // In Android AccessibilityService.dispatchGesture, a single tap stroke covers DOWN+UP.
            // Ignore key UP event to prevent unintended double-taps.
            return
        }
        val safeX = x.coerceAtLeast(0f)
        val safeY = y.coerceAtLeast(0f)
        val path = Path()
        path.moveTo(safeX, safeY)
        path.lineTo(safeX, safeY)

        val duration = when (action) {
            1 -> 60L // DOWN / tap
            2 -> 50L // MOVE
            else -> 60L
        }

        try {
            Log.d(TAG, "dispatchGesture tap at ($safeX, $safeY), duration=${duration}ms")
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "dispatchGesture onCompleted at ($safeX, $safeY)")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "dispatchGesture onCancelled at ($safeX, $safeY)")
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatchGesture: ${e.message}", e)
        }
    }

    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 100L) {
        val path = Path()
        path.moveTo(startX.coerceAtLeast(0f), startY.coerceAtLeast(0f))
        path.lineTo(endX.coerceAtLeast(0f), endY.coerceAtLeast(0f))

        try {
            val builder = GestureDescription.Builder()
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(20L)))
            dispatchGesture(builder.build(), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatchSwipe: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "XtMapperA11y"

        @Volatile
        var instance: XtMapperAccessibilityService? = null
            private set

        @Volatile
        var inputDispatcher: AccessibilityInputDispatcher? = null

        @Volatile
        var foregroundAppListener: ((packageName: String) -> Unit)? = null

        fun isEnabled(context: Context): Boolean {
            val expectedServiceName = ComponentName(context, XtMapperAccessibilityService::class.java).flattenToString()
            val expectedShortName = ComponentName(context, XtMapperAccessibilityService::class.java).flattenToShortString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedServiceName, ignoreCase = true) ||
                    componentName.equals(expectedShortName, ignoreCase = true) ||
                    componentName.endsWith("/" + XtMapperAccessibilityService::class.java.name) ||
                    componentName.endsWith("/.service.XtMapperAccessibilityService")
                ) {
                    return true
                }
            }
            return false
        }
    }
}
