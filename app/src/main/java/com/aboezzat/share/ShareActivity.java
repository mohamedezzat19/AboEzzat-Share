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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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

        // 1. يوتيوب (ثابت كما هو)
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            sendToTv(targetTvIp, url);
            return;
        }

        // 2. تيك توك العادي (ثابت كما هو عبر tikwm)
        if (url.contains("tiktok.com") && !url.contains("/live")) {
            Toast.makeText(this, "جاري استخراج فيديو تيك توك...", Toast.LENGTH_SHORT).show();
            extractTikTokVideo(url);
            return;
        }

        // 3. لايف تيك توك (استخراج مباشر عبر Live API الداخلي)
        if (url.contains("tiktok.com") && url.contains("/live")) {
            Toast.makeText(this, "جاري قنص بث تيك توك المباشر...", Toast.LENGTH_SHORT).show();
            extractTikTokLive(url);
            return;
        }

        // 4. إنستجرام: جلب رابط الـ Progressive MP4 (صوت + صورة مدمجين) من الصفحة
        if (url.contains("instagram.com")) {
            Toast.makeText(this, "جاري استخراج ريل إنستجرام بصوت وصورة...", Toast.LENGTH_SHORT).show();
            startInstagramSniffing(url);
            return;
        }

        // 5. باقي المنصات (تويتر، ثريدز، فيسبوك) - القناص المباشر
        Toast.makeText(this, "جاري قنص وتشغيل الفيديو للشاشة...", Toast.LENGTH_SHORT).show();
        startGeneralSniffing(url);
    }

    private void extractTikTokVideo(final String url) {
        try {
            String apiUrl = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8");
            Request request = new Request.Builder().url(apiUrl).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> startGeneralSniffing(url));
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
                    mainHandler.post(() -> startGeneralSniffing(url));
                }
            });
        } catch (Exception e) {
            startGeneralSniffing(url);
        }
    }

    private void extractTikTokLive(final String url) {
        // محاولة استخراج اسم الحساب من رابط البث
        Pattern pattern = Pattern.compile("@([a-zA-Z0-9_.-]+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            String username = matcher.group(1);
            String apiUrl = "https://www.tiktok.com/api-live/user/room/?uniqueId=" + username + "&sourceType=54";

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> startGeneralSniffing(url));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String resp = response.body().string();
                        JSONObject json = new JSONObject(resp);
                        JSONObject liveRoom = json.getJSONObject("data").getJSONObject("liveRoom");
                        String streamDataStr = liveRoom.getJSONObject("streamUrl").getString("live_core_sdk_data");
                        JSONObject streamObj = new JSONObject(streamDataStr);
                        JSONObject pullDatas = streamObj.getJSONObject("pull_data").getJSONObject("stream_data");

                        // استخراج أول رابط بث حي متاح (FLV أو HLS)
                        String pullUrl = "";
                        for (java.util.Iterator<String> it = pullDatas.keys(); it.hasNext(); ) {
                            String key = it.next();
                            JSONObject quality = pullDatas.getJSONObject(key);
                            if (quality.has("main")) {
                                JSONObject mainStream = quality.getJSONObject("main");
                                pullUrl = mainStream.optString("flv", mainStream.optString("hls"));
                                if (!pullUrl.isEmpty()) break;
                            }
                        }

                        if (!pullUrl.isEmpty()) {
                            final String liveStream = pullUrl;
                            mainHandler.post(() -> sendToTv(targetTvIp, liveStream));
                            return;
                        }
                    } catch (Exception ignored) {}
                    mainHandler.post(() -> startGeneralSniffing(url));
                }
            });
        } else {
            startGeneralSniffing(url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startInstagramSniffing(String url) {
        isCaptured = false;
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onMediaFound(String mediaUrl) {
                if (!isCaptured && mediaUrl != null && !mediaUrl.isEmpty()) {
                    isCaptured = true;
                    mainHandler.post(() -> {
                        sendToTv(targetTvIp, cleanCdnUrl(mediaUrl));
                        destroyWebView();
                    });
                }
            }
        }, "MediaSniffer");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                // فحص كود الصفحة لاستخراج رابط الفيديو الكامل المدمج (og:video أو وسوم الفيديو)
                String js = "(function() { " +
                        "   var ogVideo = document.querySelector('meta[property=\"og:video\"]'); " +
                        "   if (ogVideo && ogVideo.content) { " +
                        "       window.MediaSniffer.onMediaFound(ogVideo.content); " +
                        "       return; " +
                        "   } " +
                        "   var vids = document.getElementsByTagName('video'); " +
                        "   for (var i = 0; i < vids.length; i++) { " +
                        "       var src = vids[i].src || vids[i].currentSrc; " +
                        "       if (src && src.startsWith('http')) { " +
                        "           window.MediaSniffer.onMediaFound(src); " +
                        "           return; " +
                        "       } " +
                        "   } " +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });

        webView.loadUrl(url);

        mainHandler.postDelayed(() -> {
            if (!isCaptured) {
                destroyWebView();
                startGeneralSniffing(url);
            }
        }, 8000);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startGeneralSniffing(String url) {
        isCaptured = false;
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
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
                        "   var btns = document.querySelectorAll('button, div'); " +
                        "   for (var j=0; j<btns.length; j++) { " +
                        "       if (btns[j].innerText && (btns[j].innerText.includes('Play') || btns[j].innerText.includes('تشغيل') || btns[j].innerText.includes('Watch'))) { " +
                        "           btns[j].click(); " +
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

        String cleanUrl = cleanCdnUrl(reqUrl);
        String lower = cleanUrl.toLowerCase();

        // استبعاد الملفات غير المرغوبة
        if (lower.contains(".jpg") || lower.contains(".png") || lower.contains(".webp") ||
            lower.contains(".js") || lower.contains(".css") || lower.contains("rsrc.php") ||
            lower.contains("preview")) {
            return;
        }

        // قنص البث الحي HLS / FLV
        boolean isLiveStream = lower.contains(".m3u8") ||
                               lower.contains(".flv") ||
                               lower.contains("pull-flv") ||
                               lower.contains("pull-hls") ||
                               lower.contains("live-hls");

        // قنص الفيديوهات الصريحة مع استبعاد روابط الصوت المنفصلة
        boolean isAudioOnly = lower.contains("audio") || lower.contains("dash_ln_heaac");
        boolean isDirectVideo = (lower.contains(".mp4") && !isAudioOnly) ||
                                lower.contains("video_playback") ||
                                lower.contains("&mime=video");

        if (isLiveStream || isDirectVideo) {
            isCaptured = true;
            final String finalMedia = cleanUrl;
            mainHandler.post(() -> {
                sendToTv(targetTvIp, finalMedia);
                destroyWebView();
            });
        }
    }

    private String cleanCdnUrl(String rawUrl) {
        // إزالة محددات الأجزاء ليعمل الملف كتدفق كامل بصوت وصورة
        if (rawUrl.contains("&bytestart=")) {
            return rawUrl.replaceAll("&bytestart=\\d+", "").replaceAll("&byteend=\\d+", "");
        }
        return rawUrl;
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
