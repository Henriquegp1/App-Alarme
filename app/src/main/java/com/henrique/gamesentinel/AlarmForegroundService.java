package com.henrique.gamesentinel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
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

    public interface OnStatusChangeListener {
        void onStatusChanged(Status status);
    }

    private static OnStatusChangeListener statusListener;

    public static void setOnStatusChangeListener(OnStatusChangeListener listener) {
        statusListener = listener;
        if (listener != null) {
            listener.onStatusChanged(ultimoStatus);
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
    private boolean deveReconectar = false;

    @Override
    public void onCreate() {
        super.onCreate();
        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .build();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        criarCanalNotificacao();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String acao = intent.getAction();

        if (ACAO_DESCONECTAR.equals(acao)) {
            pararTudo();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACAO_CONECTAR.equals(acao)) {
            urlAtual = intent.getStringExtra(EXTRA_WS_URL);
            deveReconectar = true;
            enviarStatus(Status.CONECTANDO);
            iniciarComoForeground();
            adquirirWakeLock();
            conectar(urlAtual);
        }

        return START_STICKY;
    }

    private void iniciarComoForeground() {
        atualizarNotificacao("Aguardando partida...");
    }

    private void adquirirWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GameSentinel::ConexaoAtiva");
        wakeLock.acquire(TimeUnit.HOURS.toMillis(6));
    }

    private void conectar(String url) {
        // Se já existir uma conexão, fecha ela antes de abrir uma nova para evitar bugs
        if (webSocket != null) {
            webSocket.close(1000, "Reiniciando conexão");
            webSocket = null;
        }

        Request request = new Request.Builder().url(url).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.i(TAG, "Conectado: " + url);
                LogManager.addLog(AlarmForegroundService.this, "Conectado ao PC");
                enviarStatus(Status.CONECTADO);
                atualizarNotificacao("Conectado — aguardando partida...");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.i(TAG, "Mensagem recebida: " + text);
                try {
                    JSONObject json = new JSONObject(text);
                    String status = json.optString("status");
                    
                    if ("PARTIDA_ENCONTRADA".equals(status)) {
                        LogManager.addLog(AlarmForegroundService.this, "⚠️ PARTIDA ENCONTRADA!");
                        dispararAlarme();
                    } else if ("TESTE_ALARME".equals(status) || "TESTE".equals(status)) {
                        LogManager.addLog(AlarmForegroundService.this, "🧪 Teste recebido do PC");
                        dispararAlarme();
                    } else if ("ping".equals(json.optString("tipo"))) {
                        // Responde ao ping de verificação manual do PC.
                        // Sem isso, o botão "verificar conexão agora" do
                        // PC nunca tem como confirmar que essa conexão
                        // ainda está viva -- precisa de uma resposta de
                        // volta, não só conseguir mandar o ping.
                        JSONObject pong = new JSONObject();
                        pong.put("tipo", "pong");
                        ws.send(pong.toString());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Mensagem em formato inesperado: " + text, e);
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "Conexão caiu: " + t.getMessage());
                
                Status novoStatus = Status.CONECTANDO;
                String mensagemNotif = "Conexão perdida — reconectando...";

                if (response != null) {
                    int code = response.code();
                    if (code == 401 || code == 403) {
                        novoStatus = Status.ERRO_AUTENTICACAO;
                        mensagemNotif = "Erro de autenticação — verifique a senha.";
                        deveReconectar = false; // Para de tentar se a senha está errada
                    } else if (code == 429) {
                        novoStatus = Status.BLOQUEIO_TEMPORARIO;
                        mensagemNotif = "Muitas tentativas — bloqueio temporário.";
                        deveReconectar = false;
                    }
                } else {
                    // Sem resposta (response == null) geralmente indica erro de rede/IP
                    novoStatus = Status.IP_INACESSIVEL;
                    mensagemNotif = "IP inacessível — verifique a rede e o PC.";
                }

                enviarStatus(novoStatus);
                atualizarNotificacao(mensagemNotif);
                LogManager.addLog(AlarmForegroundService.this, "Erro: " + mensagemNotif);
                
                if (deveReconectar) {
                    agendarReconexao();
                }
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                if (deveReconectar) {
                    enviarStatus(Status.CONECTANDO);
                }
                agendarReconexao();
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
        atualizarNotificacao("PARTIDA ENCONTRADA!");
        
        SharedPreferences prefs = getSharedPreferences("ow_alarm_prefs", MODE_PRIVATE);
        int volumeProgress = prefs.getInt(getString(R.string.pref_volume_key), 80);
        float volume = volumeProgress / 100f;
        int vibrType = prefs.getInt(getString(R.string.pref_vibration_key), 1);

        tocarSom(volume);
        vibrar(vibrType);
        confirmarRecebimentoAoPc();
    }

    private void confirmarRecebimentoAoPc() {
        if (webSocket != null) {
            try {
                JSONObject confirmacao = new JSONObject();
                confirmacao.put("status", "ALARME_RECEBIDO_CELULAR");
                confirmacao.put("timestamp", System.currentTimeMillis());
                webSocket.send(confirmacao.toString());
                Log.i(TAG, "Confirmação de alarme enviada ao PC");
                LogManager.addLog(this, "Confirmação enviada ao PC");
            } catch (Exception e) {
                Log.e(TAG, "Erro ao enviar confirmação ao PC", e);
            }
        }
    }

    private void tocarSom(float volume) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }

            SharedPreferences prefs = getSharedPreferences("ow_alarm_prefs", MODE_PRIVATE);
            String uriString = prefs.getString(getString(R.string.custom_sound_uri_key), null);

            if (uriString != null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                mediaPlayer.setDataSource(this, Uri.parse(uriString));
                mediaPlayer.prepare();
            } else {
                mediaPlayer = MediaPlayer.create(this, R.raw.alarme);
                if (mediaPlayer == null) {
                    Log.e(TAG, "res/raw/alarme.mp3 não encontrado — adicione o arquivo de áudio no projeto.");
                    return;
                }
            }

            mediaPlayer.setVolume(volume, volume);
            mediaPlayer.setLooping(false);
            mediaPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao tocar alarme", e);
            try {
                if (mediaPlayer != null) mediaPlayer.release();
                mediaPlayer = MediaPlayer.create(this, R.raw.alarme);
                if (mediaPlayer != null) {
                    mediaPlayer.setVolume(volume, volume);
                    mediaPlayer.start();
                }
            } catch (Exception ex) {
                Log.e(TAG, "Erro no fallback do alarme", ex);
            }
        }
    }

    private void vibrar(int type) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        
        long[] pattern;
        if (type == 0) pattern = new long[]{0, 200}; // Curto
        else if (type == 2) pattern = new long[]{0, 100, 100, 100, 400, 100, 100, 100}; // Heartbeat
        else pattern = new long[]{0, 1000}; // Longo

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }

    private void pararTudo() {
        deveReconectar = false;
        if (ultimoStatus != Status.DESCONECTADO) {
            LogManager.addLog(this, "Desconectado");
        }
        enviarStatus(Status.DESCONECTADO);
        if (webSocket != null) {
            webSocket.close(1000, "Usuário desconectou");
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        stopForeground(true);
    }

    private void enviarStatus(Status status) {
        ultimoStatus = status;

        if (statusListener != null) {
            new Handler(getMainLooper()).post(() -> {
                if (statusListener != null) statusListener.onStatusChanged(status);
            });
        }

        Intent intent = new Intent(ACAO_STATUS_ATUALIZADO);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, status.name());
        sendBroadcast(intent);
    }

    private void atualizarNotificacao(String texto) {
        // Intent para abrir o app ao clicar na notificação
        Intent intentApp = new Intent(this, MainActivity.class);
        intentApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingApp = PendingIntent.getActivity(this, 0, intentApp, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent para o botão de desconectar
        Intent intentDesconectar = new Intent(this, AlarmForegroundService.class);
        intentDesconectar.setAction(ACAO_DESCONECTAR);
        PendingIntent pendingDesconectar = PendingIntent.getService(this, 1, intentDesconectar, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CANAL_ID)
                .setContentTitle("Game Sentinel")
                .setContentText(texto)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .setContentIntent(pendingApp) // Abre o app ao clicar
                .setPriority(NotificationCompat.PRIORITY_LOW) // Padrão é baixa para não incomodar
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", pendingDesconectar);

        // Se for um alerta de partida encontrada, aumenta a prioridade
        if (texto.contains("PARTIDA ENCONTRADA")) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                   .setDefaults(Notification.DEFAULT_ALL)
                   .setVibrate(new long[]{0, 500, 200, 500});
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notification = builder.build();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification);
        } else {
            nm.notify(NOTIF_ID, notification);
        }
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL_ID, "Overwatch Alarm", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(canal);
        }
    }

    @Override
    public void onDestroy() {
        pararTudo();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}