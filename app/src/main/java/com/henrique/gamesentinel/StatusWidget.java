package com.henrique.gamesentinel;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

public class StatusWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(context, StatusWidget.class));
        for (int id : ids) {
            updateAppWidget(context, appWidgetManager, id);
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_status);
        
        // Intent para abrir o app ao clicar no widget
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_status, pendingIntent);

        // Atualizar textos baseados no status global
        AlarmForegroundService.Status status = AlarmForegroundService.ultimoStatus;
        String jogo = AlarmForegroundService.jogoAtivo;
        
        String statusText;
        int color;
        
        switch (status) {
            case CONECTADO:
                statusText = context.getString(R.string.status_conectado) + (jogo != null ? " - " + jogo : "");
                color = ContextCompat.getColor(context, R.color.green_ok);
                break;
            case CONECTANDO:
                statusText = context.getString(R.string.status_conectando);
                color = ContextCompat.getColor(context, R.color.text_muted);
                break;
            case ERRO_AUTENTICACAO:
                statusText = context.getString(R.string.status_erro_auth);
                color = ContextCompat.getColor(context, R.color.red_danger);
                break;
            case IP_INACESSIVEL:
                statusText = context.getString(R.string.status_ip_inacessivel);
                color = ContextCompat.getColor(context, R.color.yellow_alert);
                break;
            case DESCONECTADO:
            default:
                statusText = context.getString(R.string.status_desconectado);
                color = ContextCompat.getColor(context, R.color.text_muted);
                break;
        }

        views.setTextViewText(R.id.widget_status, statusText);
        views.setTextColor(R.id.widget_status, color);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
}
