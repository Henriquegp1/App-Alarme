package com.henrique.gamesentinel;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

public class QuickTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        AlarmForegroundService.Status status = AlarmForegroundService.ultimoStatus;

        switch (status) {
            case CONECTADO:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Sentinel: ON");
                break;
            case CONECTANDO:
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Conectando...");
                break;
            case DESCONECTADO:
            default:
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel("Sentinel: OFF");
                break;
        }
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        AlarmForegroundService.Status status = AlarmForegroundService.ultimoStatus;

        if (status == AlarmForegroundService.Status.DESCONECTADO) {
            startSentinel();
        } else {
            stopSentinel();
        }
    }

    private void startSentinel() {
        SharedPreferences prefs = getSharedPreferences("ow_alarm_prefs", MODE_PRIVATE);
        String ip = prefs.getString("endereco", "");
        String token = prefs.getString("credencial", "");

        if (TextUtils.isEmpty(ip)) {
            Toast.makeText(this, "Configure o IP no app primeiro", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = montarUrl(ip, token);
        Intent intent = new Intent(this, AlarmForegroundService.class);
        intent.putExtra(AlarmForegroundService.EXTRA_WS_URL, url);
        intent.setAction(AlarmForegroundService.ACAO_CONECTAR);
        ContextCompat.startForegroundService(this, intent);
        
        updateTile();
    }

    private void stopSentinel() {
        Intent intent = new Intent(this, AlarmForegroundService.class);
        intent.setAction(AlarmForegroundService.ACAO_DESCONECTAR);
        startService(intent);
        updateTile();
    }

    private String montarUrl(String enderecoEntrada, String credencial) {
        String rawAddress = enderecoEntrada.replace("ws://", "");
        int idxWs = rawAddress.indexOf("/ws");
        String cleanAddress = idxWs >= 0 ? rawAddress.substring(0, idxWs) : rawAddress;
        String finalAddress = cleanAddress.contains(":") ? cleanAddress : cleanAddress + ":8000";
        String baseUrl = "ws://" + finalAddress + "/ws";
        if (!TextUtils.isEmpty(credencial)) {
            return baseUrl + "?token=" + android.net.Uri.encode(credencial);
        }
        return baseUrl;
    }
}
