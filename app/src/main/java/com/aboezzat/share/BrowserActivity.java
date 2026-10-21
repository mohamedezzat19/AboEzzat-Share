package com.aboezzat.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BrowserActivity extends Activity {

    private WebView webView;
    private Button btnSniff;
    private final List<String> capturedStreams = Collections.synchronizedList(new ArrayList<>());
    private final OkHttpClient client = new OkHttpClient();
    private String tvIp = "";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        tvIp = prefs.getString("tv_ip", "").trim();

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(10, 10, 10, 10);

        EditText etUrl = new EditText(this);
        etUrl.setHint("اكتب الرابط هنا...");
        etUrl.setText("https://www.google.com");
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        topBar.addView(etUrl, etParams);

        Button btnGo = new Button(this);
        btnGo.setText("فتح");
        btnGo.setOnClickListener(v -> {
            String target = etUrl.getText().toString().trim();
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://" + target;
            }
            webView.loadUrl(target);
        });
        topBar.addView(btnGo);
        mainLayout.addView(topBar);

        FrameLayout webContainer = new FrameLayout(this);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        btnSniff = new Button(this);
        btnSniff.setText("▶ 0");
        btnSniff.setBackgroundColor(Color.parseColor("#FFD700"));
        btnSniff.setTextColor(Color.BLACK);
        btnSniff.setVisibility(View.GONE);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.setMargins(0, 0, 40, 40);
        btnSniff.setLayoutParams(fabParams);
        btnSniff.setOnClickListener(v -> showStreamsDialog());
        webContainer.addView(btnSniff);

        mainLayout.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(mainLayout);

        setupSniffer();

        String startUrl = getIntent().getStringExtra("url");
        if (startUrl != null && !startUrl.isEmpty()) {
            etUrl.setText(startUrl);
            webView.loadUrl(startUrl);
        } else {
            webView.loadUrl("https://www.google.com");
        }
    }

    private void setupSniffer() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                String lower = reqUrl.toLowerCase();

                boolean isMedia = lower.contains(".m3u8") ||
                                  lower.contains(".flv") ||
                                  lower.contains("pull-flv") ||
                                  lower.contains("pull-hls") ||
                                  lower.contains(".mp4");

                if (isMedia && !lower.contains(".jpg") && !lower.contains(".png") && !lower.contains(".js") && !lower.contains(".css")) {
                    if (!capturedStreams.contains(reqUrl)) {
                        capturedStreams.add(reqUrl);
                        runOnUiThread(() -> {
                            btnSniff.setText("▶ " + capturedStreams.size());
                            btnSniff.setVisibility(View.VISIBLE);
                        });
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }

    private void showStreamsDialog() {
        if (capturedStreams.isEmpty()) return;

        String[] streamItems = new String[capturedStreams.size()];
        for (int i = 0; i < capturedStreams.size(); i++) {
            String url = capturedStreams.get(i);
            String label = (url.contains(".m3u8") || url.contains("flv")) ? "بث مباشر / Live" : "ملف فيديو / MP4";
            streamItems[i] = (i + 1) + ". " + label;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("الروابط المكتشفة (" + capturedStreams.size() + ")");
        builder.setItems(streamItems, (dialog, which) -> {
            String selectedUrl = capturedStreams.get(which);
            showActionDialog(selectedUrl);
        });
        builder.setPositiveButton("مسح القائمة", (dialog, which) -> {
            capturedStreams.clear();
            btnSniff.setVisibility(View.GONE);
        });
        builder.show();
    }

    private void showActionDialog(String mediaUrl) {
        String[] actions = {"بث إلى الشاشة (Cast)", "تحميل إلى الهاتف (Download)"};
        new AlertDialog.Builder(this)
                .setTitle("اختر الإجراء")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        castToTv(mediaUrl);
                    } else {
                        downloadMedia(mediaUrl);
                    }
                })
                .show();
    }

    private void castToTv(String mediaUrl) {
        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين IP الشاشة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String endpoint = tvIp;
            if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                endpoint = "http://" + endpoint;
            }
            if (!endpoint.contains(":8080")) {
                endpoint = endpoint + ":8080";
            }
            String fullUrl = endpoint + "/?url=" + URLEncoder.encode(mediaUrl, "UTF-8");
            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "فشل الإرسال للشاشة", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تم الإرسال والتشغيل ✓", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في الرابط", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadMedia(String mediaUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mediaUrl));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle("تحميل الوسائط - Abo Ezzat");
            request.setDescription("جاري تنزيل الملف...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            String ext = mediaUrl.contains(".mp4") ? ".mp4" : (mediaUrl.contains(".m3u8") ? ".m3u8" : ".flv");
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AboEzzat_" + System.currentTimeMillis() + ext);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "بدأ التحميل في الخلفية ✓", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "تعذر بدء التحميل عبر مدير التنزيل", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
