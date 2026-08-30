package com.henrique.gamesentinel;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class AlarmAlertActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Configuração para mostrar sobre a tela de bloqueio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }

        setContentView(R.layout.activity_alarm_alert);

        TextView txtGame = findViewById(R.id.txt_game_name);
        ImageView imgIcon = findViewById(R.id.img_alert_icon);
        Button btnDismiss = findViewById(R.id.btn_dismiss);

        String game = getIntent().getStringExtra("game_name");
        if (game != null) {
            txtGame.setText(game.toUpperCase());
            
            // Mudar cor conforme o jogo
            int colorRes = R.color.accent_generic;
            String lower = game.toLowerCase();
            if (lower.contains("overwatch")) colorRes = R.color.accent_overwatch;
            else if (lower.contains("valorant")) colorRes = R.color.accent_valorant;
            else if (lower.contains("dead") || lower.contains("dbd")) colorRes = R.color.accent_dbd;
            
            txtGame.setTextColor(ContextCompat.getColor(this, colorRes));
            imgIcon.setColorFilter(ContextCompat.getColor(this, colorRes));
        }

        // Animação de pulsar
        Animation pulse = new AlphaAnimation(0.4f, 1.0f);
        pulse.setDuration(500);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        imgIcon.startAnimation(pulse);

        btnDismiss.setOnClickListener(v -> {
            // Ao clicar, o serviço já cuida de parar o som se houver uma lógica lá, 
            // mas aqui apenas fechamos a tela.
            finish();
        });
    }
}
