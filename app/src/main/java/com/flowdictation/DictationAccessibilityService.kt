package com.flowdictation

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

/**
 * Owns the floating button and types the finished text into whatever field has focus.
 * It never reads or stores what is on screen; it only looks for the focused input field.
 */
class DictationAccessibilityService : AccessibilityService(), Dictation.Target {
    companion object {
        @Volatile
        var instance: DictationAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private lateinit var wm: WindowManager
    private var bubble: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private var attached = false
    private var sizePx = 0

    private val visibilityRunnable = Runnable { applyVisibility() }

    // ---------- Lifecycle ----------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = Prefs(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sizePx = dp(60)

        val b = BubbleView(this)
        bubble = b
        val lp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val dm = resources.displayMetrics
        lp.x = if (prefs.bubbleX >= 0) prefs.bubbleX else dm.widthPixels - sizePx - dp(4)
        lp.y = if (prefs.bubbleY >= 0) prefs.bubbleY else (dm.heightPixels * 0.5f).toInt()
        params = lp
        clampPosition()
        attachTouchHandling(b)

        Dictation.target = this
        Dictation.stateListener = { s ->
            bubble?.setBubbleState(s)
            if (s != Dictation.State.IDLE) haptic()
            refresh()
        }
        refresh()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        handler.removeCallbacks(visibilityRunnable)
        setAttached(false)
        Dictation.reset()
        if (Dictation.target === this) Dictation.target = null
        Dictation.stateListener = null
        if (instance === this) instance = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        clampPosition()
        if (attached) {
            try { wm.updateViewLayout(bubble, params) } catch (e: Exception) { /* ignore */ }
        }
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> refresh()
        }
    }

    // ---------- Showing / hiding the bubble ----------

    /** Re-evaluates whether the bubble should be on screen (debounced). */
    fun refresh() {
        handler.removeCallbacks(visibilityRunnable)
        handler.postDelayed(visibilityRunnable, 120)
    }

    private fun applyVisibility() {
        val b = bubble ?: return
        val busy = Dictation.state != Dictation.State.IDLE
        val show = busy || !prefs.keyboardOnly || keyboardVisible()
        b.alpha = if (DictationService.isRunning || busy) 1f else 0.5f
        setAttached(show)
    }

    private fun keyboardVisible(): Boolean {
        return try {
            val all = windows
            if (all.isEmpty()) true // can't tell, so don't hide the button
            else all.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        } catch (e: Exception) {
            true
        }
    }

    private fun setAttached(show: Boolean) {
        val b = bubble ?: return
        if (show && !attached) {
            try {
                wm.addView(b, params)
                attached = true
            } catch (e: Exception) {
                attached = false
            }
        } else if (!show && attached) {
            try { wm.removeView(b) } catch (e: Exception) { /* ignore */ }
            attached = false
        }
    }

    // ---------- Dragging and tapping ----------

    private fun attachTouchHandling(view: BubbleView) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var longPressed = false
        val longPress = Runnable {
            longPressed = true
            Dictation.onBubbleLongPress(this)
        }

        view.setOnTouchListener { _, e ->
            val lp = params ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    longPressed = false
                    handler.postDelayed(longPress, 650)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        moved = true
                        handler.removeCallbacks(longPress)
                    }
                    if (moved) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        clampPosition()
                        try { wm.updateViewLayout(view, lp) } catch (ex: Exception) { /* ignore */ }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (moved) {
                        snapToEdge()
                        try { wm.updateViewLayout(view, lp) } catch (ex: Exception) { /* ignore */ }
                        prefs.bubbleX = lp.x
                        prefs.bubbleY = lp.y
                    } else if (!longPressed) {
                        Dictation.onBubbleTap(this)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    true
                }
                else -> false
            }
        }
    }

    private fun clampPosition() {
        val lp = params ?: return
        val dm = resources.displayMetrics
        lp.x = lp.x.coerceIn(0, Math.max(0, dm.widthPixels - sizePx))
        lp.y = lp.y.coerceIn(0, Math.max(0, dm.heightPixels - sizePx))
    }

    private fun snapToEdge() {
        val lp = params ?: return
        val dm = resources.displayMetrics
        val margin = dp(4)
        lp.x = if (lp.x + sizePx / 2 < dm.widthPixels / 2) margin else dm.widthPixels - sizePx - margin
        clampPosition()
    }

    private fun haptic() {
        try {
            bubble?.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        } catch (e: Exception) {
            // not important
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    // ---------- Dictation.Target ----------

    override fun captureContext(): FieldContext? {
        val f = findInputFocus() ?: return null
        return FieldContext(f.packageName?.toString(), f.hintText?.toString())
    }

    override fun deliver(text: String, context: FieldContext?) {
        val node = findInputFocus()
        if (node == null) {
            copyToClipboard(text)
            toast("No text field selected. Copied to clipboard.")
            return
        }
        if (node.isPassword) {
            toast("Not typing into a password field")
            return
        }
        val payload = spacingPrefix(node) + text
        copyToClipboard(payload)
        // Small delay so the clipboard write has settled before the target app reads it.
        handler.postDelayed({
            val current = findInputFocus() ?: node
            val pasted = current.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (!pasted) {
                if (!setTextFallback(current, text)) {
                    toast("Couldn't type here. Text copied, so you can paste it.")
                }
            }
        }, 80)
    }

    // ---------- Finding the field and inserting ----------

    private fun findInputFocus(): AccessibilityNodeInfo? {
        try {
            val direct = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (direct != null) return direct
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val found = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (found != null) return found
            }
        } catch (e: Exception) {
            // fall through
        }
        return null
    }

    /** Adds a space before the text when the cursor sits right after a word. */
    private fun spacingPrefix(node: AccessibilityNodeInfo): String {
        if (node.isShowingHintText) return ""
        val t = node.text ?: return ""
        if (t.isEmpty()) return ""
        val start = node.textSelectionStart
        val pos = if (start in 0..t.length) start else t.length
        if (pos == 0) return ""
        val before = t[pos - 1]
        return if (before.isWhitespace() || before == '(' || before == '[' || before == '{' ||
            before == '\u201C' || before == '\u2018'
        ) "" else " "
    }

    /** Backup method for apps that ignore paste: rewrite the whole field with the text spliced in. */
    private fun setTextFallback(node: AccessibilityNodeInfo, text: String): Boolean {
        val existing = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        var s = node.textSelectionStart
        var e = node.textSelectionEnd
        if (s < 0 || e < 0 || s > existing.length || e > existing.length) {
            s = existing.length
            e = existing.length
        }
        if (s > e) {
            val tmp = s
            s = e
            e = tmp
        }
        val prefix = if (s > 0 && !existing[s - 1].isWhitespace()) " " else ""
        val newText = existing.substring(0, s) + prefix + text + existing.substring(e)
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (ok) {
            val cursor = s + prefix.length + text.length
            val sel = Bundle()
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
        }
        return ok
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("dictation", text)
            val extras = PersistableBundle()
            extras.putBoolean("android.content.extra.IS_SENSITIVE", true) // hides the clipboard preview popup
            clip.description.extras = extras
            cm.setPrimaryClip(clip)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}
