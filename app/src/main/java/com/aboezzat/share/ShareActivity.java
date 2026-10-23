package com.aboezzat.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

public class ShareActivity extends Activity {

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private boolean isCaptured = false;
    private String targetTvIp = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.TRANSPARENT);
        ProgressBar progressBar = new ProgressBar(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.CENTER;
        rootLayout.addView(progressBar, params);
        setContentView(rootLayout);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
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

        // 1. يوتيوب (مباشر للشاشة دون أي تعديل)
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            sendToTv(targetTvIp, url);
            return;
        }

        // 2. تيك توك العادي عبر API tikwm الخارق (دون أي تعديل)
        if (url.contains("tiktok.com") && !url.contains("/live")) {
            Toast.makeText(this, "جاري استخراج فيديو تيك توك...", Toast.LENGTH_SHORT).show();
            extractTikTokVideo(url);
            return;
        }

        // 3. إنستجرام وثريدز (استخراج الرابط المدمج بصوت وصورة)
        if (url.contains("instagram.com") || url.contains("threads.net")) {
            Toast.makeText(this, "جاري استخراج فيديو بصوت وصورة...", Toast.LENGTH_SHORT).show();
            extractInstagramAndThreads(url);
            return;
        }

        // 4. تويتر، فيسبوك، ولايف تيك توك عبر القناص التلقائي
        Toast.makeText(this, "جاري قنص وتشغيل الفيديو للشاشة...", Toast.LENGTH_SHORT).show();
        startSniffing(url);
    }

    private void extractTikTokVideo(final String url) {
        try {
            String apiUrl = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8");
            Request request = new Request.Builder().url(apiUrl).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> startSniffing(url));
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

    private void extractInstagramAndThreads(final String originalUrl) {
        try {
            // استخدام واجهة عامة لدمج واستخراج مسار الفيديو مع الصوت معاً
            String cleanUrl = originalUrl.split("\\?")[0];
            String endpoint = "https://api.cobalt.tools/api/json";

            JSONObject jsonPayload = new JSONObject();
            jsonPayload.put("url", cleanUrl);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    jsonPayload.toString()
            );

            Request request = new Request.Builder()
                    .url(endpoint)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> startSniffing(originalUrl));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String respData = response.body().string();
                            JSONObject json = new JSONObject(respData);

                            // رابط الفيديو المباشر المدمج بصوته
                            String streamUrl = json.optString("url");
                            if (streamUrl.isEmpty() && json.has("picker")) {
                                JSONArray picker = json.getJSONArray("picker");
                                if (picker.length() > 0) {
                                    streamUrl = picker.getJSONObject(0).optString("url");
                                }
                            }

                            if (!streamUrl.isEmpty()) {
                                sendToTv(targetTvIp, streamUrl);
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                    // إذا تعذر الاستخراج نعود فوراً للقناص
                    mainHandler.post(() -> startSniffing(originalUrl));
                }
            });
        } catch (Exception e) {
            startSniffing(originalUrl);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startSniffing(String url) {
        isCaptured = false;
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onMediaFound(String mediaUrl) {
                checkAndSendMedia(mediaUrl);
            }
        }, "MediaSniffer");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                checkAndSendMedia(reqUrl);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                String js = "(function() { " +
                        "   var vids = document.getElementsByTagName('video'); " +
                        "   for (var i = 0; i < vids.length; i++) { " +
                        "       try { vids[i].play(); } catch(e){} " +
                        "       var src = vids[i].currentSrc || vids[i].src; " +
                        "       if (src && src.startsWith('http')) { " +
                        "           window.MediaSniffer.onMediaFound(src); " +
                        "       } " +
                        "   } " +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });

        webView.loadUrl(url);

        mainHandler.postDelayed(() -> {
            if (!isCaptured) {
                Toast.makeText(ShareActivity.this, "تعذر قنص الفيديو تلقائياً", Toast.LENGTH_SHORT).show();
                destroyWebView();
                finish();
            }
        }, 18000);
    }

    private synchronized void checkAndSendMedia(String reqUrl) {
        if (isCaptured || reqUrl == null || reqUrl.isEmpty()) return;

        String lower = reqUrl.toLowerCase();

        // 1. استبعاد ملفات الصور والنظام
        if (lower.contains(".jpg") || lower.contains(".png") || lower.contains(".webp") ||
            lower.contains(".js") || lower.contains(".css") || lower.contains("rsrc.php") ||
            lower.contains("preview")) {
            return;
        }

        // 2. قنص البث الحي (HLS / تيك توك لايف FLV)
        boolean isLiveStream = lower.contains(".m3u8") ||
                               lower.contains(".flv") ||
                               lower.contains("pull-flv") ||
                               lower.contains("pull-hls") ||
                               lower.contains("live-hls");

        // 3. قنص الفيديوهات الصريحة (تويتر، ثريدز، فيسبوك، إنستجرام)
        boolean isDirectVideo = (lower.contains(".mp4") && !lower.contains("bytestart=")) ||
                                lower.contains("video_playback") ||
                                lower.contains("&mime=video");

        if (isLiveStream || isDirectVideo) {
            isCaptured = true;
            mainHandler.post(() -> {
                sendToTv(targetTvIp, reqUrl);
                destroyWebView();
            });
        }
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
                        Toast.makeText(ShareActivity.this, "تعذر الإرسال للشاشة: تأكد من تشغيل الشاشة والـ IP", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    mainHandler.post(() -> {
                        Toast.makeText(ShareActivity.this, "تم الإرسال والتشغيل بنجاح ✓", Toast.LENGTH_SHORT).show();
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
