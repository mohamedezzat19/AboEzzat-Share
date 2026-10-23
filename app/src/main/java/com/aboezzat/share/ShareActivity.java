package com.aboezzat.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
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
import java.util.ArrayList;
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
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
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

        if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            ArrayList<Uri> mediaUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (mediaUris != null && !mediaUris.isEmpty()) {
                sendMultipleMedia(mediaUris);
                return;
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
            Toast.makeText(this, "يرجى تعيين عنوان IP الشاشة أولاً", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 1. يوتيوب (مباشر)
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            sendToTv(targetTvIp, url, false);
            return;
        }

        // 2. تيك توك العادي فقط (الفيديوهات المسجلة)
        if (url.contains("tiktok.com") && !url.contains("/live") && !url.contains("live.tiktok.com")) {
            Toast.makeText(this, "جاري استخراج فيديو تيك توك...", Toast.LENGTH_SHORT).show();
            extractTikTokVideo(url);
            return;
        }

        // 3. ثريدز، تويتر، لايف تيك توك، فيسبوك
        Toast.makeText(this, "جاري قنص البث للشاشة...", Toast.LENGTH_SHORT).show();
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
                                sendToTv(targetTvIp, play, false);
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
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // User-Agent الخفيف الأصلي اللي ثريدز وتيك توك بيقبلوه بدون حظر
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
                        "   try { " +
                        "       var vids = document.getElementsByTagName('video'); " +
                        "       for (var i = 0; i < vids.length; i++) { " +
                        "           vids[i].muted = true; " +
                        "           vids[i].play(); " +
                        "           var src = vids[i].currentSrc || vids[i].src; " +
                        "           if (src && src.startsWith('http')) { " +
                        "               window.MediaSniffer.onMediaFound(src); " +
                        "           } " +
                        "       } " +
                        "   } catch(e){} " +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });

        webView.loadUrl(url);

        mainHandler.postDelayed(() -> {
            if (!isCaptured) {
                Toast.makeText(ShareActivity.this, "تعذر قنص البث تلقائياً، جرب فتحه بالمتصفح", Toast.LENGTH_SHORT).show();
                destroyWebView();
                finish();
            }
        }, 18000);
    }

    private synchronized void checkAndSendMedia(String reqUrl) {
        if (isCaptured || reqUrl == null || reqUrl.isEmpty()) return;

        String lower = reqUrl.toLowerCase();

        // استبعاد الملفات التافهة والصور
        if (lower.contains(".jpg") || lower.contains(".png") || lower.contains(".webp") ||
                lower.contains(".js") || lower.contains(".css") || lower.contains("rsrc.php") ||
                lower.contains("preview") || lower.contains(".ico")) {
            return;
        }

        // 1. قنص لايف تيك توك الصريح (مثل الذي استخرجه Web Video Caster)
        boolean isTikTokLive = (lower.contains("pull-") && lower.contains("tiktokcdn.com")) ||
                               (lower.contains("tiktokcdn.com") && (lower.contains(".flv") || lower.contains(".m3u8")));

        // 2. قنص فيديوهات ثريدز وتويتر وفيسبوك
        boolean isDirectMedia = lower.contains(".mp4") ||
                                lower.contains(".m3u8") ||
                                lower.contains("video_playback") ||
                                lower.contains("&mime=video") ||
                                (lower.contains("cdninstagram.com") && lower.contains("/v/")) ||
                                (lower.contains("threads.net") && lower.contains("video"));

        if (isTikTokLive || isDirectMedia) {
            isCaptured = true;
            mainHandler.post(() -> {
                sendToTv(targetTvIp, reqUrl, false);
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

    private void sendToTv(String tvIp, String mediaUrl, boolean isQueue) {
        try {
            if (!tvIp.startsWith("http://") && !tvIp.startsWith("https://")) {
                tvIp = "http://" + tvIp;
            }
            if (!tvIp.contains(":8080")) {
                tvIp = tvIp + ":8080";
            }

            String paramName = isQueue ? "queue" : "url";
            String fullUrl = tvIp + "/?" + paramName + "=" + URLEncoder.encode(mediaUrl, "UTF-8");
            Request request = new Request.Builder().url(fullUrl).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> {
                        Toast.makeText(ShareActivity.this, "تعذر الإرسال للشاشة", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    mainHandler.post(() -> {
                        Toast.makeText(ShareActivity.this, isQueue ? "تمت الإضافة لقائمة التشغيل ✓" : "تم الإرسال والتشغيل بنجاح ✓", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        } catch (Exception e) {
            mainHandler.post(this::finish);
        }
    }

    private void sendMultipleMedia(ArrayList<Uri> uris) {
        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        targetTvIp = prefs.getString("tv_ip", "").trim();

        if (targetTvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين عنوان IP الشاشة أولاً", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        Toast.makeText(this, "جاري إرسال قائمة الفيديوهات للشاشة...", Toast.LENGTH_SHORT).show();

        for (int i = 0; i < uris.size(); i++) {
            final boolean isQueue = (i > 0);
            final String fileUrl = uris.get(i).toString();
            mainHandler.postDelayed(() -> sendToTv(targetTvIp, fileUrl, isQueue), i * 350L);
        }
    }

    @Override
    protected void onDestroy() {
        destroyWebView();
        super.onDestroy();
    }
}
