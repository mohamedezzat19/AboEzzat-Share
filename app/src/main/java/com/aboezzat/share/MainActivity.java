package com.aboezzat.share;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 80, 50, 50);

        EditText etIp = new EditText(this);
        etIp.setHint("أدخل عنوان IP الشاشة (مثال: 192.168.1.50)");
        etIp.setText(prefs.getString("tv_ip", ""));
        layout.addView(etIp);

        Button btnSave = new Button(this);
        btnSave.setText("حفظ عنوان الشاشة");
        btnSave.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            prefs.edit().putString("tv_ip", ip).apply();
            Toast.makeText(MainActivity.this, "تم الحفظ بنجاح ✓", Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnSave);

        Button btnOpenBrowser = new Button(this);
        btnOpenBrowser.setText("فتح متصفح القنص وتسجيل الحسابات");
        btnOpenBrowser.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
            startActivity(intent);
        });
        layout.addView(btnOpenBrowser);

        setContentView(layout);
    }
}
