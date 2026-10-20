package com.aboezzat.share;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etIp = findViewById(R.id.et_ip);
        Button btnSave = findViewById(R.id.btn_save);

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        etIp.setText(prefs.getString("tv_ip", ""));

        btnSave.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (!ip.isEmpty()) {
                prefs.edit().putString("tv_ip", ip).apply();
                Toast.makeText(this, "تم حفظ عنوان الشاشة بنجاح ✓", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "يرجى كتابة عنوان الـ IP أولاً", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
