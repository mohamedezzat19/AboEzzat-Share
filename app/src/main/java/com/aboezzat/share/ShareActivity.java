package com.aboezzat.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public class ShareActivity extends Activity {

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private boolean isCaptured = false;
    private String targetTvIp = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                String extractedUrl = extractUrl(sharedText);
                if (extractedUrl != null) {
                    processUrl(extractedUrl);
                    return;
                }
            }
        }
        finish();
    }

    private String extractUrl(String text) {
        Pattern pattern = Pattern.compile("(https?://[\\w\\-\\.\\?\\=\\&\\%\\/\\#\\+]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private void processUrl(String url) {
        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        targetTvIp = prefs.getString("tv_ip", "").trim();

        if (targetTvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين عنوان IP الشاشة من داخل التطبيق أولاً", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 1. يوتيوب يُرسل فوراً
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            sendToTv(targetTvIp, url);
            return;
        }

        // 2. فيديوهات تيك توك العادية (عبر الـ API السريع)
        if (url.contains("tiktok.com") && !url.contains("/live")) {
            Toast.makeText(this, "جاري استخراج فيديو تيك توك...", Toast.LENGTH_SHORT).show();
            extractTikTokVideo(url);
            return;
        }

        // 3. البث المباشر لتيك توك أو أي منصة معقدة (فيسبوك، إنستجرام) يتم قنصها بالـ WebView
        Toast.makeText(this, "جاري قنص رابط البث للشاشة...", Toast.LENGTH_SHORT).show();
        startSniffing(url);
    }

    private void extractTikTokVideo(String url) {
        try {
            String apiUrl = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8");
            Request request = new Request.Builder().url(apiUrl).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    startSniffing(url);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        if (json.optInt("code") == 0 && json.has("data")) {
                            JSONObject data = json.getJSONObject("data");
                            String play = data.optString("play");
                            if (!play.isEmpty()) {
                                if (!play.startsWith("http")) play = "https://www.tikwm.com" + play;
                                sendToTv(targetTvIp, play);
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                    mainHandler.post(() -> startSniffing(url));
                }
            });
        } catch (Exception e) {
            startSniffing(url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startSniffing(String url) {
        isCaptured = false;
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                
                // قنص روابط البث الحي وملفات الفيديو المباشرة
                if (!isCaptured && (reqUrl.contains(".m3u8") || reqUrl.contains(".flv") || reqUrl.contains(".mp4") || reqUrl.contains("pull-flv") || reqUrl.contains("pull-hls"))) {
                    isCaptured = true;
                    mainHandler.post(() -> {
                        sendToTv(targetTvIp, reqUrl);
                        destroyWebView();
                    });
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        webView.loadUrl(url);

        // مهلة حماية 20 ثانية لإغلاق الصفحة لو لم يتم العثور على بث
        mainHandler.postDelayed(() -> {
            if (!isCaptured) {
                Toast.makeText(ShareActivity.this, "تعذر قنص البث، تأكد من أن الرابط مباشر", Toast.LENGTH_SHORT).show();
                destroyWebView();
                finish();
            }
        }, 20000);
    }

    private void destroyWebView() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
    }

    private void sendToTv(String tvIp, String mediaUrl) {
        try {
            if (!tvIp.startsWith("http://") && !tvIp.startsWith("https://")) {
                tvIp = "http://" + tvIp;
            }
            if (!tvIp.contains(":8080")) {
                tvIp = tvIp + ":8080";
            }

            String fullUrl = tvIp + "/?url=" + URLEncoder.encode(mediaUrl, "UTF-8");
            Request request = new Request.Builder().url(fullUrl).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> {
                        Toast.makeText(ShareActivity.this, "تعذر الإرسال: تأكد من الشاشة والـ IP", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    mainHandler.post(() -> {
                        Toast.makeText(ShareActivity.this, "تم قنص الفيديو وتشغيله على الشاشة بنجاح ✓", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        } catch (Exception e) {
            mainHandler.post(this::finish);
        }
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        super.onDestroy();
    }
}
