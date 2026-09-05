package com.globalcall.app.calls

import android.content.Context
import com.globalcall.app.media.WebRtcCallEngine
import com.globalcall.app.model.CallSession

/**
 * Process-level owner for the active WebRTC engine.
 *
 * The call UI may be recreated when GlobalCall is minimized/restored. Keeping the
 * media engine here prevents a Compose/Activity disposal from tearing down an
 * otherwise active microphone/camera call. ActiveCallService keeps the process in
 * foreground priority while this engine is alive.
 */
object ActiveCallEngineStore {
    data class Snapshot(
        val callId: String = "",
        val state: String = "Preparing secure media…",
        val connected: Boolean = false,
        val connectedAtMs: Long = 0L,
        val error: String? = null
    )

    private val lock = Any()
    private var engine: WebRtcCallEngine? = null
    private var engineCallId: String = ""

    @Volatile private var state: String = "Preparing secure media…"
    @Volatile private var connected: Boolean = false
    @Volatile private var connectedAtMs: Long = 0L
    @Volatile private var error: String? = null

    fun obtain(context: Context, session: CallSession, uid: String): WebRtcCallEngine = synchronized(lock) {
        if (engine != null && engineCallId == session.callId) return@synchronized engine!!

        closeLocked()
        engineCallId = session.callId
        state = "Preparing secure media…"
        connected = false
        connectedAtMs = 0L
        error = null

        WebRtcCallEngine(
            context = context.applicationContext,
            callId = session.callId,
            uid = uid,
            outgoing = session.outgoing,
            video = session.video,
            onState = { next ->
                state = next
                if (next.contains("reconnect", ignoreCase = true)) connected = false
            },
            onConnected = {
                connected = true
                if (connectedAtMs == 0L) connectedAtMs = System.currentTimeMillis()
                state = "Connected"
                error = null
            },
            onError = { message ->
                error = message
                state = "Media connection problem"
            }
        ).also {
            engine = it
            it.start()
        }
    }

    fun current(callId: String): WebRtcCallEngine? = synchronized(lock) {
        engine?.takeIf { engineCallId == callId }
    }

    fun activeCallId(): String = synchronized(lock) {
        if (engine == null) "" else engineCallId
    }

    fun snapshot(callId: String): Snapshot = synchronized(lock) {
        if (engine == null || engineCallId != callId) return@synchronized Snapshot(callId = callId)
        Snapshot(
            callId = engineCallId,
            state = state,
            connected = connected,
            connectedAtMs = connectedAtMs,
            error = error
        )
    }

    fun clearError(callId: String) = synchronized(lock) {
        if (engineCallId == callId) error = null
    }

    fun close(callId: String = "") = synchronized(lock) {
        if (callId.isBlank() || engineCallId == callId) closeLocked()
    }

    private fun closeLocked() {
        runCatching { engine?.close() }
        engine = null
        engineCallId = ""
        state = "Preparing secure media…"
        connected = false
        connectedAtMs = 0L
        error = null
    }
}
