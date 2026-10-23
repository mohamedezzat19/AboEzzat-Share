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
    private FrameLayout rootLayout;
    private WebView webView;
    private boolean isCaptured = false;
    private String targetTvIp = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        rootLayout = new FrameLayout(this);
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

        // 1. يوتيوب (ثابت وشغال 100%)
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            sendToTv(targetTvIp, url);
            return;
        }

        // 2. تيك توك العادي (ثابت وشغال 100% عبر tikwm)
        if (url.contains("tiktok.com") && !url.contains("/live")) {
            Toast.makeText(this, "جاري استخراج فيديو تيك توك...", Toast.LENGTH_SHORT).show();
            extractTikTokVideo(url);
            return;
        }

        // 3. إنستجرام: تنظيف الرابط وفك الصوت والصورة عبر صفحة الـ Embed المدمجة
        if (url.contains("instagram.com")) {
            Toast.makeText(this, "جاري استخراج ريل إنستجرام بصوت وصورة...", Toast.LENGTH_SHORT).show();
            processInstagramUrl(url);
            return;
        }

        // 4. تويتر، فيسبوك، وثريدز: القناص المباشر الشغال ممتاز
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

    private void processInstagramUrl(String rawUrl) {
        // استخراج كود المنشور أو الريل لتحويله لرابط Embed موحد
        Pattern pattern = Pattern.compile("instagram\\.com/(?:reel|reels|p)/([a-zA-Z0-9_-]+)");
        Matcher matcher = pattern.matcher(rawUrl);
        String targetEmbedUrl = rawUrl.split("\\?")[0];

        if (matcher.find()) {
            String shortCode = matcher.group(1);
            targetEmbedUrl = "https://www.instagram.com/p/" + shortCode + "/embed/captioned/";
        }

        startInstagramSniffing(targetEmbedUrl, rawUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startInstagramSniffing(String embedUrl, String fallbackUrl) {
        isCaptured = false;
        webView = new WebView(this);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(1, 1)); // ربط بالواجهة لمنع تجميد النظام له

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
                // محاكاة النقر على تشغيل وتخطي نوافذ المتابعة
                String js = "(function() { " +
                        "   var btns = document.querySelectorAll('button, div, a'); " +
                        "   for (var j=0; j<btns.length; j++) { " +
                        "       var txt = btns[j].innerText || ''; " +
                        "       if (txt.includes('ليس الآن') || txt.includes('Not Now') || txt.includes('Play')) { " +
                        "           btns[j].click(); " +
                        "       } " +
                        "   } " +
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

        webView.loadUrl(embedUrl);

        mainHandler.postDelayed(() -> {
            if (!isCaptured) {
                destroyWebView();
                startSniffing(fallbackUrl.split("\\?")[0]);
            }
        }, 10000);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startSniffing(String url) {
        isCaptured = false;
        webView = new WebView(this);
        rootLayout.addView(webView, new FrameLayout.LayoutParams(1, 1));

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

        // تنظيف الرابط من معاملات التجزئة
        String cleanUrl = reqUrl;
        if (cleanUrl.contains("&bytestart=")) {
            cleanUrl = cleanUrl.replaceAll("&bytestart=\\d+", "").replaceAll("&byteend=\\d+", "");
        }

        String lower = cleanUrl.toLowerCase();

        if (lower.contains(".jpg") || lower.contains(".png") || lower.contains(".webp") ||
            lower.contains(".js") || lower.contains(".css") || lower.contains("rsrc.php") ||
            lower.contains("preview")) {
            return;
        }

        // استبعاد مسارات الصوت المنفصلة لضمان التقاط ملف الفيديو المدمج
        boolean isAudioOnly = lower.contains("audio") || lower.contains("dash_ln_heaac");

        boolean isLiveStream = lower.contains(".m3u8") ||
                               lower.contains(".flv") ||
                               lower.contains("pull-flv") ||
                               lower.contains("pull-hls") ||
                               lower.contains("live-hls");

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

    private void destroyWebView() {
        if (webView != null) {
            try {
                rootLayout.removeView(webView);
                webView.stopLoading();
                webView.destroy();
            } catch (Exception ignored) {}
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
