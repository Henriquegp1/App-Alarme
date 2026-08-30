package com.henrique.gamesentinel;

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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final String PREFS = "ow_alarm_prefs";
    private TextView txtCurrentSound, txtBatteryStatus, txtVersion, labelCurrentSoundFor;
    private EditText editProfileName, editProfileIp, editProfileSenha;
    private Spinner spinnerProfiles, spinnerGameSound;
    private Button btnBattery, btnCheckUpdates;
    private SharedPreferences prefs;
    private MediaPlayer testMediaPlayer;
    private Vibrator testVibrator;
    private List<ProfileManager.Profile> profiles = new ArrayList<>();
    private ArrayAdapter<ProfileManager.Profile> profileAdapter;
    private final OkHttpClient httpClient = new OkHttpClient();

    private final String[] GAME_NAMES = {
        "Geral", 
        "Overwatch", 
        "Valorant", 
        "Dead by Daylight"
    };

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
        txtVersion = findViewById(R.id.txt_current_version);
        labelCurrentSoundFor = findViewById(R.id.label_current_sound_for);
        ImageButton btnBack = findViewById(R.id.btn_back);
        Button btnSelectSound = findViewById(R.id.btn_select_sound);
        Button btnTestSound = findViewById(R.id.btn_test_sound);
        btnCheckUpdates = findViewById(R.id.btn_check_updates);

        SeekBar seekVolume = findViewById(R.id.seek_volume);
        RadioGroup groupVibr = findViewById(R.id.group_vibration);
        SwitchCompat switchDnd = findViewById(R.id.switch_dnd_bypass);
        SwitchCompat switchFull = findViewById(R.id.switch_full_screen);
        spinnerGameSound = findViewById(R.id.spinner_game_sound);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        testVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        setupGameSoundSpinner();
        updateCurrentSoundDisplay();
        
        // Exibir versão atual
        String displayVersion = "1.0.0";
        try {
            displayVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        txtVersion.setText(getString(R.string.current_version_label, displayVersion));

        editProfileName = findViewById(R.id.edit_profile_name);
        editProfileIp = findViewById(R.id.edit_profile_ip);
        editProfileSenha = findViewById(R.id.edit_profile_senha);
        spinnerProfiles = findViewById(R.id.spinner_profiles);
        Button btnSaveProfile = findViewById(R.id.btn_save_profile);
        
        setupProfiles();

        // Carregar valores salvos
        seekVolume.setProgress(prefs.getInt(getString(R.string.pref_volume_key), 80));
        switchDnd.setChecked(prefs.getBoolean(getString(R.string.pref_dnd_bypass_key), false));
        switchFull.setChecked(prefs.getBoolean(getString(R.string.pref_full_screen_key), true));
        int vibrType = prefs.getInt(getString(R.string.pref_vibration_key), 1);
        switch (vibrType) {
            case 0:
                groupVibr.check(R.id.radio_vibr_short);
                break;
            case 2:
                groupVibr.check(R.id.radio_vibr_heart);
                break;
            case 1:
            default:
                groupVibr.check(R.id.radio_vibr_long);
                break;
        }

        btnBack.setOnClickListener(v -> finish());
        
        btnCheckUpdates.setOnClickListener(v -> checkUpdates());

        seekVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) prefs.edit().putInt(getString(R.string.pref_volume_key), progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        switchDnd.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(getString(R.string.pref_dnd_bypass_key), isChecked).apply());

        switchFull.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(getString(R.string.pref_full_screen_key), isChecked).apply();
        });

        groupVibr.setOnCheckedChangeListener((group, checkedId) -> {
            int selectedType;
            if (checkedId == R.id.radio_vibr_short) {
                selectedType = 0;
            } else if (checkedId == R.id.radio_vibr_heart) {
                selectedType = 2;
            } else {
                selectedType = 1;
            }
            prefs.edit().putInt(getString(R.string.pref_vibration_key), selectedType).apply();
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

        spinnerProfiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ProfileManager.Profile p = profiles.get(position);
                editProfileName.setText(p.name);
                editProfileIp.setText(p.address);
                editProfileSenha.setText(p.token);
                
                prefs.edit()
                    .putString("endereco", p.address)
                    .putString("credencial", p.token)
                    .apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveProfile() {
        String name = editProfileName.getText().toString().trim();
        String address = editProfileIp.getText().toString().trim();
        String token = editProfileSenha.getText().toString().trim();

        if (name.isEmpty() || address.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, R.string.err_fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        ProfileManager.Profile newProfile = new ProfileManager.Profile(name, address, token);
        boolean found = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (java.util.Objects.equals(profiles.get(i).name, name)) {
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

    private void setupGameSoundSpinner() {
        String[] displayNames = {
            getString(R.string.game_generic),
            getString(R.string.game_overwatch),
            getString(R.string.game_valorant),
            getString(R.string.game_dbd)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, displayNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGameSound.setAdapter(adapter);

        spinnerGameSound.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCurrentSoundDisplay();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateCurrentSoundDisplay() {
        String selectedGame = GAME_NAMES[spinnerGameSound.getSelectedItemPosition()];
        String displayName = (String) spinnerGameSound.getSelectedItem();
        labelCurrentSoundFor.setText(getString(R.string.current_sound_label, displayName));
        
        String key = "custom_sound_name_" + selectedGame;
        String soundName = prefs.getString(key, getString(R.string.default_sound_name));
        txtCurrentSound.setText(soundName);
    }

    private void saveSoundUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String selectedGame = GAME_NAMES[spinnerGameSound.getSelectedItemPosition()];
            String soundName = getFileName(uri);
            
            prefs.edit()
                    .putString("custom_sound_uri_" + selectedGame, uri.toString())
                    .putString("custom_sound_name_" + selectedGame, soundName)
                    .apply();

            updateCurrentSoundDisplay();
            Toast.makeText(this, getString(R.string.toast_sound_updated, selectedGame), Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.err_permission, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Permission error", e);
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
                Log.e(TAG, "Error getting file name", e);
            }
        }
        if (fileName == null && uri != null && uri.getPath() != null) {
            fileName = uri.getPath();
            int cut = fileName.lastIndexOf('/');
            if (cut != -1) {
                fileName = fileName.substring(cut + 1);
            }
        }
        return fileName != null ? fileName : getString(R.string.sound_selected_label);
    }

    private void testAlarmSound() {
        if (testMediaPlayer != null) {
            testMediaPlayer.release();
            testMediaPlayer = null;
        }
        if (testVibrator != null) {
            testVibrator.cancel();
        }

        String selectedGame = GAME_NAMES[spinnerGameSound.getSelectedItemPosition()];
        String uriString = prefs.getString("custom_sound_uri_" + selectedGame, null);
        
        if (uriString == null && !selectedGame.equals("Geral")) {
            uriString = prefs.getString("custom_sound_uri_Geral", null);
        }

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
                    Toast.makeText(this, R.string.err_default_sound, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            testMediaPlayer.setVolume(volume, volume);
            testMediaPlayer.start();
            LogManager.addLog(this, getString(R.string.log_test_manual));
            
            // Testar Vibração
            int currentVibrType = prefs.getInt(getString(R.string.pref_vibration_key), 1);
            long[] pattern;
            switch (currentVibrType) {
                case 0:
                    pattern = new long[]{0, 200};
                    break;
                case 2:
                    pattern = new long[]{0, 100, 100, 100, 400, 100, 100, 100};
                    break;
                default:
                    pattern = new long[]{0, 1000};
                    break;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                testVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                //noinspection deprecation
                testVibrator.vibrate(pattern, -1);
            }

            Toast.makeText(this, R.string.toast_testing_alert, Toast.LENGTH_SHORT).show();

        } catch (IOException | SecurityException e) {
            Toast.makeText(this, getString(R.string.err_play_sound, e.getMessage()), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error playing sound", e);
        }
    }

    private void checkUpdates() {
        btnCheckUpdates.setEnabled(false);
        btnCheckUpdates.setText(R.string.checking_updates);

        String url = "https://api.github.com/repos/Henriquegp1/App-Alarme/releases/latest";
        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull IOException e) {
                runOnUiThread(() -> {
                    btnCheckUpdates.setEnabled(true);
                    btnCheckUpdates.setText(R.string.btn_check_updates);
                    Toast.makeText(SettingsActivity.this, R.string.update_error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull Call call, @androidx.annotation.NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        JSONObject release = new JSONObject(json);
                        String latestTag = release.getString("tag_name").replace("v", "");
                        String htmlUrl = release.getString("html_url");
                        
                        String currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                        
                        runOnUiThread(() -> {
                            btnCheckUpdates.setEnabled(true);
                            btnCheckUpdates.setText(R.string.btn_check_updates);
                            
                            if (isNewer(latestTag, currentVersion)) {
                                showUpdateDialog(latestTag, htmlUrl);
                            } else {
                                Toast.makeText(SettingsActivity.this, R.string.no_updates, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        onFailure(call, new IOException(e));
                    }
                } else {
                    onFailure(call, new IOException("Error " + response.code()));
                }
            }
        });
    }

    private boolean isNewer(String latest, String current) {
        try {
            String[] lParts = latest.split("\\.");
            String[] cParts = current.split("\\.");
            for (int i = 0; i < Math.min(lParts.length, cParts.length); i++) {
                int l = Integer.parseInt(lParts[i]);
                int c = Integer.parseInt(cParts[i]);
                if (l > c) return true;
                if (l < c) return false;
            }
            return lParts.length > cParts.length;
        } catch (Exception e) {
            return !latest.equals(current);
        }
    }

    private void showUpdateDialog(String version, String url) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.update_available_title)
                .setMessage(getString(R.string.update_available_msg, version))
                .setPositiveButton(R.string.btn_download, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.dialog_now_no, null)
                .show();
    }

    private void abrirConfigOtimizacaoBateria() {
        Intent intent = new Intent();
        String packageName = getPackageName();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        
        if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        } else {
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
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
