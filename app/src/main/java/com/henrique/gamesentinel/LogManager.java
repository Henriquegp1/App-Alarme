package com.henrique.gamesentinel;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LogManager {
    public interface OnLogChangeListener {
        void onLogAdded();
    }

    private static OnLogChangeListener listener;
    private static final String PREF_LOGS = "app_logs";
    private static final String KEY_LOG_LIST = "log_list";
    private static final int MAX_LOGS = 20;

    public static void setOnLogChangeListener(OnLogChangeListener l) {
        listener = l;
    }

    public static void addLog(Context context, String message) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_LOGS, Context.MODE_PRIVATE);
        List<String> logs = getLogs(context);
        
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        logs.add(0, "[" + timestamp + "] " + message);
        
        if (logs.size() > MAX_LOGS) {
            logs = logs.subList(0, MAX_LOGS);
        }
        
        JSONArray array = new JSONArray(logs);
        prefs.edit().putString(KEY_LOG_LIST, array.toString()).apply();

        if (listener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (listener != null) listener.onLogAdded();
            });
        }
    }

    public static List<String> getLogs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_LOGS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_LOG_LIST, "[]");
        List<String> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }
    
    public static void clearLogs(Context context) {
        context.getSharedPreferences(PREF_LOGS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
