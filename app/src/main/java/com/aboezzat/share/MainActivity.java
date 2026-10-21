package com.aboezzat.share;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    private static final int PICK_MEDIA_REQUEST = 101;
    private EditText etTvIp;
    private SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();
    private LocalMediaServer localServer;
    private Uri selectedMediaUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        startLocalServer();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setBackgroundColor(Color.parseColor("#121212"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Abo Ezzat Share");
        tvTitle.setTextSize(26);
        tvTitle.setTextColor(Color.parseColor("#00B0FF"));
        tvTitle.setPadding(0, 0, 0, 40);
        layout.addView(tvTitle);

        etTvIp = new EditText(this);
        etTvIp.setHint("اكتب عنوان IP الشاشة (مثلاً: 192.168.1.5)");
        etTvIp.setHintTextColor(Color.GRAY);
        etTvIp.setTextColor(Color.WHITE);
        etTvIp.setBackgroundColor(Color.parseColor("#1E1E1E"));
        etTvIp.setPadding(30, 30, 30, 30);
        etTvIp.setText(prefs.getString("tv_ip", ""));
        layout.addView(etTvIp, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnSaveIp = new Button(this);
        btnSaveIp.setText("حفظ عنوان IP الشاشة ✓");
        btnSaveIp.setBackgroundColor(Color.parseColor("#00897B"));
        btnSaveIp.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 20, 0, 30);
        btnSaveIp.setLayoutParams(btnParams);
        btnSaveIp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = etTvIp.getText().toString().trim();
                prefs.edit().putString("tv_ip", ip).apply();
                Toast.makeText(MainActivity.this, "تم حفظ الـ IP بنجاح", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(btnSaveIp);

        Button btnBrowser = new Button(this);
        btnBrowser.setText("🌐 فتح المتصفح وقنص الفيديوهات");
        btnBrowser.setBackgroundColor(Color.parseColor("#0288D1"));
        btnBrowser.setTextColor(Color.WHITE);
        btnBrowser.setLayoutParams(btnParams);
        btnBrowser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
                startActivity(intent);
            }
        });
        layout.addView(btnBrowser);

        // زر بث وسائط من ذاكرة الهاتف
        Button btnLocalCast = new Button(this);
        btnLocalCast.setText("📁 بث فيديو أو صورة من الهاتف (Local Cast)");
        btnLocalCast.setBackgroundColor(Color.parseColor("#E65100"));
        btnLocalCast.setTextColor(Color.WHITE);
        btnLocalCast.setLayoutParams(btnParams);
        btnLocalCast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openMediaPicker();
            }
        });
        layout.addView(btnLocalCast);

        setContentView(layout);
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"video/*", "image/*", "audio/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, PICK_MEDIA_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedMediaUri = data.getData();
            if (selectedMediaUri != null) {
                castLocalMediaToTv();
            }
        }
    }

    private void startLocalServer() {
        try {
            if (localServer == null) {
                localServer = new LocalMediaServer(8088);
                localServer.start();
            }
        } catch (Exception ignored) {}
    }

    private String getDeviceIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) {
                return Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private void castLocalMediaToTv() {
        String tvIp = prefs.getString("tv_ip", "").trim();
        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى كتابة وحفظ IP الشاشة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        String phoneIp = getDeviceIpAddress();
        String localStreamUrl = "http://" + phoneIp + ":8088/stream";

        try {
            String endpoint = tvIp;
            if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                endpoint = "http://" + endpoint;
            }
            if (!endpoint.contains(":8080")) {
                endpoint = endpoint + ":8080";
            }

            String fullUrl = endpoint + "/?url=" + URLEncoder.encode(localStreamUrl, "UTF-8");
            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "تعذر الإرسال: تأكد أن الهاتف والشاشة على نفس الواي فاي", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "تم بث الملف المحلي للشاشة بنجاح ✓", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في الرابط المحلي", Toast.LENGTH_SHORT).show();
        }
    }

    private class LocalMediaServer extends NanoHTTPD {
        public LocalMediaServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (selectedMediaUri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(selectedMediaUri);
                    String mime = getContentResolver().getType(selectedMediaUri);
                    if (mime == null) mime = "video/mp4";
                    return newFixedLengthResponse(Response.Status.OK, mime, inputStream, inputStream.available());
                } catch (Exception e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error loading file");
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No file selected");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (localServer != null) {
            localServer.stop();
        }
    }
}
