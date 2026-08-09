package com.jktdeveloper.habitto.widget

import android.content.Context
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch

/**
 * Refresh source (1) of 3: DB writes.
 *
 * Watches the repository Flows the app already exposes and re-renders all four widgets
 * whenever any of them change, instead of every mutation site (LogHabitAction, ViewModels,
 * timer start/cancel, ...) poking the widgets itself. A burst of writes from a single user
 * action that touches several tables coalesces into one update via [debounce].
 *
 * This intentionally does NOT cover the want-timer's live point drain while it is running —
 * that's time-derived, not DB-derived (points are only written when the timer ends), so this
 * collector never fires during a run. See the per-minute tick in WantTimerService instead.
 */
class WidgetRefresher(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        scope.launch {
            container.authState
                .map { it.userId }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    combine(
                        container.habitLogRepository.observeAllActiveLogsForUser(userId),
                        container.wantLogRepository.observeAllActiveLogsForUser(userId),
                        container.habitRepository.observeHabitsForUser(userId),
                        container.wantActivityRepository.observeWantActivities(userId),
                    ) { _, _, _, _ -> Unit }
                }
                .debounce(DEBOUNCE_MS)
                // The runCatching below guards only the render. A throw from any of the four
                // SQLDelight Flows — or from combine/flatMapLatest — would escape .collect into
                // this GlobalScope launch, which has no parent job and no CoroutineExceptionHandler,
                // and kill the process. Retrying re-subscribes instead, so a transient DB fault
                // costs one refresh rather than the app.
                .retry {
                    delay(RETRY_DELAY_MS)
                    true
                }
                .collect {
                    runCatching { WidgetUpdates.updateAll(context) }
                }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L

        // ponytail: flat delay, no backoff — a persistently failing read costs one wakeup per 5s
        // and the 30-min updatePeriodMillis backstop still renders. Add backoff if it ever shows
        // up in battery stats.
        const val RETRY_DELAY_MS = 5_000L
    }
}
