package com.cascalc.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * V7's home-screen widget: the last result, and a tap target to open the app.
 *
 * Deliberately not a full keypad. A `RemoteViews` calculator would need a
 * pending intent per key and a round trip through the widget host for every
 * digit, which is slower and more fragile than opening the app — and the app
 * opens on the calculator screen anyway.
 */
class CalculatorWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val store = HistoryStore(context)
        val latest = store.load().firstOrNull()

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_calculator).apply {
                setTextViewText(R.id.widget_input, latest?.input ?: "Tap to calculate")
                setTextViewText(R.id.widget_result, latest?.result?.exact.orEmpty())
                setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun launchIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Refreshes every widget; called after a calculation is recorded. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, CalculatorWidget::class.java),
            )
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, CalculatorWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
