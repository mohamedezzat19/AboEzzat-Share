package com.aboezzat.share;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
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

        Button btnLocalCast = new Button(this);
        btnLocalCast.setText("📁 بث فيديو أو صورة أو صوت (Local Cast)");
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
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_MEDIA_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedMediaUri = data.getData();
            if (selectedMediaUri != null) {
                try {
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(selectedMediaUri, takeFlags);
                } catch (Exception ignored) {}
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
                int ip = wm.getConnectionInfo().getIpAddress();
                return Formatter.formatIpAddress(ip);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void castLocalMediaToTv() {
        String tvIp = prefs.getString("tv_ip", "").trim();
        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى كتابة وحفظ IP الشاشة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        String phoneIp = getDeviceIpAddress();
        if (phoneIp.isEmpty() || phoneIp.equals("0.0.0.0")) {
            Toast.makeText(this, "تأكد من اتصال الهاتف بالواي فاي", Toast.LENGTH_LONG).show();
            return;
        }

        String mime = getContentResolver().getType(selectedMediaUri);
        String targetPath = (mime != null && mime.startsWith("image/")) ? "/view_image" : "/media";
        String localStreamUrl = "http://" + phoneIp + ":8088" + targetPath;

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
                            Toast.makeText(MainActivity.this, "تعذر الإرسال: تأكد أن الشاشة والهاتف على نفس الشبكة", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "جاري العرض على الشاشة الآن ✓", Toast.LENGTH_SHORT).show();
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
            String uriPath = session.getUri();

            if (selectedMediaUri != null) {
                // عرض الصور بصفحة شاشة كاملة نظيفة
                if ("/view_image".equals(uriPath)) {
                    String phoneIp = getDeviceIpAddress();
                    String html = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                            "<style>body{margin:0;background:#000;display:flex;justify-content:center;align-items:center;height:100vh;overflow:hidden;}" +
                            "img{max-width:100%;max-height:100%;object-fit:contain;}</style></head>" +
                            "<body><img src='http://" + phoneIp + ":8088/media'></body></html>";
                    return newFixedLengthResponse(Response.Status.OK, "text/html", html);
                }

                // بث ملفات الفيديو أو الصوت أو الصورة الخام
                if ("/media".equals(uriPath)) {
                    try {
                        AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(selectedMediaUri, "r");
                        if (afd != null) {
                            FileInputStream fis = afd.createInputStream();
                            long fileLength = afd.getLength();
                            String mime = getContentResolver().getType(selectedMediaUri);
                            if (mime == null) {
                                mime = "video/mp4";
                            }

                            Response res = newFixedLengthResponse(Response.Status.OK, mime, fis, fileLength);
                            res.addHeader("Accept-Ranges", "bytes");
                            res.addHeader("Access-Control-Allow-Origin", "*");
                            return res;
                        }
                    } catch (Exception e) {
                        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error loading file");
                    }
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No media selected");
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
