package com.torque.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.nehuatl.llamacpp.LlamaHelper

/**
 * A real local GGUF inference adapter. No cloud endpoints or API keys.
 * The user imports a licensed GGUF with Android's document picker.
 * This class does not label the scripted fallback as an LLM.
 *
 * Native initialization/inference must be measured on the user's phone.
 */
class TorqueLocalBrain(context: Context, private val callbacks: Callbacks) {
    interface Callbacks {
        fun modelReady(description: String)
        fun answer(text: String, durationMs: Long, tokens: Int)
        fun failed(reason: String)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = MutableSharedFlow<LlamaHelper.LLMEvent>(
        replay = 0, extraBufferCapacity = 1024
    )
    private val helper = LlamaHelper(context.contentResolver, scope, events)
    @Volatile private var loaded = false
    @Volatile private var busy = false
    @Volatile private var disposed = false
    private val output = StringBuilder()

    init {
        scope.launch {
            events.collect { event ->
                if (disposed) return@collect
                when (event) {
                    is LlamaHelper.LLMEvent.Loaded -> {
                        loaded = true
                        handler.post { if (!disposed) callbacks.modelReady(event.path) }
                    }
                    is LlamaHelper.LLMEvent.Started -> {
                        synchronized(output) { output.setLength(0) }
                    }
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        synchronized(output) { output.append(event.word) }
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        val completed = synchronized(output) { output.toString().trim() }
                        busy = false
                        handler.post {
                            if (!disposed) {
                                if (completed.isNotBlank()) callbacks.answer(completed, event.duration, event.tokenCount)
                                else callbacks.failed("Local model produced no speech.")
                            }
                        }
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        busy = false
                        loaded = false
                        handler.post { if (!disposed) callbacks.failed(event.message) }
                    }
                }
            }
        }
    }

    fun isReady(): Boolean = loaded && !disposed
    fun isBusy(): Boolean = busy
    fun load(uri: String) {
        if (disposed) return
        if (busy) {
            callbacks.failed("Finish the current reply before loading another model.")
            return
        }
        loaded = false
        try {
            helper.load(path = uri, contextLength = 2048) {
                // The Loaded event is the authoritative ready signal.
            }
        } catch (t: Throwable) {
            handler.post { if (!disposed) callbacks.failed("Model load: ${t.message ?: t.javaClass.simpleName}") }
        }
    }

    fun answer(prompt: String) {
        if (!isReady() || busy) {
            callbacks.failed("The local GGUF model is not ready.")
            return
        }
        busy = true
        try {
            helper.predict(prompt, partialCompletion = true)
        } catch (t: Throwable) {
            busy = false
            handler.post { if (!disposed) callbacks.failed("Inference: ${t.message ?: t.javaClass.simpleName}") }
        }
    }

    fun interrupt() {
        if (disposed) return
        busy = false
        try { helper.stopPrediction() } catch (_: Throwable) { }
    }

    fun release() {
        disposed = true
        busy = false
        loaded = false
        try { helper.abort(); helper.release() } catch (_: Throwable) { }
        scope.cancel()
    }
}
