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
 * Backstop for widgets whose Glance session is gone.
 *
 * Widgets normally repaint themselves: they collect `AppContainer.widgetData` inside their
 * composition, so a DB write reaches them with nothing pushed at them from outside. That is
 * the fast path, and it is the one that matters — re-provisioning every widget on the tap
 * path is what made each tap queue behind the previous tap's renders.
 *
 * Glance only holds a session open while the widget is live, though. A widget the launcher
 * re-provisions after a process death has no collector until something calls `updateAll`,
 * which is this class. The debounce is deliberately slack: this path is correctness
 * insurance, not latency, and a live widget has repainted long before it fires.
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
        /** Slack on purpose: live widgets have already repainted themselves by now. */
        const val DEBOUNCE_MS = 1_000L

        // ponytail: flat delay, no backoff — a persistently failing read costs one wakeup per 5s
        // and the 30-min updatePeriodMillis backstop still renders. Add backoff if it ever shows
        // up in battery stats.
        const val RETRY_DELAY_MS = 5_000L
    }
}
