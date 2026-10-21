package xtr.keymapper.accessibility

import android.util.Log
import android.view.KeyEvent
import xtr.keymapper.IRemoteServiceCallback
import xtr.keymapper.keymap.KeymapConfig
import xtr.keymapper.keymap.KeymapProfile
import xtr.keymapper.server.IInputInterface
import xtr.keymapper.server.TouchJitter
import xtr.keymapper.server.event.KeyEventHandler
import xtr.keymapper.server.event.MouseEventHandler
import xtr.keymapper.service.XtMapperAccessibilityService

class AccessibilityInputDispatcher(
    private var profile: KeymapProfile,
    private var keymapConfig: KeymapConfig,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val callback: IRemoteServiceCallback? = null
) : IInputInterface {

    private val touchJitter: TouchJitter = TouchJitter(keymapConfig.touchJitter, keymapConfig.touchJitterSpread)
    private val mouseEventHandler: MouseEventHandler
    private val keyEventHandler: KeyEventHandler
    private var isPaused = false

    init {
        profile.scale(screenWidth.toFloat(), screenHeight.toFloat())
        touchJitter.setScreenDimensions(screenWidth, screenHeight)

        mouseEventHandler = MouseEventHandler(this)
        mouseEventHandler.init(screenWidth, screenHeight)

        keyEventHandler = KeyEventHandler(this)
        keyEventHandler.init()
    }

    fun updateProfile(newProfile: KeymapProfile, newConfig: KeymapConfig) {
        newProfile.scale(screenWidth.toFloat(), screenHeight.toFloat())
        this.profile = newProfile
        this.keymapConfig = newConfig
        this.touchJitter.updateConfig(newConfig.touchJitter, newConfig.touchJitterSpread)
        this.touchJitter.setScreenDimensions(screenWidth, screenHeight)

        this.keyEventHandler.stop()
        this.mouseEventHandler.stop()
        this.keyEventHandler.init()
        this.mouseEventHandler.init(screenWidth, screenHeight)
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (isPaused) return false

        val keyCodeName = KeyEvent.keyCodeToString(event.keyCode)
        val keyStr = if (keyCodeName.startsWith("KEYCODE_")) {
            keyCodeName.replace("KEYCODE_", "KEY_")
        } else {
            "KEY_$keyCodeName"
        }

        val actionStr = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
        val line = "/dev/input/event0 EV_KEY $keyStr $actionStr"

        val isMapped = isKeyMappedInProfile(keyStr)
        Log.d(TAG, "handleKeyEvent: keyCode=${event.keyCode}, keyStr=$keyStr, action=$actionStr, isMapped=$isMapped")

        try {
            keyEventHandler.handleEvent(line)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling key event: ${e.message}", e)
        }

        return isMapped
    }

    private fun isKeyMappedInProfile(keyStr: String): Boolean {
        // Keys
        for (k in profile.keys) {
            if (keyStr.equals(k.code, ignoreCase = true)) return true
        }
        // Dpads
        for (dpad in profile.dpadArray) {
            if (dpad != null && dpad.keycodes != null) {
                if (keyStr.equals(dpad.keycodes.Up, ignoreCase = true) ||
                    keyStr.equals(dpad.keycodes.Down, ignoreCase = true) ||
                    keyStr.equals(dpad.keycodes.Left, ignoreCase = true) ||
                    keyStr.equals(dpad.keycodes.Right, ignoreCase = true)
                ) {
                    return true
                }
            }
        }
        // Swipe keys
        for (swipe in profile.swipeKeys) {
            if (swipe.key1 != null && keyStr.equals(swipe.key1.code, ignoreCase = true)) return true
            if (swipe.key2 != null && keyStr.equals(swipe.key2.code, ignoreCase = true)) return true
        }
        // Macros
        for (entry in profile.macroIdMap.values) {
            val trig = entry.triggerKey
            if (trig != null) {
                val fullKey = if (trig.startsWith("KEY_")) trig else "KEY_$trig"
                if (keyStr.equals(fullKey, ignoreCase = true)) return true
            }
        }
        // Shortcuts
        if (keyStr.equals(keymapConfig.pauseResumeShortcutKey, ignoreCase = true)) return true
        if (keyStr.equals(keymapConfig.launchEditorShortcutKey, ignoreCase = true)) return true
        if (keyStr.equals(keymapConfig.switchProfileShortcutKey, ignoreCase = true)) return true

        return false
    }

    fun stop() {
        touchJitter.clear()
        keyEventHandler.stop()
        mouseEventHandler.stop()
    }

    override fun injectEvent(x: Float, y: Float, action: Int, pointerId: Int) {
        val wrapped = touchJitter.wrapCoordinates(x, y, action, pointerId)
        Log.d(TAG, "injectEvent: orig=($x, $y) -> jittered=(${wrapped.x}, ${wrapped.y}), action=$action, pid=$pointerId")
        XtMapperAccessibilityService.instance?.dispatchTouch(wrapped.x, wrapped.y, action, pointerId)
    }

    override fun injectHoverEvent(x: Float, y: Float, pointerId: Int) {
        // No-op for accessibility gestures
    }

    override fun injectRightClickEvent(x: Float, y: Float, pointerId: Int, pressed: Boolean) {
        if (pressed) {
            injectEvent(x, y, 1, pointerId)
        } else {
            injectEvent(x, y, 0, pointerId)
        }
    }

    override fun injectMiddleClickEvent(x: Float, y: Float, pointerId: Int, pressed: Boolean) {
        if (pressed) {
            injectEvent(x, y, 1, pointerId)
        } else {
            injectEvent(x, y, 0, pointerId)
        }
    }

    override fun injectScroll(x: Float, y: Float, value: Float) {
        val startY = y
        val endY = y - (value * 200f)
        XtMapperAccessibilityService.instance?.dispatchSwipe(x, startY, x, endY, 60L)
    }

    override fun pauseResumeKeymap() {
        isPaused = !isPaused
    }

    override fun getKeymapConfig(): KeymapConfig = keymapConfig

    override fun getKeyEventHandler(): KeyEventHandler = keyEventHandler

    override fun getMouseEventHandler(): MouseEventHandler = mouseEventHandler

    override fun getKeymapProfile(): KeymapProfile = profile

    override fun getCallback(): IRemoteServiceCallback? = callback

    override fun moveCursorX(x: Int) {}

    override fun moveCursorY(y: Int) {}

    override fun hideCursor() {}

    override fun showCursor() {}

    override fun getTouchJitter(): TouchJitter = touchJitter

    companion object {
        private const val TAG = "A11yInputDispatcher"
    }
}
