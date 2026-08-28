package com.henrique.owalarm;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final String PREFS = "ow_alarm_prefs";
    private TextView txtCurrentSound, txtBatteryStatus;
    private EditText editProfileName, editProfileIp, editProfileSenha;
    private Spinner spinnerProfiles;
    private Button btnBattery;
    private SharedPreferences prefs;
    private MediaPlayer testMediaPlayer;
    private Vibrator testVibrator;
    private List<ProfileManager.Profile> profiles = new ArrayList<>();
    private ArrayAdapter<ProfileManager.Profile> profileAdapter;

    private final ActivityResultLauncher<Intent> soundPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        saveSoundUri(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        txtCurrentSound = findViewById(R.id.txt_current_sound);
        txtBatteryStatus = findViewById(R.id.txt_battery_status);
        ImageButton btnBack = findViewById(R.id.btn_back);
        Button btnSelectSound = findViewById(R.id.btn_select_sound);
        Button btnTestSound = findViewById(R.id.btn_test_sound);
        
        SeekBar seekVolume = findViewById(R.id.seek_volume);
        RadioGroup groupVibr = findViewById(R.id.group_vibration);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        testVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        updateCurrentSoundDisplay();
        
        editProfileName = findViewById(R.id.edit_profile_name);
        editProfileIp = findViewById(R.id.edit_profile_ip);
        editProfileSenha = findViewById(R.id.edit_profile_senha);
        spinnerProfiles = findViewById(R.id.spinner_profiles);
        Button btnSaveProfile = findViewById(R.id.btn_save_profile);
        
        setupProfiles();

        // Carregar valores salvos
        seekVolume.setProgress(prefs.getInt(getString(R.string.pref_volume_key), 80));
        int vibrType = prefs.getInt(getString(R.string.pref_vibration_key), 1);
        if (vibrType == 0) groupVibr.check(R.id.radio_vibr_short);
        else if (vibrType == 2) groupVibr.check(R.id.radio_vibr_heart);
        else groupVibr.check(R.id.radio_vibr_long);

        btnBack.setOnClickListener(v -> finish());
        
        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.edit().putInt(getString(R.string.pref_volume_key), progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        groupVibr.setOnCheckedChangeListener((group, checkedId) -> {
            int type = 1;
            if (checkedId == R.id.radio_vibr_short) type = 0;
            else if (checkedId == R.id.radio_vibr_heart) type = 2;
            prefs.edit().putInt(getString(R.string.pref_vibration_key), type).apply();
        });

        btnBattery = findViewById(R.id.btn_battery_optimization);
        btnBattery.setOnClickListener(v -> abrirConfigOtimizacaoBateria());

        Button btnClear = findViewById(R.id.btn_clear_credentials);
        btnClear.setOnClickListener(v -> {
            ProfileManager.clearAll(this);
            profiles.clear();
            profileAdapter.notifyDataSetChanged();
            Toast.makeText(this, R.string.credentials_cleared, Toast.LENGTH_SHORT).show();
        });

        btnSaveProfile.setOnClickListener(v -> saveProfile());

        btnSelectSound.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/*");
            soundPickerLauncher.launch(intent);
        });

        btnTestSound.setOnClickListener(v -> testAlarmSound());
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarStatusBateria();
    }

    private void atualizarStatusBateria() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            txtBatteryStatus.setText(R.string.battery_status_protected);
            txtBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.green_ok));
            btnBattery.setText(R.string.battery_optimization_btn_off);
        } else {
            txtBatteryStatus.setText(R.string.battery_status_optimized);
            txtBatteryStatus.setTextColor(ContextCompat.getColor(this, R.color.yellow_alert));
            btnBattery.setText(R.string.battery_optimization_btn_on);
        }
    }

    private void setupProfiles() {
        profiles = ProfileManager.getProfiles(this);
        profileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, profiles);
        profileAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProfiles.setAdapter(profileAdapter);

        spinnerProfiles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                ProfileManager.Profile p = profiles.get(position);
                editProfileName.setText(p.name);
                editProfileIp.setText(p.address);
                editProfileSenha.setText(p.token);
                
                // Ao selecionar nas configurações, salvamos como o "ativo" para a Main carregar
                prefs.edit()
                    .putString("endereco", p.address)
                    .putString("credencial", p.token)
                    .apply();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void saveProfile() {
        String name = editProfileName.getText().toString().trim();
        String address = editProfileIp.getText().toString().trim();
        String token = editProfileSenha.getText().toString().trim();

        if (name.isEmpty() || address.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show();
            return;
        }

        ProfileManager.Profile newProfile = new ProfileManager.Profile(name, address, token);
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).name.equals(name)) {
                profiles.set(i, newProfile);
                found = true;
                break;
            }
        }
        if (!found) profiles.add(newProfile);

        ProfileManager.saveProfiles(this, profiles);
        profileAdapter.notifyDataSetChanged();
        Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
    }

    private void updateCurrentSoundDisplay() {
        String soundName = prefs.getString(getString(R.string.custom_sound_name_key), getString(R.string.default_sound_name));
        txtCurrentSound.setText(soundName);
    }

    private void saveSoundUri(Uri uri) {
        try {
            // Take persistable URI permission
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String soundName = getFileName(uri);
            prefs.edit()
                    .putString(getString(R.string.custom_sound_uri_key), uri.toString())
                    .putString(getString(R.string.custom_sound_name_key), soundName)
                    .apply();

            updateCurrentSoundDisplay();
            Toast.makeText(this, "Som atualizado para: " + soundName, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "Erro de permissão ao acessar arquivo", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Erro de permissão", e);
        }
    }

    private String getFileName(Uri uri) {
        String fileName = null;
        if (uri != null && "content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        fileName = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao obter nome do arquivo", e);
            }
        }
        if (fileName == null && uri != null && uri.getPath() != null) {
            fileName = uri.getPath();
            int cut = fileName.lastIndexOf('/');
            if (cut != -1) {
                fileName = fileName.substring(cut + 1);
            }
        }
        return fileName != null ? fileName : "Som Selecionado";
    }

    private void testAlarmSound() {
        if (testMediaPlayer != null) {
            testMediaPlayer.release();
            testMediaPlayer = null;
        }
        if (testVibrator != null) {
            testVibrator.cancel();
        }

        String uriString = prefs.getString(getString(R.string.custom_sound_uri_key), null);
        int volumeProgress = prefs.getInt(getString(R.string.pref_volume_key), 80);
        float volume = volumeProgress / 100f;

        try {
            if (uriString != null) {
                testMediaPlayer = new MediaPlayer();
                testMediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                testMediaPlayer.setDataSource(this, Uri.parse(uriString));
                testMediaPlayer.prepare();
            } else {
                testMediaPlayer = MediaPlayer.create(this, R.raw.alarme);
                if (testMediaPlayer == null) {
                    Toast.makeText(this, "Erro ao carregar som padrão", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            testMediaPlayer.setVolume(volume, volume);
            testMediaPlayer.start();
            LogManager.addLog(this, "Teste de alarme executado");
            
            // Testar Vibração
            int vibrType = prefs.getInt(getString(R.string.pref_vibration_key), 1);
            long[] pattern;
            if (vibrType == 0) pattern = new long[]{0, 200}; // Curto
            else if (vibrType == 2) pattern = new long[]{0, 100, 100, 100, 400, 100, 100, 100}; // Heartbeat
            else pattern = new long[]{0, 1000}; // Longo

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                testVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                testVibrator.vibrate(pattern, -1);
            }

            Toast.makeText(this, "Testando alerta completo...", Toast.LENGTH_SHORT).show();

        } catch (IOException | SecurityException e) {
            Toast.makeText(this, "Erro ao reproduzir som: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Erro ao reproduzir som", e);
        }
    }

    private void abrirConfigOtimizacaoBateria() {
        Intent intent = new Intent();
        String packageName = getPackageName();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        
        if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) {
            // Já está desativado, abre a lista geral para o usuário ver
            intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            Toast.makeText(this, "Otimização já está desativada para este app.", Toast.LENGTH_LONG).show();
        } else {
            // Pede para desativar
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            // Fallback para a tela de configurações se a intent específica falhar
            intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        if (testMediaPlayer != null) {
            testMediaPlayer.release();
        }
        super.onDestroy();
    }
}
