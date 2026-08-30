package com.henrique.gamesentinel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.service.quicksettings.TileService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class AlarmForegroundService extends Service {

    public static final String EXTRA_WS_URL = "extra_ws_url";
    public static final String ACAO_CONECTAR = "com.henrique.gamesentinel.CONECTAR";
    public static final String ACAO_DESCONECTAR = "com.henrique.gamesentinel.DESCONECTAR";
    public static final String ACAO_STATUS_ATUALIZADO = "com.henrique.gamesentinel.STATUS_ATUALIZADO";
    public static final String EXTRA_STATUS = "extra_status";

    public enum Status {
        DESCONECTADO, CONECTANDO, CONECTADO, ERRO_AUTENTICACAO, IP_INACESSIVEL, BLOQUEIO_TEMPORARIO
    }

    public static volatile Status ultimoStatus = Status.DESCONECTADO;
    public static volatile String jogoAtivo = null;
    public static volatile String statusMonitorPc = null;

    public interface OnStatusChangeListener {
        void onStatusChanged(Status status);
        void onGameChanged(String gameName);
        void onPcMonitorStatusChanged(String pcStatus);
    }

    private static OnStatusChangeListener statusListener;

    public static void setOnStatusChangeListener(OnStatusChangeListener listener) {
        statusListener = listener;
        if (listener != null) {
            listener.onStatusChanged(ultimoStatus);
            listener.onGameChanged(jogoAtivo);
            listener.onPcMonitorStatusChanged(statusMonitorPc);
        }
    }

    private static final String TAG = "AlarmService";
    private static final String CANAL_ID = "ow_alarm_canal";
    private static final int NOTIF_ID = 1;

    private static final long INTERVALO_RECONEXAO_MS = 5000;

    private OkHttpClient client;
    private WebSocket webSocket;
    private PowerManager.WakeLock wakeLock;
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;

    private String urlAtual;
    private String deviceId;
    private boolean deveReconectar = false;

    private static AlarmForegroundService instance;

    public static void enviarComandoRemoto(String comando) {
        switch (ultimoStatus) {
            case CONECTANDO:
            case CONECTADO:
                if (instance != null && instance.webSocket != null) {
                    try {
                        JSONObject json = new JSONObject();
                        json.put("tipo", "comando_remoto");
                        json.put("comando", comando);
                        instance.webSocket.send(json.toString());
                        LogManager.addLog(instance, instance.getString(R.string.log_remote_cmd, comando));
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending remote command", e);
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        SharedPreferences prefs = getSharedPreferences("ow_alarm_prefs", MODE_PRIVATE);
        deviceId = prefs.getString("device_id", null);
        if (deviceId == null) {
            deviceId = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("device_id", deviceId).apply();
        }
        
        criarCanalNotificacao();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String acao = intent.getAction();

        if (acao != null) {
            switch (acao) {
                case ACAO_DESCONECTAR:
                    pararTudo();
                    stopSelf();
                    return START_NOT_STICKY;
                case ACAO_CONECTAR:
                    urlAtual = intent.getStringExtra(EXTRA_WS_URL);
                    deveReconectar = true;
                    enviarStatus(Status.CONECTANDO);
                    iniciarComoForeground();
                    adquirirWakeLock();
                    conectar(urlAtual);
                    break;
            }
        }

        return START_STICKY;
    }

    private void iniciarComoForeground() {
        atualizarNotificacao(getString(R.string.status_waiting));
    }

    private void adquirirWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameSentinel::ConexaoAtiva");
        wakeLock.acquire(TimeUnit.HOURS.toMillis(6));
    }

    private void conectar(String url) {
        if (webSocket != null) {
            webSocket.close(1000, getString(R.string.restarting_connection));
            webSocket = null;
        }

        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@androidx.annotation.NonNull WebSocket ws, @androidx.annotation.NonNull Response response) {
                Log.i(TAG, "Conectado: " + url);
                
                try {
                    JSONObject reg = new JSONObject();
                    reg.put("tipo", "registrar_dispositivo");
                    reg.put("device_id", deviceId);
                    ws.send(reg.toString());
                } catch (Exception e) {
                    Log.e(TAG, "Error sending registration", e);
                }

                LogManager.addLog(AlarmForegroundService.this, getString(R.string.log_connected));
                enviarStatus(Status.CONECTADO);
                atualizarNotificacao(getString(R.string.status_conectado) + " — " + getString(R.string.status_waiting));
            }

            @Override
            public void onMessage(@androidx.annotation.NonNull WebSocket ws, @androidx.annotation.NonNull String text) {
                try {
                    JSONObject json = new JSONObject(text);
                    String status = json.optString("status");
                    String tipo = json.optString("tipo");
                    
                    if ("PARTIDA_ENCONTRADA".equals(status)) {
                        LogManager.addLog(AlarmForegroundService.this, getString(R.string.log_match_found));
                        dispararAlarme();
                    } else if ("perfil_ativo".equals(tipo)) {
                        atualizarJogo(json.optString("nome"));
                    } else if ("status_monitor".equals(tipo)) {
                        boolean ativo = json.optBoolean("ativo");
                        boolean cooldown = json.optBoolean("cooldown");
                        String novoStatusPc = cooldown ? "COOLDOWN" : (ativo ? "MONITORANDO" : "PRONTO");
                        atualizarStatusMonitorPc(novoStatusPc);
                    } else if ("TESTE_ALARME".equals(status) || "TESTE".equals(status)) {
                        LogManager.addLog(AlarmForegroundService.this, getString(R.string.log_test_pc));
                        dispararAlarme();
                    } else if ("ping".equals(tipo)) {
                        JSONObject pong = new JSONObject();
                        pong.put("tipo", "pong");
                        ws.send(pong.toString());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing message", e);
                }
            }

            @Override
            public void onFailure(@androidx.annotation.NonNull WebSocket ws, @androidx.annotation.NonNull Throwable t, Response response) {
                Status erroStatus;
                String msg;

                if (response != null) {
                    int code = response.code();
                    if (code == 401 || code == 403) {
                        erroStatus = Status.ERRO_AUTENTICACAO;
                        msg = getString(R.string.err_auth_failed);
                        deveReconectar = false;
                    } else if (code == 429) {
                        erroStatus = Status.BLOQUEIO_TEMPORARIO;
                        msg = getString(R.string.err_too_many_attempts);
                        deveReconectar = false;
                    } else {
                        erroStatus = Status.CONECTANDO;
                        msg = getString(R.string.err_connection_lost);
                    }
                } else {
                    erroStatus = Status.IP_INACESSIVEL;
                    msg = getString(R.string.err_ip_unreachable);
                }

                enviarStatus(erroStatus);
                atualizarNotificacao(msg);
                LogManager.addLog(AlarmForegroundService.this, msg);
                if (deveReconectar) agendarReconexao();
            }

            @Override
            public void onClosed(@androidx.annotation.NonNull WebSocket ws, int code, @androidx.annotation.NonNull String reason) {
                if (deveReconectar) {
                    enviarStatus(Status.CONECTANDO);
                    agendarReconexao();
                }
            }
        });
    }

    private void agendarReconexao() {
        if (!deveReconectar || urlAtual == null) return;
        new Handler(getMainLooper()).postDelayed(() -> {
            if (deveReconectar) conectar(urlAtual);
        }, INTERVALO_RECONEXAO_MS);
    }

    private void dispararAlarme() {
        SharedPreferences prefs = getSharedPreferences("ow_alarm_prefs", MODE_PRIVATE);
        
        // Se a opção de tela cheia estiver ativa, abre a Activity de Alerta
        boolean fullScreen = prefs.getBoolean(getString(R.string.pref_full_screen_key), true);
        if (fullScreen) {
            Intent alertIntent = new Intent(this, AlarmAlertActivity.class);
            alertIntent.putExtra("game_name", jogoAtivo);
            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
            startActivity(alertIntent);
        }

        atualizarNotificacao(getString(R.string.log_match_found));
        float volume = prefs.getInt(getString(R.string.pref_volume_key), 80) / 100f;
        int vibrType = prefs.getInt(getString(R.string.pref_vibration_key), 1);
        tocarSom(volume);
        vibrar(vibrType);
        confirmarRecebimentoAoPc();
    }

    private void confirmarRecebimentoAoPc() {
        if (webSocket != null) {
            try {
                JSONObject json = new JSONObject();
                json.put("status", "ALARME_RECEBIDO_CELULAR");
                json.put("timestamp", System.currentTimeMillis());
                webSocket.send(json.toString());
                LogManager.addLog(this, getString(R.string.log_confirm_sent));
            } catch (Exception e) {
                Log.e(TAG, "Error confirming", e);
            }
        }
    }

    private void tocarSom(float volume) {
        try {
            if (mediaPlayer != null) mediaPlayer.release();
            SharedPreferences prefs = getSharedPreferences("ow_alarm_prefs", MODE_PRIVATE);
            
            boolean dndBypass = prefs.getBoolean("pref_dnd_bypass", false);
            if (dndBypass) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int calculatedVol = (int) (maxVol * volume);
                if (calculatedVol == 0 && volume > 0) calculatedVol = 1;
                am.setStreamVolume(AudioManager.STREAM_ALARM, calculatedVol, 0);
            }

            String uriString = null;
            if (jogoAtivo != null) {
                uriString = prefs.getString("custom_sound_uri_" + jogoAtivo.trim(), null);
            }
            if (uriString == null) {
                uriString = prefs.getString("custom_sound_uri_Geral", null);
            }

            if (uriString != null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                mediaPlayer.setDataSource(this, Uri.parse(uriString));
                mediaPlayer.prepare();
            } else {
                mediaPlayer = MediaPlayer.create(this, R.raw.alarme);
            }
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(volume, volume);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound", e);
        }
    }

    private void vibrar(int type) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = (type == 0) ? new long[]{0, 200} : (type == 2 ? new long[]{0, 100, 100, 100, 400, 100, 100, 100} : new long[]{0, 1000});
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            //noinspection deprecation
            vibrator.vibrate(pattern, -1);
        }
    }

    private void pararTudo() {
        deveReconectar = false;
        if (ultimoStatus != Status.DESCONECTADO) LogManager.addLog(this, getString(R.string.log_disconnected));
        enviarStatus(Status.DESCONECTADO);
        if (webSocket != null) webSocket.close(1000, "User Logout");
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (mediaPlayer != null) mediaPlayer.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private void enviarStatus(Status status) {
        ultimoStatus = status;
        if (status == Status.DESCONECTADO) {
            jogoAtivo = null;
            statusMonitorPc = null;
        }
        if (statusListener != null) {
            new Handler(getMainLooper()).post(() -> {
                if (statusListener != null) {
                    statusListener.onStatusChanged(status);
                    statusListener.onGameChanged(jogoAtivo);
                    statusListener.onPcMonitorStatusChanged(statusMonitorPc);
                }
            });
        }
        StatusWidget.updateAllWidgets(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(this, new android.content.ComponentName(this, QuickTileService.class));
        }
        Intent intent = new Intent(ACAO_STATUS_ATUALIZADO);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, status.name());
        sendBroadcast(intent);
    }

    private void atualizarJogo(String nome) {
        jogoAtivo = nome;
        LogManager.addLog(this, getString(R.string.log_profile_active, nome));
        if (statusListener != null) {
            new Handler(getMainLooper()).post(() -> {
                if (statusListener != null) statusListener.onGameChanged(nome);
            });
        }
        StatusWidget.updateAllWidgets(this);
    }

    private void atualizarStatusMonitorPc(String pcStatus) {
        statusMonitorPc = pcStatus;
        if (statusListener != null) {
            new Handler(getMainLooper()).post(() -> {
                if (statusListener != null) statusListener.onPcMonitorStatusChanged(pcStatus);
            });
        }
    }

    private void atualizarNotificacao(String texto) {
        Intent intentApp = new Intent(this, MainActivity.class);
        PendingIntent pendingApp = PendingIntent.getActivity(this, 0, intentApp, PendingIntent.FLAG_IMMUTABLE);
        Intent intentDesconectar = new Intent(this, AlarmForegroundService.class);
        intentDesconectar.setAction(ACAO_DESCONECTAR);
        PendingIntent pendingDesconectar = PendingIntent.getService(this, 1, intentDesconectar, PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, CANAL_ID)
                .setContentTitle("Game Sentinel")
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .setContentIntent(pendingApp)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", pendingDesconectar)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif);
        } else {
            nm.notify(NOTIF_ID, notif);
        }
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(CANAL_ID, "Game Sentinel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(canal);
        }
    }

    @Override
    public void onDestroy() {
        pararTudo();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
