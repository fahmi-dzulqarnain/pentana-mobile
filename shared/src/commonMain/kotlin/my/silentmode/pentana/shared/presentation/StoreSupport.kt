package my.silentmode.pentana.shared.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared mechanics for the presentation stores. Internal on purpose — the stores expose only
 * concrete sealed states and plain members, keeping the SKIE-generated Swift surface simple.
 */

/**
 * Overlap-safe refresh tracking: the [refreshing] flag stays up until the LAST overlapping
 * refresh completes. Runs on Dispatchers.Main so the active-count is confined to one thread
 * (callers may be Swift-async threads via SKIE).
 */
internal class RefreshTracker(private val refreshing: MutableStateFlow<Boolean>) {
    private var activeCount = 0

    suspend fun run(fetch: suspend () -> Unit) = withContext(Dispatchers.Main) {
        activeCount += 1
        refreshing.value = true
        try {
            fetch()
        } finally {
            activeCount -= 1
            if (activeCount == 0) refreshing.value = false
        }
    }
}

/**
 * Guarded fire-and-forget per-id action: synchronous in-flight check-and-set BEFORE launching
 * (a plain Main dispatch would arm the guard a turn too late), failure surfaced via
 * [actionError], id always removed in finally. A guarded duplicate returns before touching
 * [actionError] — it must not wipe an error the user is reading.
 */
internal fun runGuardedAction(
    scope: CoroutineScope,
    inFlight: MutableStateFlow<Set<Long>>,
    actionError: MutableStateFlow<String?>,
    id: Long,
    errorMessage: String,
    action: suspend () -> Unit,
) {
    if (id in inFlight.value) return
    inFlight.value = inFlight.value + id
    actionError.value = null
    scope.launch {
        try {
            action()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            actionError.value = errorMessage
        } finally {
            inFlight.value = inFlight.value - id
        }
    }
}
