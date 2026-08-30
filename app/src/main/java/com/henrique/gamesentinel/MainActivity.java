package com.henrique.gamesentinel;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Objects;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "ow_alarm_prefs";
    private static final String KEY_ENDERECO = "endereco";
    private static final String KEY_CREDENCIAL = "credencial";
    private static final int REQ_NOTIFICACAO = 1001;

    private EditText editIp;
    private EditText editSenha;
    private TextView labelStatus;
    private TextView txtLogs;
    private TextView txtBatteryWarning;
    private TextView txtActiveGame;
    private TextView txtPcStatus;
    private View containerActiveGame;
    private View containerRemoteControl;
    private View containerLogs;
    private SharedPreferences prefs;

    private final AlarmForegroundService.OnStatusChangeListener listenerStatus = new AlarmForegroundService.OnStatusChangeListener() {
        @Override
        public void onStatusChanged(AlarmForegroundService.Status status) {
            atualizarStatusUI(status);
        }

        @Override
        public void onGameChanged(String gameName) {
            atualizarJogoUI(gameName);
        }

        @Override
        public void onPcMonitorStatusChanged(String pcStatus) {
            atualizarPcMonitorUI(pcStatus);
        }
    };

    private final LogManager.OnLogChangeListener listenerLog = this::refreshLogs;

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null) {
                    preencherComQr(result.getContents());
                    Toast.makeText(this, R.string.toast_qr_read, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editIp = findViewById(R.id.edit_ip);
        editSenha = findViewById(R.id.edit_senha);
        labelStatus = findViewById(R.id.label_status);
        txtLogs = findViewById(R.id.txt_logs);
        txtBatteryWarning = findViewById(R.id.txt_battery_warning);
        txtActiveGame = findViewById(R.id.txt_active_game);
        txtPcStatus = findViewById(R.id.txt_pc_status);
        containerActiveGame = findViewById(R.id.container_active_game);
        containerRemoteControl = findViewById(R.id.container_remote_control);
        containerLogs = findViewById(R.id.container_logs);
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        Button btnScan = findViewById(R.id.btn_scan_qr);
        Button btnConectar = findViewById(R.id.btn_conectar);
        Button btnDesconectar = findViewById(R.id.btn_desconectar);
        Button btnRemoteStart = findViewById(R.id.btn_remote_start);
        Button btnRemoteStop = findViewById(R.id.btn_remote_stop);
        TextView btnClearLogs = findViewById(R.id.btn_clear_logs);
        TextView btnToggleLogs = findViewById(R.id.btn_toggle_logs);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        
        // Carregar estado de visibilidade dos logs
        boolean logsVisible = prefs.getBoolean("logs_visible", true);
        containerLogs.setVisibility(logsVisible ? View.VISIBLE : View.GONE);
        btnToggleLogs.setText(logsVisible ? R.string.hide_logs : R.string.show_logs);

        String enderecoSalvo = prefs.getString(KEY_ENDERECO, "");
        if (!TextUtils.isEmpty(enderecoSalvo)) {
            editIp.setText(enderecoSalvo);
        }
        String credencialSalva = prefs.getString(KEY_CREDENCIAL, "");
        if (!TextUtils.isEmpty(credencialSalva)) {
            editSenha.setText(credencialSalva);
        }

        pedirPermissaoNotificacaoSeNecessario();

        refreshLogs();

        TextWatcher disconnectWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Se o usuário mudar o IP ou a Senha enquanto está conectado,
                // desconecta na hora para evitar bugs de credenciais inválidas.
                if (AlarmForegroundService.ultimoStatus != AlarmForegroundService.Status.DESCONECTADO) {
                    Intent intent = new Intent(MainActivity.this, AlarmForegroundService.class);
                    intent.setAction(AlarmForegroundService.ACAO_DESCONECTAR);
                    startService(intent);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        editIp.addTextChangedListener(disconnectWatcher);
        editSenha.addTextChangedListener(disconnectWatcher);

        atualizarStatusUI(AlarmForegroundService.ultimoStatus);
        atualizarJogoUI(AlarmForegroundService.jogoAtivo);
        atualizarPcMonitorUI(AlarmForegroundService.statusMonitorPc);

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        btnScan.setOnClickListener(v -> iniciarLeituraQr());

        btnConectar.setOnClickListener(v -> {
            String credencial = editSenha.getText().toString().trim();
            if (TextUtils.isEmpty(credencial)) {
                Toast.makeText(this, R.string.toast_input_token, Toast.LENGTH_SHORT).show();
                return;
            }

            String url = montarUrlWebSocket(editIp.getText().toString().trim(), credencial);
            if (url == null) {
                Toast.makeText(this, R.string.toast_input_ip, Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit()
                    .putString(KEY_ENDERECO, editIp.getText().toString().trim())
                    .putString(KEY_CREDENCIAL, credencial)
                    .apply();

            Intent intent = new Intent(this, AlarmForegroundService.class);
            intent.putExtra(AlarmForegroundService.EXTRA_WS_URL, url);
            intent.setAction(AlarmForegroundService.ACAO_CONECTAR);
            ContextCompat.startForegroundService(this, intent);

            labelStatus.setText(R.string.status_conectando);
            labelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        });

        btnDesconectar.setOnClickListener(v -> {
            Intent intent = new Intent(this, AlarmForegroundService.class);
            intent.setAction(AlarmForegroundService.ACAO_DESCONECTAR);
            startService(intent);
        });

        btnClearLogs.setOnClickListener(v -> {
            LogManager.clearLogs(this);
            refreshLogs();
        });

        txtBatteryWarning.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        btnRemoteStart.setOnClickListener(v -> AlarmForegroundService.enviarComandoRemoto("iniciar"));
        btnRemoteStop.setOnClickListener(v -> AlarmForegroundService.enviarComandoRemoto("parar"));

        btnToggleLogs.setOnClickListener(v -> {
            boolean isVisible = containerLogs.getVisibility() == View.VISIBLE;
            containerLogs.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            btnToggleLogs.setText(isVisible ? R.string.show_logs : R.string.hide_logs);
            prefs.edit().putBoolean("logs_visible", !isVisible).apply();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBatteryOptimization();
    }

    private void checkBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            txtBatteryWarning.setVisibility(View.GONE);
        } else {
            txtBatteryWarning.setVisibility(View.VISIBLE);
        }
    }

    private void refreshLogs() {
        List<String> logs = LogManager.getLogs(this);
        if (logs.isEmpty()) {
            txtLogs.setText(R.string.no_history);
        } else {
            StringBuilder sb = new StringBuilder();
            for (String log : logs) {
                sb.append(log).append("\n");
            }
            txtLogs.setText(sb.toString().trim());
        }
    }

    private void atualizarJogoUI(String nome) {
        if (nome == null || nome.isEmpty()) {
            containerActiveGame.setVisibility(View.GONE);
        } else {
            containerActiveGame.setVisibility(View.VISIBLE);
            txtActiveGame.setText(nome);
            
            // Mudar cor conforme o jogo
            int activeColorRes = R.color.accent_generic;
            String lower = nome.toLowerCase();
            if (lower.contains("overwatch")) activeColorRes = R.color.accent_overwatch;
            else if (lower.contains("valorant")) activeColorRes = R.color.accent_valorant;
            else if (lower.contains("dead") || lower.contains("dbd")) activeColorRes = R.color.accent_dbd;
            
            txtActiveGame.setTextColor(ContextCompat.getColor(this, activeColorRes));
        }
    }

    private void atualizarPcMonitorUI(String pcStatus) {
        if (AlarmForegroundService.ultimoStatus != AlarmForegroundService.Status.CONECTADO || pcStatus == null) {
            containerRemoteControl.setVisibility(View.GONE);
        } else {
            containerRemoteControl.setVisibility(View.VISIBLE);
            int color = ContextCompat.getColor(this, R.color.text_muted);
            String statusName;
            
            switch (pcStatus) {
                case "MONITORANDO":
                    statusName = getString(R.string.pc_status_monitoring);
                    color = ContextCompat.getColor(this, R.color.green_ok);
                    findViewById(R.id.btn_remote_start).setEnabled(false);
                    findViewById(R.id.btn_remote_stop).setEnabled(true);
                    break;
                case "COOLDOWN":
                    statusName = getString(R.string.pc_status_cooldown);
                    color = ContextCompat.getColor(this, R.color.yellow_alert);
                    findViewById(R.id.btn_remote_start).setEnabled(false);
                    findViewById(R.id.btn_remote_stop).setEnabled(true);
                    break;
                case "PRONTO":
                default:
                    statusName = getString(R.string.pc_status_idle);
                    findViewById(R.id.btn_remote_start).setEnabled(true);
                    findViewById(R.id.btn_remote_stop).setEnabled(false);
                    break;
            }
            txtPcStatus.setText(getString(R.string.pc_status_prefix, statusName));
            txtPcStatus.setTextColor(color);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        AlarmForegroundService.setOnStatusChangeListener(listenerStatus);
        LogManager.setOnLogChangeListener(listenerLog);
        refreshLogs();
    }

    @Override
    protected void onStop() {
        super.onStop();
        AlarmForegroundService.setOnStatusChangeListener(null);
        LogManager.setOnLogChangeListener(null);
    }

    private void atualizarStatusUI(AlarmForegroundService.Status status) {
        switch (status) {
            case CONECTADO:
                labelStatus.setText(R.string.status_conectado);
                labelStatus.setTextColor(ContextCompat.getColor(this, R.color.green_ok));
                break;
            case CONECTANDO:
                labelStatus.setText(R.string.status_conectando);
                labelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                break;
            case ERRO_AUTENTICACAO:
                labelStatus.setText(R.string.status_erro_auth);
                labelStatus.setTextColor(ContextCompat.getColor(this, R.color.red_danger));
                break;
            case IP_INACESSIVEL:
                labelStatus.setText(R.string.status_ip_inacessivel);
                labelStatus.setTextColor(ContextCompat.getColor(this, R.color.yellow_alert));
                break;
            case BLOQUEIO_TEMPORARIO:
                labelStatus.setText(R.string.status_bloqueio_temp);
                labelStatus.setTextColor(ContextCompat.getColor(this, R.color.red_danger));
                break;
            case DESCONECTADO:
            default:
                labelStatus.setText(R.string.status_desconectado);
                labelStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
                break;
        }
    }

    /**
     * A partir do Android 13 (API 33), mostrar notificação exige permissão
     * pedida em tempo de execução — sem isso, o Foreground Service ainda
     * roda, mas a notificação (obrigatória para o Android não matar o
     * serviço) pode não aparecer e o sistema reclama.
     */
    private void pedirPermissaoNotificacaoSeNecessario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICACAO
                );
            }
        }
    }

    private void iniciarLeituraQr() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.qr_prompt));
        options.setBeepEnabled(false);
        options.setOrientationLocked(true);
        qrLauncher.launch(options);
    }

    /**
     * O PC gera um código de resposta rápida (QR Code) com "ws://IP:PORTA/ws?token=XXXX".
     * Preenche IP:PORTA no campo de endereço e a credencial (token) no campo de
     * senha automaticamente, sem o usuário precisar digitar nada.
     */
    private void preencherComQr(String conteudoQr) {
        String semProtocolo = conteudoQr.replace("ws://", "");
        int idxWs = semProtocolo.indexOf("/ws");
        String hostPorta = idxWs >= 0 ? semProtocolo.substring(0, idxWs) : semProtocolo;
        String resto = idxWs >= 0 ? semProtocolo.substring(idxWs) : "";

        editIp.setText(hostPorta);

        String token = extrairParametro(resto);
        if (!TextUtils.isEmpty(token)) {
            editSenha.setText(token);
        }
    }

    private String extrairParametro(String urlOuQuery) {
        String chave = "token=";
        int idx = urlOuQuery.indexOf(chave);
        if (idx < 0) return null;
        String valorParcial = urlOuQuery.substring(idx + chave.length());
        int fimIdx = valorParcial.indexOf('&');
        if (fimIdx >= 0) {
            return valorParcial.substring(0, fimIdx);
        }
        return valorParcial;
    }

    private String montarUrlWebSocket(String enderecoEntrada, String credencial) {
        if (TextUtils.isEmpty(enderecoEntrada)) return null;
        String rawAddress = enderecoEntrada.replace("ws://", "");
        int idxWs = rawAddress.indexOf("/ws");
        String cleanAddress = idxWs >= 0 ? rawAddress.substring(0, idxWs) : rawAddress;
        
        String finalAddress = cleanAddress.contains(":") ? cleanAddress : cleanAddress + ":8000";
        
        String baseUrl = "ws://" + finalAddress + "/ws";
        if (!TextUtils.isEmpty(credencial)) {
            return baseUrl + "?token=" + Uri.encode(credencial);
        }
        return baseUrl;
    }
}
