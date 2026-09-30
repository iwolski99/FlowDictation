package com.flowdictation

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    companion object {
        const val DEFAULT_STT = "whisper-large-v3-turbo"
        const val DEFAULT_LLM = "openai/gpt-oss-20b"
    }

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("flow_prefs", Context.MODE_PRIVATE)

    private fun str(key: String, def: String): String = sp.getString(key, def) ?: def

    var groqKey: String
        get() = str("groq_key", "")
        set(v) { sp.edit().putString("groq_key", v.trim()).apply() }

    var sttModel: String
        get() = str("stt_model", DEFAULT_STT).ifBlank { DEFAULT_STT }
        set(v) { sp.edit().putString("stt_model", v.trim()).apply() }

    var llmModel: String
        get() = str("llm_model", DEFAULT_LLM).ifBlank { DEFAULT_LLM }
        set(v) { sp.edit().putString("llm_model", v.trim()).apply() }

    /** ISO code like "en". Blank = let Whisper auto-detect. */
    var language: String
        get() = str("language", "en")
        set(v) { sp.edit().putString("language", v.trim()).apply() }

    var cleanupEnabled: Boolean
        get() = sp.getBoolean("cleanup", true)
        set(v) { sp.edit().putBoolean("cleanup", v).apply() }

    var vocabulary: String
        get() = str("vocab", "")
        set(v) { sp.edit().putString("vocab", v.trim()).apply() }

    var extraInstructions: String
        get() = str("extra", "")
        set(v) { sp.edit().putString("extra", v.trim()).apply() }

    var cleanupTimeoutSec: Int
        get() = sp.getInt("cleanup_timeout", 4)
        set(v) { sp.edit().putInt("cleanup_timeout", v).apply() }

    var keyboardOnly: Boolean
        get() = sp.getBoolean("keyboard_only", true)
        set(v) { sp.edit().putBoolean("keyboard_only", v).apply() }

    var autoStart: Boolean
        get() = sp.getBoolean("auto_start", true)
        set(v) { sp.edit().putBoolean("auto_start", v).apply() }

    /** True when the user pressed Stop, so opening the app does not silently restart the engine. */
    var userStopped: Boolean
        get() = sp.getBoolean("user_stopped", false)
        set(v) { sp.edit().putBoolean("user_stopped", v).apply() }

    var micAsked: Boolean
        get() = sp.getBoolean("mic_asked", false)
        set(v) { sp.edit().putBoolean("mic_asked", v).apply() }

    var bubbleX: Int
        get() = sp.getInt("bubble_x", -1)
        set(v) { sp.edit().putInt("bubble_x", v).apply() }

    var bubbleY: Int
        get() = sp.getInt("bubble_y", -1)
        set(v) { sp.edit().putInt("bubble_y", v).apply() }
}
