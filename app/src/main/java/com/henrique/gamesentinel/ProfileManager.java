package com.henrique.gamesentinel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class ProfileManager {
    private static final String FILE_NAME = "secret_ow_profiles";
    private static final String KEY_PROFILES = "profiles_json";
    
    public static class Profile {
        public String name;
        public String address;
        public String token;

        public Profile(String name, String address, String token) {
            this.name = name;
            this.address = address;
            this.token = token;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("address", address);
            json.put("token", token);
            return json;
        }

        public static Profile fromJson(JSONObject json) throws JSONException {
            return new Profile(
                json.getString("name"),
                json.getString("address"),
                json.getString("token")
            );
        }
        
        @androidx.annotation.NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    private static SharedPreferences getEncryptedPrefs(Context context) {
        try {
            // Utilizando a API recomendada para SharedPreferences criptografados
            //noinspection deprecation
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            //noinspection deprecation
            return EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Log.e("ProfileManager", "Erro ao acessar SharedPreferences criptografado", e);
            return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
        }
    }

    public static void saveProfiles(Context context, List<Profile> profiles) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        JSONArray array = new JSONArray();
        for (Profile p : profiles) {
            try {
                array.put(p.toJson());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply();
    }

    public static List<Profile> getProfiles(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        String json = prefs.getString(KEY_PROFILES, "[]");
        List<Profile> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(Profile.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void clearAll(Context context) {
        getEncryptedPrefs(context).edit().clear().apply();
    }
}
