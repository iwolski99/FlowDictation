package com.flowdictation

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

private val C_BG = Color.parseColor("#0E1116")
private val C_CARD = Color.parseColor("#171B22")
private val C_FIELD = Color.parseColor("#0E1116")
private val C_TEXT = Color.parseColor("#E8EAED")
private val C_SUB = Color.parseColor("#9AA0A6")
private val C_ACCENT = Color.parseColor("#4F8CFF")
private val C_OK = Color.parseColor("#3DD68C")
private val C_WARN = Color.parseColor("#F5A524")
private val C_BTN2 = Color.parseColor("#2A303A")

private class StepCard(
    val root: LinearLayout,
    val status: TextView,
    val primary: Button,
    val secondary: Button?
)

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var summary: TextView
    private lateinit var micCard: StepCard
    private var notifCard: StepCard? = null
    private lateinit var a11yCard: StepCard
    private lateinit var batteryCard: StepCard
    private lateinit var engineCard: StepCard

    private lateinit var keyField: EditText
    private lateinit var sttField: EditText
    private lateinit var llmField: EditText
    private lateinit var langField: EditText
    private lateinit var vocabField: EditText
    private lateinit var extraField: EditText
    private lateinit var timeoutField: EditText
    private lateinit var cleanupSwitch: Switch
    private lateinit var keyboardSwitch: Switch
    private lateinit var autoStartSwitch: Switch
    private lateinit var testResult: TextView

    private val REQ_MIC = 11
    private val REQ_NOTIF = 12

    // ---------- Lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        maybeAutoStart()
        refresh()
        handler.postDelayed({ refresh() }, 800)
    }

    override fun onPause() {
        saveSettings()
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        maybeAutoStart()
        refresh()
    }

    // ---------- UI construction ----------

    private fun buildUi() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(C_BG)
        scroll.isFillViewport = true
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(20), dp(16), dp(32))
        scroll.addView(root)

        root.addView(text("Flow Dictation", 28f, C_TEXT, true))
        root.addView(text("Tap the floating mic, talk, tap again. Your words are cleaned up and typed into any app.", 14f, C_SUB, false, top = 4))
        summary = text("", 15f, C_WARN, true, top = 14)
        root.addView(summary)

        root.addView(sectionTitle("1. Set up"))

        micCard = stepCard(
            "Microphone",
            "So the app can hear you.",
            "Allow microphone", { requestMic() }
        )
        root.addView(micCard.root)

        if (Build.VERSION.SDK_INT >= 33) {
            val c = stepCard(
                "Notifications",
                "Shows the small 'ready' notification while dictation is on.",
                "Allow notifications", { requestNotifications() }
            )
            notifCard = c
            root.addView(c.root)
        }

        a11yCard = stepCard(
            "Accessibility service",
            "Lets the floating button appear over other apps and type the text for you. " +
                "Open the settings, choose Installed apps (or Downloaded apps), tap Flow Dictation and turn it on.\n\n" +
                "If it says \"Restricted setting\" or the switch is greyed out: press Open app info below, " +
                "tap the three dots at the top right, choose Allow restricted settings, then try again. " +
                "(That menu only appears after you have tried to turn the service on once.)",
            "Open accessibility settings", { openAccessibilitySettings() },
            "Open app info", { openAppInfo() }
        )
        root.addView(a11yCard.root)

        batteryCard = stepCard(
            "Keep it running (recommended)",
            "Stops Samsung's battery saver from putting the app to sleep.",
            "Allow background use", { requestIgnoreBattery() }
        )
        root.addView(batteryCard.root)

        engineCard = stepCard(
            "Dictation engine",
            "Must be running for the floating mic to work. It starts by itself when you open this app. " +
                "After a restart, open the app once.",
            "Start", { toggleEngine() }
        )
        root.addView(engineCard.root)

        // ---- Groq settings ----
        root.addView(sectionTitle("2. Groq"))
        val groq = card()
        groq.addView(text("API key", 13f, C_SUB, false))
        keyField = input("Paste your Groq key (starts with gsk_)", prefs.groqKey, password = true)
        groq.addView(keyField)
        val keyButtons = LinearLayout(this)
        keyButtons.orientation = LinearLayout.HORIZONTAL
        val show = button("Show key", false) {
            val visible = (keyField.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD) == 0
            keyField.inputType = InputType.TYPE_CLASS_TEXT or
                (if (visible) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            keyField.setSelection(keyField.text.length)
        }
        val test = button("Save & test connection", true) { testConnection() }
        keyButtons.addView(show, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val gap = View(this)
        keyButtons.addView(gap, LinearLayout.LayoutParams(dp(8), 1))
        keyButtons.addView(test, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f))
        val kbLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        kbLp.topMargin = dp(10)
        groq.addView(keyButtons, kbLp)
        testResult = text("Get a free key at console.groq.com/keys", 13f, C_SUB, false, top = 10)
        groq.addView(testResult)
        root.addView(groq)

        // ---- Behaviour ----
        root.addView(sectionTitle("3. Behavior"))
        val beh = card()
        cleanupSwitch = switch("AI cleanup: punctuation, filler words, quotes, lists", prefs.cleanupEnabled)
        beh.addView(cleanupSwitch)
        keyboardSwitch = switch("Only show the mic button when the keyboard is open", prefs.keyboardOnly)
        beh.addView(keyboardSwitch, topLp(8))
        autoStartSwitch = switch("Start the engine when I open this app", prefs.autoStart)
        beh.addView(autoStartSwitch, topLp(8))

        beh.addView(text("Custom words (names, jargon), comma separated", 13f, C_SUB, false, top = 14))
        vocabField = input("e.g. Kubernetes, Priya, Wispr", prefs.vocabulary)
        beh.addView(vocabField)
        beh.addView(text("Extra instructions for the AI (optional)", 13f, C_SUB, false, top = 12))
        extraField = input("e.g. Use British spelling", prefs.extraInstructions)
        beh.addView(extraField)
        root.addView(beh)

        // ---- Advanced ----
        root.addView(sectionTitle("4. Advanced"))
        val adv = card()
        adv.addView(text("Transcription model", 13f, C_SUB, false))
        sttField = input(Prefs.DEFAULT_STT, prefs.sttModel)
        adv.addView(sttField)
        adv.addView(text("Cleanup model", 13f, C_SUB, false, top = 12))
        llmField = input(Prefs.DEFAULT_LLM, prefs.llmModel)
        adv.addView(llmField)
        adv.addView(text("Language code (en, es, fr...). Leave empty to auto-detect.", 13f, C_SUB, false, top = 12))
        langField = input("en", prefs.language)
        adv.addView(langField)
        adv.addView(text("Cleanup timeout in seconds. If the AI is slower than this, you get the raw text instead.", 13f, C_SUB, false, top = 12))
        timeoutField = input("4", prefs.cleanupTimeoutSec.toString(), number = true)
        adv.addView(timeoutField)
        val save = button("Save settings", true) {
            saveSettings()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }
        val saveLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        saveLp.topMargin = dp(14)
        adv.addView(save, saveLp)
        root.addView(adv)

        // ---- Try it ----
        root.addView(sectionTitle("5. Try it"))
        val tryCard = card()
        tryCard.addView(text("Tap the box below. When the keyboard opens, the mic bubble appears. Tap it, speak, tap again.", 13f, C_SUB, false))
        val tryField = input("Dictate here…", "", multiline = true)
        tryCard.addView(tryField, topLp(10))
        tryCard.addView(text(
            "Tips: drag the bubble anywhere. Long-press it while recording to cancel. " +
                "Say \"new line\", \"new paragraph\", \"bullet point\", or \"quote … end quote\" and it will format them.",
            13f, C_SUB, false, top = 10
        ))
        root.addView(tryCard)

        setContentView(scroll)
    }

    // ---------- Status refresh ----------

    private fun refresh() {
        val micOk = hasMic()
        setStatus(micCard, micOk, "Allowed", "Needed")
        micCard.primary.visibility = if (micOk) View.GONE else View.VISIBLE

        val nc = notifCard
        if (nc != null) {
            val ok = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            setStatus(nc, ok, "Allowed", "Recommended")
            nc.primary.visibility = if (ok) View.GONE else View.VISIBLE
        }

        val a11yOk = isAccessibilityEnabled()
        setStatus(a11yCard, a11yOk, "On", "Needed")
        a11yCard.secondary?.visibility = if (a11yOk) View.GONE else View.VISIBLE

        val batOk = isIgnoringBattery()
        setStatus(batteryCard, batOk, "Allowed", "Recommended")
        batteryCard.primary.visibility = if (batOk) View.GONE else View.VISIBLE

        val running = DictationService.isRunning
        setStatus(engineCard, running, "Running", "Stopped")
        engineCard.primary.text = if (running) "Stop" else "Start"

        val keyOk = prefs.groqKey.isNotBlank()
        if (micOk && a11yOk && running && keyOk) {
            summary.text = "✅ Ready. Open any text field and tap the mic bubble."
            summary.setTextColor(C_OK)
        } else {
            val todo = ArrayList<String>()
            if (!micOk) todo.add("microphone")
            if (!a11yOk) todo.add("accessibility")
            if (!keyOk) todo.add("Groq key")
            if (!running) todo.add("start the engine")
            summary.text = "Still to do: " + todo.joinToString(", ")
            summary.setTextColor(C_WARN)
        }
    }

    private fun setStatus(card: StepCard, ok: Boolean, okText: String, badText: String) {
        card.status.text = if (ok) "✓ $okText" else "• $badText"
        card.status.setTextColor(if (ok) C_OK else C_WARN)
    }

    // ---------- Actions ----------

    private fun hasMic(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMic() {
        if (hasMic()) return
        if (prefs.micAsked && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            // Android stopped showing the popup, so send the user to the app's permission page.
            Toast.makeText(this, "Open Permissions, then Microphone, then Allow", Toast.LENGTH_LONG).show()
            openAppInfo()
            return
        }
        prefs.micAsked = true
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openAppInfo() {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open app info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isIgnoringBattery(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBattery() {
        try {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = Uri.parse("package:$packageName")
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Open Settings, Apps, Flow Dictation, Battery, Unrestricted", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (DictationAccessibilityService.instance != null) return true
        val expected = ComponentName(this, DictationAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val cn = ComponentName.unflattenFromString(splitter.next())
            if (cn != null && cn == expected) return true
        }
        return false
    }

    private fun toggleEngine() {
        if (DictationService.isRunning) {
            prefs.userStopped = true
            DictationService.stop(this)
        } else {
            if (!hasMic()) {
                Toast.makeText(this, "Allow the microphone first", Toast.LENGTH_SHORT).show()
                requestMic()
                return
            }
            prefs.userStopped = false
            startEngine()
        }
        handler.postDelayed({ refresh() }, 400)
        handler.postDelayed({ refresh() }, 1200)
    }

    private fun startEngine() {
        try {
            DictationService.start(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't start the engine: " + e.javaClass.simpleName, Toast.LENGTH_LONG).show()
        }
    }

    private fun maybeAutoStart() {
        if (prefs.autoStart && !prefs.userStopped && !DictationService.isRunning &&
            hasMic() && prefs.groqKey.isNotBlank()
        ) {
            startEngine()
        }
    }

    private fun saveSettings() {
        prefs.groqKey = keyField.text.toString()
        prefs.sttModel = sttField.text.toString()
        prefs.llmModel = llmField.text.toString()
        prefs.language = langField.text.toString()
        prefs.vocabulary = vocabField.text.toString()
        prefs.extraInstructions = extraField.text.toString()
        prefs.cleanupTimeoutSec = (timeoutField.text.toString().trim().toIntOrNull() ?: 4).coerceIn(1, 20)
        prefs.cleanupEnabled = cleanupSwitch.isChecked
        prefs.keyboardOnly = keyboardSwitch.isChecked
        prefs.autoStart = autoStartSwitch.isChecked
        DictationAccessibilityService.instance?.refresh()
    }

    private fun testConnection() {
        saveSettings()
        val key = prefs.groqKey
        if (key.isBlank()) {
            testResult.text = "Paste your Groq API key first."
            testResult.setTextColor(C_WARN)
            return
        }
        testResult.text = "Testing…"
        testResult.setTextColor(C_SUB)
        val stt = prefs.sttModel
        val llm = prefs.llmModel
        val wantLlm = prefs.cleanupEnabled
        Thread {
            var ok = true
            val msg = try {
                val ids = GroqClient.listModels(key)
                val sb = StringBuilder()
                sb.append("✅ API key works (").append(ids.size).append(" models available)\n")
                if (ids.contains(stt)) {
                    sb.append("✅ Transcription model found: ").append(stt)
                } else {
                    ok = false
                    sb.append("⚠️ Transcription model not found: ").append(stt)
                }
                if (wantLlm) {
                    if (ids.contains(llm)) {
                        sb.append("\n✅ Cleanup model found: ").append(llm)
                    } else {
                        ok = false
                        val chat = ids.filter {
                            !it.contains("whisper") && !it.contains("guard") && !it.contains("tts") && !it.contains("orpheus")
                        }.take(8)
                        sb.append("\n⚠️ Cleanup model not found: ").append(llm)
                        sb.append("\nTry one of: ").append(chat.joinToString(", "))
                    }
                }
                sb.toString()
            } catch (e: Exception) {
                ok = false
                "❌ " + GroqClient.friendly(e)
            }
            runOnUiThread {
                testResult.text = msg
                testResult.setTextColor(if (ok) C_OK else C_WARN)
                refresh()
                maybeAutoStart()
            }
        }.start()
    }

    // ---------- View helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun roundBg(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.RECTANGLE
        d.setColor(color)
        d.cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) d.setStroke(dp(1), strokeColor)
        return d
    }

    private fun text(s: String, sizeSp: Float, color: Int, bold: Boolean, top: Int = 0): TextView {
        val t = TextView(this)
        t.text = s
        t.setTextColor(color)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.layoutParams = topLp(top)
        return t
    }

    private fun topLp(topDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(topDp)
        return lp
    }

    private fun sectionTitle(s: String): TextView {
        val t = text(s, 17f, C_TEXT, true, top = 24)
        return t
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = roundBg(C_CARD, 16)
        c.setPadding(dp(14), dp(14), dp(14), dp(14))
        val lp = topLp(10)
        c.layoutParams = lp
        return c
    }

    private fun button(label: String, primary: Boolean, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        b.background = roundBg(if (primary) C_ACCENT else C_BTN2, 12)
        b.stateListAnimator = null
        b.setPadding(dp(12), dp(10), dp(12), dp(10))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun input(hint: String, value: String, password: Boolean = false, number: Boolean = false, multiline: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setTextColor(C_TEXT)
        e.setHintTextColor(Color.parseColor("#5F6670"))
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        e.background = roundBg(C_FIELD, 10, Color.parseColor("#2A303A"))
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        e.setSingleLine(!multiline)
        if (multiline) {
            e.minLines = 3
            e.gravity = Gravity.TOP or Gravity.START
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else if (password) {
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        } else if (number) {
            e.inputType = InputType.TYPE_CLASS_NUMBER
        } else {
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        e.layoutParams = topLp(6)
        return e
    }

    private fun switch(label: String, checked: Boolean): Switch {
        val s = Switch(this)
        s.text = label
        s.setTextColor(C_TEXT)
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        s.isChecked = checked
        s.layoutParams = topLp(0)
        return s
    }

    private fun stepCard(
        title: String,
        desc: String,
        primaryLabel: String,
        onPrimary: () -> Unit,
        secondaryLabel: String? = null,
        onSecondary: (() -> Unit)? = null
    ): StepCard {
        val c = card()
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        val titleView = text(title, 16f, C_TEXT, true)
        val status = text("", 13f, C_WARN, true)
        status.gravity = Gravity.END
        header.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        c.addView(header)
        c.addView(text(desc, 13f, C_SUB, false, top = 6))
        val primary = button(primaryLabel, true, onPrimary)
        c.addView(primary, topLp(10))
        var secondary: Button? = null
        if (secondaryLabel != null && onSecondary != null) {
            secondary = button(secondaryLabel, false, onSecondary)
            c.addView(secondary, topLp(8))
        }
        return StepCard(c, status, primary, secondary)
    }
}
