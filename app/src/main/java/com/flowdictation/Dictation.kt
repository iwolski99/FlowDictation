package com.flowdictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

/** What we know about the text field the user was in when they started dictating. */
data class FieldContext(val packageName: String?, val hint: String?)

/**
 * Central controller: record -> transcribe -> clean up -> hand text to the accessibility service.
 * All state changes happen on the main thread.
 */
object Dictation {
    enum class State { IDLE, RECORDING, PROCESSING }

    /** Implemented by the accessibility service, which owns the screen. */
    interface Target {
        fun captureContext(): FieldContext?
        fun deliver(text: String, context: FieldContext?)
    }

    private sealed class Outcome {
        object NoSpeech : Outcome()
        class Text(val text: String, val note: String?) : Outcome()
        class Failure(val message: String) : Outcome()
    }

    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val recorder = Recorder()
    private var fieldContext: FieldContext? = null

    @Volatile
    var state: State = State.IDLE
        private set

    var target: Target? = null
    var stateListener: ((State) -> Unit)? = null

    private fun setState(s: State) {
        state = s
        stateListener?.invoke(s)
    }

    // ---------- Called by the floating button ----------

    fun onBubbleTap(ctx: Context) {
        val app = ctx.applicationContext
        when (state) {
            State.IDLE -> startRecording(app)
            State.RECORDING -> stopAndProcess(app)
            State.PROCESSING -> toast(app, "Still finishing the last one…")
        }
    }

    fun onBubbleLongPress(ctx: Context) {
        if (state == State.RECORDING) {
            recorder.cancel()
            setState(State.IDLE)
            toast(ctx.applicationContext, "Recording cancelled")
        }
    }

    /** Called when the engine service or accessibility service goes away. */
    fun reset() {
        if (state == State.RECORDING) {
            recorder.cancel()
            setState(State.IDLE)
        }
    }

    // ---------- Recording ----------

    private fun startRecording(app: Context) {
        val prefs = Prefs(app)
        if (prefs.groqKey.isBlank()) {
            toast(app, "Add your Groq API key in the Flow Dictation app first")
            return
        }
        if (!DictationService.isRunning) {
            toast(app, "Dictation engine is off. Open Flow Dictation and tap Start.")
            return
        }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast(app, "Microphone permission is missing. Open Flow Dictation to allow it.")
            return
        }
        fieldContext = try { target?.captureContext() } catch (e: Exception) { null }
        recorder.onMaxLength = {
            main.post { if (state == State.RECORDING) stopAndProcess(app) }
        }
        if (!recorder.start()) {
            toast(app, "Couldn't open the microphone. Is another app using it?")
            return
        }
        setState(State.RECORDING)
    }

    private fun stopAndProcess(app: Context) {
        val pcm = recorder.stop()
        setState(State.PROCESSING)
        val prefs = Prefs(app)
        val fc = fieldContext
        executor.execute {
            val outcome = try {
                runPipeline(pcm, prefs, fc)
            } catch (e: Exception) {
                Outcome.Failure("Something went wrong: " + e.javaClass.simpleName)
            }
            main.post {
                setState(State.IDLE)
                when (outcome) {
                    is Outcome.NoSpeech -> toast(app, "Didn't catch anything")
                    is Outcome.Failure -> toast(app, outcome.message)
                    is Outcome.Text -> {
                        val t = target
                        if (t == null) {
                            toast(app, "Accessibility service isn't running")
                        } else {
                            t.deliver(outcome.text, fc)
                            if (outcome.note != null) toast(app, outcome.note)
                        }
                    }
                }
            }
        }
    }

    // ---------- Background pipeline ----------

    private fun runPipeline(pcm: ByteArray, prefs: Prefs, fc: FieldContext?): Outcome {
        val speech = Pcm.trimToSpeech(pcm) ?: return Outcome.NoSpeech
        val wav = Pcm.toWav(speech)

        val raw = try {
            GroqClient.transcribe(prefs.groqKey, prefs.sttModel, wav, prefs.language, prefs.vocabulary)
        } catch (e: Exception) {
            return Outcome.Failure("Transcription failed: " + GroqClient.friendly(e))
        }
        if (raw.isBlank()) return Outcome.NoSpeech

        var text = LocalRules.apply(raw)
        var note: String? = null
        if (prefs.cleanupEnabled) {
            try {
                text = Cleanup.polish(prefs, raw, text, fc)
            } catch (e: Exception) {
                note = "AI cleanup skipped (" + GroqClient.friendly(e) + ")"
            }
        }
        if (text.isBlank()) return Outcome.NoSpeech
        return Outcome.Text(text, note)
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
