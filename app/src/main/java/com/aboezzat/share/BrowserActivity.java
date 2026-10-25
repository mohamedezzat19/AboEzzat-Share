package com.aboezzat.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BrowserActivity extends Activity {

    private final List<WebView> tabList = new ArrayList<>();
    private final List<String> tabTitles = new ArrayList<>();
    private int currentTabIndex = -1;

    private LinearLayout rootLayout;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private ProgressBar progressBar;
    private FrameLayout webContainer;
    private FrameLayout fullScreenContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private EditText etUrl;
    private Button btnSniff;
    private Button btnCast;
    private Button btnTabs;

    private PowerManager.WakeLock wakeLock;

    public static class CapturedMedia {
        public String url;
        public String title;
        public String format;
        public String posterUrl;
        public Bitmap posterBitmap;
        public int mediaType;
        public boolean isAudio;
        public boolean isSelected = false;

        public CapturedMedia(String url, String title, String format, String posterUrl, int mediaType) {
            this.url = url;
            this.title = title;
            this.format = format;
            this.posterUrl = posterUrl;
            this.mediaType = mediaType;
            this.isAudio = (mediaType == 2);
        }
    }

    private final List<CapturedMedia> capturedList = Collections.synchronizedList(new ArrayList<CapturedMedia>());
    private final OkHttpClient client = new OkHttpClient();
    private String tvIp = "";
    private String lastDiscoveredPoster = "";
    private String lastDiscoveredTitle = "";

    private final List<String> adDomains = Arrays.asList(
            "doubleclick.net", "googleads", "pagead2", "adservice.google",
            "googletagmanager", "adcolony", "criteo", "adnxs", "outbrain",
            "taboola", "popads", "popcash", "propellerads", "adsterra",
            "exoclick", "yandex.ru/ads", "smartadserver", "scorecardresearch",
            "zedo", "adroll", "pubmatic", "rubiconproject"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AboEzzatShare:AudioPlaybackWakeLock");
            }
        } catch (Exception ignored) {}

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        tvIp = prefs.getString("tv_ip", "").trim();

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#121212"));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(8, 8, 8, 8);
        topBar.setBackgroundColor(Color.parseColor("#1F1F1F"));
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button btnMenu = new Button(this);
        btnMenu.setText("☰");
        btnMenu.setTextColor(Color.WHITE);
        btnMenu.setBackgroundColor(Color.TRANSPARENT);
        btnMenu.setTextSize(20);
        btnMenu.setOnClickListener(v -> showSideMenu());
        topBar.addView(btnMenu, new LinearLayout.LayoutParams(95, ViewGroup.LayoutParams.WRAP_CONTENT));

        etUrl = new EditText(this);
        etUrl.setHint("اكتب الرابط أو ابحث...");
        etUrl.setHintTextColor(Color.GRAY);
        etUrl.setTextColor(Color.WHITE);
        etUrl.setTextSize(13);
        etUrl.setSingleLine(true);
        etUrl.setBackgroundColor(Color.parseColor("#2A2A2A"));
        etUrl.setPadding(15, 10, 15, 10);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        urlParams.setMargins(5, 0, 5, 0);
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            loadTargetUrl(etUrl.getText().toString().trim());
            return true;
        });
        topBar.addView(etUrl, urlParams);

        btnSniff = new Button(this);
        btnSniff.setText("🎯");
        btnSniff.setTextSize(16);
        btnSniff.setTextColor(Color.WHITE);
        btnSniff.setBackgroundColor(Color.parseColor("#D32F2F"));
        btnSniff.setOnClickListener(v -> triggerManualSniff());
        LinearLayout.LayoutParams sniffParams = new LinearLayout.LayoutParams(95, ViewGroup.LayoutParams.WRAP_CONTENT);
        sniffParams.setMargins(0, 5, 5, 0);
        topBar.addView(btnSniff, sniffParams);

        btnCast = new Button(this);
        btnCast.setText("📺 0");
        btnCast.setTextSize(13);
        btnCast.setTextColor(Color.BLACK);
        btnCast.setBackgroundColor(Color.parseColor("#FFC107"));
        btnCast.setVisibility(View.GONE);
        btnCast.setOnClickListener(v -> showMediaListDialog());
        topBar.addView(btnCast, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout.addView(topBar);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6));
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        rootLayout.addView(progressBar);

        FrameLayout baseFrame = new FrameLayout(this);
        webContainer = new FrameLayout(this);
        fullScreenContainer = new FrameLayout(this);
        fullScreenContainer.setVisibility(View.GONE);
        fullScreenContainer.setBackgroundColor(Color.BLACK);

        baseFrame.addView(webContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        baseFrame.addView(fullScreenContainer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        rootLayout.addView(baseFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#1F1F1F"));
        bottomBar.setPadding(10, 5, 10, 5);
        bottomBar.setGravity(Gravity.CENTER);

        bottomBar.addView(createBottomButton("◀", v -> {
            WebView currentWeb = getCurrentWebView();
            if (currentWeb != null && currentWeb.canGoBack()) currentWeb.goBack();
        }));

        bottomBar.addView(createBottomButton("▶", v -> {
            WebView currentWeb = getCurrentWebView();
            if (currentWeb != null && currentWeb.canGoForward()) currentWeb.goForward();
        }));

        bottomBar.addView(createBottomButton("🏠", v -> loadHomePage()));

        btnTabs = createBottomButton("[ 1 ]", v -> showTabsDialog());
        bottomBar.addView(btnTabs);

        rootLayout.addView(bottomBar);

        setContentView(rootLayout);

        String startUrl = "about:home";
        if (getIntent() != null) {
            if (getIntent().getStringExtra("url") != null) {
                startUrl = getIntent().getStringExtra("url");
            } else if (getIntent().getData() != null) {
                startUrl = getIntent().getData().toString();
            }
        }
        addNewTab(startUrl);
    }

    private Button createBottomButton(String text, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(16);
        btn.setBackgroundColor(Color.TRANSPARENT);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        btn.setOnClickListener(listener);
        return btn;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void addNewTab(String url) {
        final WebView webView = new WebView(this);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);
        webView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_UP:
                    if (!v.hasFocus()) {
                        v.requestFocus();
                    }
                    break;
            }
            return false;
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36");
        settings.setGeolocationEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "AboEzzatBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (getCurrentWebView() == view) {
                    if (newProgress < 100) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    } else {
                        progressBar.setProgress(100);
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;

                topBar.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                bottomBar.setVisibility(View.GONE);
                webContainer.setVisibility(View.GONE);

                fullScreenContainer.addView(customView);
                fullScreenContainer.setVisibility(View.VISIBLE);

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;

                fullScreenContainer.removeView(customView);
                fullScreenContainer.setVisibility(View.GONE);
                customView = null;

                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }

                topBar.setVisibility(View.VISIBLE);
                bottomBar.setVisibility(View.VISIBLE);
                webContainer.setVisibility(View.VISIBLE);

                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String targetUrl = request.getUrl().toString();
                String lower = targetUrl.toLowerCase();

                if (lower.contains("popads") || lower.contains("bet365") || lower.contains("redirect") ||
                    targetUrl.contains("onelink.me") || targetUrl.contains("snssdk") || 
                    targetUrl.startsWith("intent://") || (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://"))) {
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString();
                String lowerUrl = reqUrl.toLowerCase();

                for (String ad : adDomains) {
                    if (lowerUrl.contains(ad)) {
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                    }
                }

                boolean isJunk = lowerUrl.contains(".ts") || lowerUrl.contains("seg-") ||
                                 lowerUrl.contains(".js") || lowerUrl.contains(".css") ||
                                 lowerUrl.contains("preview") || lowerUrl.contains("rsrc.php") ||
                                 lowerUrl.contains("google_ads") || lowerUrl.contains("ad-interrupting");

                if (!isJunk) {
                    if (lowerUrl.contains(".mp4") || lowerUrl.contains(".flv") || 
                        lowerUrl.contains("pull-") || lowerUrl.contains(".m3u8") || 
                        lowerUrl.contains("mime=video") || lowerUrl.contains("mime=audio") || 
                        lowerUrl.contains("fbcdn.net")) {
                        
                        String cleanMedia = reqUrl;
                        if (cleanMedia.contains("&bytestart=")) {
                            cleanMedia = cleanMedia.replaceAll("&bytestart=\\d+", "").replaceAll("&byteend=\\d+", "");
                        }

                        int type = 0;
                        String format = "⚠️ مسار غير معروف";

                        if (lowerUrl.contains("audio") || lowerUrl.contains("dash_ln_heaac") || (lowerUrl.contains("bytestart=0") && lowerUrl.contains("mime=audio"))) {
                            type = 2;
                            format = "🎵 مسار صوت مؤكد";
                        } else if (lowerUrl.contains("video") || lowerUrl.contains(".mp4") || lowerUrl.contains(".m3u8") || lowerUrl.contains(".flv")) {
                            type = 1;
                            format = "🎬 مسار فيديو مؤكد";
                            if (lowerUrl.contains(".flv") || lowerUrl.contains("pull-")) format = "📡 بث حي - flv";
                            else if (lowerUrl.contains(".m3u8")) format = "📡 بث مباشر - m3u8";
                        }

                        String cleanName = !lastDiscoveredTitle.isEmpty() ? lastDiscoveredTitle : "مقطع وسائط";
                        try {
                            String pageTitle = view.getTitle();
                            if (pageTitle != null && !pageTitle.isEmpty() && !pageTitle.contains("http") && !pageTitle.contains("Instagram")) {
                                cleanName = pageTitle;
                            }
                        } catch (Exception ignored) {}

                        addCapturedMedia(cleanMedia, cleanName, format, lastDiscoveredPoster, type);
                    }
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(final WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                if (getCurrentWebView() == view) {
                    etUrl.setText(loadedUrl.equals("about:home") ? "" : loadedUrl);
                    tabTitles.set(currentTabIndex, view.getTitle() != null ? view.getTitle() : "علامة تبويب");
                }

                String adBlockAndSniffJs = "javascript:(function(){" +
                        "  var style = document.createElement('style');" +
                        "  style.innerHTML = '.ytp-ad-overlay-container, .ytp-ad-message-container, " +
                        "                     ytd-promoted-sparkles-web-renderer, ytd-banner-promo-renderer, " +
                        "                     ytd-ad-slot-renderer, .ad-container, .ad-div, " +
                        "                     [id^=\"google_ads_\"], iframe[src*=\"doubleclick\"] { display: none !important; }';" +
                        "  document.head.appendChild(style);" +
                        "  if (!window.AboEzzatAdKiller) {" +
                        "    window.AboEzzatAdKiller = setInterval(function(){" +
                        "      try {" +
                        "        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');" +
                        "        if (skipBtn) { skipBtn.click(); }" +
                        "        var adVideo = document.querySelector('video.ad-interrupting, .ad-showing video');" +
                        "        if (adVideo && !isNaN(adVideo.duration)) {" +
                        "          adVideo.currentTime = adVideo.duration;" +
                        "          adVideo.play();" +
                        "        }" +
                        "      } catch(e) {}" +
                        "    }, 300);" +
                        "  }" +
                        "  try{" +
                        "    var title = ''; var poster = '';" +
                        "    var metaTitle = document.querySelector('meta[property=\"og:title\"], meta[name=\"twitter:title\"]');" +
                        "    if(metaTitle && metaTitle.content){ title = metaTitle.content; }" +
                        "    var metaImg = document.querySelector('meta[property=\"og:image\"], meta[name=\"twitter:image\"]');" +
                        "    if(metaImg && metaImg.content){ poster = metaImg.content; }" +
                        "    window.AboEzzatBridge.onMetaFound(title, poster);" +
                        "  }catch(e){}" +
                        "  try{" +
                        "    var origPlay = HTMLMediaElement.prototype.play;" +
                        "    HTMLMediaElement.prototype.play = function(){" +
                        "      var src = this.currentSrc || this.src;" +
                        "      if(src && src.indexOf('http') === 0 && !this.classList.contains('ad-interrupting')){" +
                        "        window.AboEzzatBridge.onVideoPlayingWithPoster(src, '', '');" +
                        "      }" +
                        "      return origPlay.apply(this, arguments);" +
                        "    };" +
                        "  }catch(e){}" +
                        "})()";

                view.loadUrl(adBlockAndSniffJs);
            }
        });

        tabList.add(webView);
        tabTitles.add("علامة تبويب");
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        switchToTab(tabList.size() - 1);
        if (url.equals("about:home")) {
            loadHomePage();
        } else {
            loadTargetUrl(url);
        }
    }
    private class JsBridge {
        @JavascriptInterface
        public void onMetaFound(String title, String posterUrl) {
            if (title != null && !title.isEmpty()) {
                lastDiscoveredTitle = title.replace("Instagram: ", "").replace("on Instagram", "").trim();
            }
            if (posterUrl != null && !posterUrl.isEmpty()) {
                lastDiscoveredPoster = posterUrl;
            }
            for (CapturedMedia item : capturedList) {
                if (item.posterBitmap == null && !lastDiscoveredPoster.isEmpty()) {
                    item.posterUrl = lastDiscoveredPoster;
                    loadThumbnailAsync(item);
                }
                if (!lastDiscoveredTitle.isEmpty() && (item.title.equals("مقطع إنستجرام") || item.title.equals("مقطع وسائط"))) {
                    item.title = lastDiscoveredTitle;
                }
            }
        }

        @JavascriptInterface
        public void onVideoPlayingWithPoster(String mediaUrl, String title, String posterUrl) {
            if (mediaUrl != null && !mediaUrl.isEmpty() && mediaUrl.startsWith("http")) {
                String cleanMedia = mediaUrl;
                if (cleanMedia.contains("&bytestart=")) {
                    cleanMedia = cleanMedia.replaceAll("&bytestart=\\d+", "").replaceAll("&byteend=\\d+", "");
                }
                
                int type = 0;
                String lower = cleanMedia.toLowerCase();
                String format = "⚠️ مسار غير معروف";
                if (lower.contains("audio") || lower.contains("dash_ln_heaac")) {
                    type = 2;
                    format = "🎵 مسار صوت مؤكد";
                } else if (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".flv")) {
                    type = 1;
                    format = "🎬 مسار فيديو مؤكد";
                }

                String realTitle = !lastDiscoveredTitle.isEmpty() ? lastDiscoveredTitle : (!title.isEmpty() ? title : "مقطع وسائط");
                String realPoster = !posterUrl.isEmpty() ? posterUrl : lastDiscoveredPoster;
                addCapturedMedia(cleanMedia, realTitle, format, realPoster, type);
            }
        }

        @JavascriptInterface
        public void onNewCustomSite(String name, String siteUrl) {
            saveCustomSite(name, siteUrl);
            runOnUiThread(() -> loadHomePage());
        }
    }

    private synchronized void addCapturedMedia(String url, String title, String format, String posterUrl, int mediaType) {
        for (CapturedMedia item : capturedList) {
            if (item.url.equalsIgnoreCase(url) || (item.url.contains("manifest") && url.contains("manifest"))) {
                if (item.posterUrl == null && posterUrl != null) {
                    item.posterUrl = posterUrl;
                    loadThumbnailAsync(item);
                }
                return;
            }
        }

        String cleanTitle = (title != null && !title.isEmpty()) ? title : "مقطع فيديو";
        if (cleanTitle.length() > 50) {
            cleanTitle = cleanTitle.substring(0, 50) + "...";
        }
        final CapturedMedia newMedia = new CapturedMedia(url, cleanTitle, format, posterUrl, mediaType);
        capturedList.add(0, newMedia);

        autoSelectTracks();

        if (posterUrl != null && !posterUrl.isEmpty()) {
            loadThumbnailAsync(newMedia);
        }

        runOnUiThread(() -> {
            btnCast.setVisibility(View.VISIBLE);
            btnCast.setText("📺 " + capturedList.size());

            Animation blink = new AlphaAnimation(0.3f, 1.0f);
            blink.setDuration(350);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(4);
            btnCast.startAnimation(blink);
        });
    }

    private void autoSelectTracks() {
        boolean hasSelectedVideo = false;
        boolean hasSelectedAudio = false;

        for (CapturedMedia item : capturedList) {
            if (item.isSelected) {
                if (item.mediaType == 1) hasSelectedVideo = true;
                if (item.mediaType == 2) hasSelectedAudio = true;
            }
        }

        for (CapturedMedia item : capturedList) {
            if (!hasSelectedVideo && item.mediaType == 1) {
                item.isSelected = true;
                hasSelectedVideo = true;
            } else if (!hasSelectedAudio && item.mediaType == 2) {
                item.isSelected = true;
                hasSelectedAudio = true;
            }
        }
    }

    private void loadThumbnailAsync(final CapturedMedia media) {
        AsyncTask.execute(() -> {
            try {
                URL u = new URL(media.posterUrl);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setDoInput(true);
                conn.connect();
                InputStream input = conn.getInputStream();
                media.posterBitmap = BitmapFactory.decodeStream(input);
            } catch (Exception ignored) {}
        });
    }

    private WebView getCurrentWebView() {
        if (currentTabIndex >= 0 && currentTabIndex < tabList.size()) {
            return tabList.get(currentTabIndex);
        }
        return null;
    }

    private void switchToTab(int index) {
        if (index < 0 || index >= tabList.size()) return;
        currentTabIndex = index;
        for (int i = 0; i < tabList.size(); i++) {
            tabList.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        WebView current = getCurrentWebView();
        if (current != null) {
            String u = current.getUrl();
            etUrl.setText((u == null || u.equals("about:home")) ? "" : u);
        }
        btnTabs.setText("[ " + tabList.size() + " ]");
    }

    private void loadTargetUrl(String target) {
        WebView current = getCurrentWebView();
        if (current == null || target.isEmpty()) return;

        capturedList.clear();
        lastDiscoveredPoster = "";
        lastDiscoveredTitle = "";
        btnCast.setText("📺 0");
        btnCast.setVisibility(View.GONE);

        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            try {
                target = "https://www.google.com.eg/search?q=" + URLEncoder.encode(target, "UTF-8") + "&gl=eg&hl=ar";
            } catch (Exception e) {
                target = "https://www.google.com.eg/search?q=" + target + "&gl=eg&hl=ar";
            }
        }
        etUrl.setText(target);

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Accept-Language", "ar-EG,ar;q=0.9,en-US;q=0.8");

        current.loadUrl(target, extraHeaders);
    }

    private void loadHomePage() {
        WebView current = getCurrentWebView();
        if (current == null) return;

        capturedList.clear();
        btnCast.setText("📺 0");
        btnCast.setVisibility(View.GONE);
        etUrl.setText("");

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        Set<String> customSites = prefs.getStringSet("custom_sites", new HashSet<>());

        StringBuilder customCards = new StringBuilder();
        if (customSites != null) {
            for (String entry : customSites) {
                String[] parts = entry.split("\\|", 2);
                if (parts.length == 2) {
                    customCards.append("<a href='").append(parts[1]).append("' class='card'>")
                               .append("<div class='icon-circle' style='background:#2C3E50;'>🌐</div>")
                               .append("<span>").append(parts[0]).append("</span>")
                               .append("</a>");
                }
            }
        }

        String homeHtml = "<html dir='rtl'><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body{background:#121212;color:white;font-family:sans-serif;text-align:center;padding:15px;margin:0;}" +
                ".grid{display:grid;grid-template-columns:repeat(3, 1fr);gap:14px;margin-top:20px;}" +
                ".card{background:#1E1E1E;padding:12px 6px;border-radius:12px;text-decoration:none;color:white;font-size:12px;display:flex;flex-direction:column;align-items:center;justify-content:center;border:1px solid #2A2A2A;transition:transform 0.2s;}" +
                ".card:active{transform:scale(0.95);background:#252525;}" +
                ".card img{width:42px;height:42px;border-radius:10px;margin-bottom:8px;object-fit:cover;}" +
                ".card span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;width:100%;font-weight:bold;}" +
                ".icon-circle{width:42px;height:42px;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:22px;margin-bottom:8px;}" +
                ".add-btn{background:#00897B;padding:12px;border-radius:10px;color:white;display:block;margin-top:22px;text-decoration:none;font-weight:bold;cursor:pointer;border:1px solid #00A896;}" +
                ".search-box{width:92%;padding:12px;border-radius:25px;border:1px solid #333;margin-top:20px;font-size:14px;text-align:center;background:#2A2A2A;color:white;outline:none;}" +
                "</style></head><body>" +
                "<h3 style='color:#00B0FF;margin-top:15px;font-size:20px;'>Abo Ezzat Browser</h3>" +
                "<form action='https://www.google.com.eg/search' method='get'>" +
                "<input type='hidden' name='gl' value='eg'>" +
                "<input type='hidden' name='hl' value='ar'>" +
                "<input type='text' name='q' class='search-box' placeholder='ابحث في جوجل مصر...'>" +
                "</form>" +
                "<div class='grid'>" +
                "<a href='https://www.tiktok.com' class='card'>" +
                "<img src='https://assets.stickpng.com/images/5cb78671a7c7755bf004c14b.png' style='background:#000;' alt='TikTok'>" +
                "<span>تيك توك</span></a>" +
                "<a href='https://www.facebook.com' class='card'>" +
                "<img src='https://upload.wikimedia.org/wikipedia/commons/0/05/Facebook_Logo_%282019%29.png' alt='Facebook'>" +
                "<span>فيسبوك</span></a>" +
                "<a href='https://www.instagram.com' class='card'>" +
                "<img src='https://upload.wikimedia.org/wikipedia/commons/a/a5/Instagram_icon.png' alt='Instagram'>" +
                "<span>إنستجرام</span></a>" +
                "<a href='https://m.youtube.com' class='card'>" +
                "<img src='https://upload.wikimedia.org/wikipedia/commons/e/ef/Youtube_logo.png' alt='YouTube'>" +
                "<span>يوتيوب</span></a>" +
                "<a href='https://www.threads.net' class='card'>" +
                "<img src='https://upload.wikimedia.org/wikipedia/commons/thumb/0/01/Threads_%28app%29_logo.svg/1024px-Threads_%28app%29_logo.svg.png' style='background:#000;padding:2px;' alt='Threads'>" +
                "<span>ثريدز</span></a>" +
                "<a href='https://x.com' class='card'>" +
                "<img src='https://upload.wikimedia.org/wikipedia/commons/c/ce/X_logo_2023.svg' style='background:#000;padding:4px;' alt='X'>" +
                "<span>تويتر / X</span></a>" +
                customCards.toString() +
                "</div>" +
                "<div class='add-btn' onclick='promptAddSite()'>➕ إضافة موقع جديد للرئيسية</div>" +
                "<script>" +
                "function promptAddSite() {" +
                "  var n = prompt('اكتب اسم الموقع:');" +
                "  if(n) {" +
                "    var u = prompt('اكتب رابط الموقع:', 'https://');" +
                "    if(u) { window.AboEzzatBridge.onNewCustomSite(n, u); }" +
                "  }" +
                "}" +
                "</script></body></html>";

        current.loadDataWithBaseURL("https://www.google.com.eg", homeHtml, "text/html", "UTF-8", null);
    }

    private void saveCustomSite(String name, String url) {
        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        Set<String> set = new HashSet<>(prefs.getStringSet("custom_sites", new HashSet<>()));
        set.add(name + "|" + url);
        prefs.edit().putStringSet("custom_sites", set).apply();
    }

    private void triggerManualSniff() {
        final WebView current = getCurrentWebView();
        if (current == null) return;
        Toast.makeText(this, "جاري فحص وتأكيد الفيديو...", Toast.LENGTH_SHORT).show();

        String curUrl = current.getUrl();
        if (curUrl != null && (curUrl.contains("watch") || curUrl.contains("youtu.be"))) {
            addCapturedMedia(curUrl, current.getTitle() != null ? current.getTitle() : "فيديو يوتيوب", "🎬 فيديو مباشر", null, 1);
            return;
        }

        String js = "javascript:(function(){" +
                "var title = ''; var poster = '';" +
                "var metaTitle = document.querySelector('meta[property=\"og:title\"], meta[name=\"twitter:title\"]');" +
                "if(metaTitle && metaTitle.content){ title = metaTitle.content; }" +
                "var metaImg = document.querySelector('meta[property=\"og:image\"], meta[name=\"twitter:image\"]');" +
                "if(metaImg && metaImg.content){ poster = metaImg.content; }" +
                "if(title || poster){ window.AboEzzatBridge.onMetaFound(title, poster); }" +
                "var meta = document.querySelector('meta[property=\"og:video\"], meta[property=\"og:video:secure_url\"]');" +
                "if(meta && meta.content){" +
                "  window.AboEzzatBridge.onVideoPlayingWithPoster(meta.content, title || 'ريل إنستجرام', poster);" +
                "}" +
                "var vids = document.getElementsByTagName('video');" +
                "for(var i=0; i<vids.length; i++){" +
                "  var src = vids[i].currentSrc || vids[i].src;" +
                "  if(src && src.indexOf('http') === 0 && !vids[i].classList.contains('ad-interrupting')){" +
                "    window.AboEzzatBridge.onVideoPlayingWithPoster(src, title || document.title, poster);" +
                "  }" +
                "}" +
                "})()";
        current.loadUrl(js);
    }

    private void showMediaListDialog() {
        if (capturedList.isEmpty()) {
            Toast.makeText(this, "لا توجد فيديوهات مكتشفة، شغل الفيديو أولاً ثم اضغط 🎯", Toast.LENGTH_SHORT).show();
            return;
        }

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildMediaListView(dialog));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private View buildMediaListView(final Dialog dialog) {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(Color.parseColor("#181818"));
        main.setPadding(20, 20, 20, 20);

        TextView header = new TextView(this);
        header.setText("المسارات المكتشفة (" + capturedList.size() + ") - محددة تلقائياً");
        header.setTextColor(Color.WHITE);
        header.setTextSize(16);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, 10, 0, 15);
        main.addView(header);

        ListView listView = new ListView(this);
        listView.setDivider(new ColorDrawable(Color.parseColor("#2A2A2A")));
        listView.setDividerHeight(2);

        listView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return capturedList.size(); }

            @Override
            public Object getItem(int position) { return capturedList.get(position); }

            @Override
            public long getItemId(int position) { return position; }

            @Override
            public View getView(final int position, View convertView, ViewGroup parent) {
                final CapturedMedia item = capturedList.get(position);

                LinearLayout row = new LinearLayout(BrowserActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(10, 14, 10, 14);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundColor(Color.parseColor("#1F1F1F"));

                CheckBox cb = new CheckBox(BrowserActivity.this);
                cb.setChecked(item.isSelected);
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> item.isSelected = isChecked);
                row.addView(cb);

                LinearLayout infoLayout = new LinearLayout(BrowserActivity.this);
                infoLayout.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                infoParams.setMargins(8, 0, 10, 0);
                infoLayout.setLayoutParams(infoParams);

                TextView tvTitle = new TextView(BrowserActivity.this);
                tvTitle.setText(item.title);
                tvTitle.setTextColor(Color.WHITE);
                tvTitle.setTextSize(13);
                tvTitle.setMaxLines(2);
                tvTitle.setEllipsize(TextUtils.TruncateAt.END);
                infoLayout.addView(tvTitle);

                TextView tvFormat = new TextView(BrowserActivity.this);
                tvFormat.setText(item.format);
                int color = Color.GRAY;
                if (item.mediaType == 1) color = Color.parseColor("#4CAF50");
                else if (item.mediaType == 2) color = Color.parseColor("#FF9800");
                tvFormat.setTextColor(color);
                tvFormat.setTextSize(12);
                tvFormat.setPadding(0, 4, 0, 0);
                infoLayout.addView(tvFormat);

                row.addView(infoLayout);

                ImageView thumb = new ImageView(BrowserActivity.this);
                LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(120, 100);
                thumb.setLayoutParams(thumbParams);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);

                if (item.posterBitmap != null) {
                    thumb.setImageBitmap(item.posterBitmap);
                } else {
                    thumb.setBackgroundColor(Color.parseColor("#2C2C2C"));
                }
                row.addView(thumb);

                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    showMediaOptionsDialog(item);
                });

                return row;
            }
        });

        main.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 650));

        Button btnCastNow = new Button(this);
        btnCastNow.setText("📺 بث فوري على الشاشة (دمج وتشغيل)");
        btnCastNow.setTextColor(Color.BLACK);
        btnCastNow.setBackgroundColor(Color.parseColor("#FFD700"));
        btnCastNow.setTextSize(14);
        btnCastNow.setOnClickListener(v -> {
            dialog.dismiss();
            castSelectedTracksManually();
        });
        LinearLayout.LayoutParams btnCastParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnCastParams.setMargins(0, 10, 0, 5);
        main.addView(btnCastNow, btnCastParams);

        LinearLayout dlRow = new LinearLayout(this);
        dlRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dlRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlRowParams.setMargins(0, 5, 0, 5);
        dlRow.setLayoutParams(dlRowParams);

        Button btnDlVideo = new Button(this);
        btnDlVideo.setText("🎬 تحميل الفيديو");
        btnDlVideo.setTextColor(Color.WHITE);
        btnDlVideo.setBackgroundColor(Color.parseColor("#2E7D32"));
        btnDlVideo.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        btnDlVideo.setOnClickListener(v -> {
            dialog.dismiss();
            downloadSelectedMedia(false);
        });
        dlRow.addView(btnDlVideo);

        View spacer = new View(this);
        dlRow.addView(spacer, new LinearLayout.LayoutParams(10, ViewGroup.LayoutParams.MATCH_PARENT));

        Button btnDlAudio = new Button(this);
        btnDlAudio.setText("🎵 تحميل الصوت");
        btnDlAudio.setTextColor(Color.WHITE);
        btnDlAudio.setBackgroundColor(Color.parseColor("#E65100"));
        btnDlAudio.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        btnDlAudio.setOnClickListener(v -> {
            dialog.dismiss();
            downloadSelectedMedia(true);
        });
        dlRow.addView(btnDlAudio);

        main.addView(dlRow);

        Button btnClear = new Button(this);
        btnClear.setText("مسح القائمة");
        btnClear.setTextColor(Color.LTGRAY);
        btnClear.setBackgroundColor(Color.parseColor("#2A2A2A"));
        btnClear.setOnClickListener(v -> {
            capturedList.clear();
            btnCast.setText("📺 0");
            btnCast.setVisibility(View.GONE);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams btnClearParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnClearParams.setMargins(0, 5, 0, 0);
        main.addView(btnClear, btnClearParams);

        return main;
    }

    private void downloadSelectedMedia(boolean audioOnly) {
        String targetUrl = "";

        for (CapturedMedia item : capturedList) {
            if (item.isSelected) {
                if (audioOnly && item.mediaType == 2) {
                    targetUrl = item.url;
                    break;
                } else if (!audioOnly && item.mediaType == 1) {
                    targetUrl = item.url;
                    break;
                }
            }
        }

        if (targetUrl.isEmpty()) {
            for (CapturedMedia item : capturedList) {
                if (audioOnly && item.mediaType == 2) {
                    targetUrl = item.url;
                    break;
                } else if (!audioOnly && item.mediaType == 1) {
                    targetUrl = item.url;
                    break;
                }
            }
        }

        if (targetUrl.isEmpty() && !capturedList.isEmpty()) {
            targetUrl = capturedList.get(0).url;
        }

        if (targetUrl.isEmpty()) {
            Toast.makeText(this, "تعذر إيجاد مسار للتحميل", Toast.LENGTH_SHORT).show();
            return;
        }

        downloadMedia(targetUrl, audioOnly);
    }

    private void castSelectedTracksManually() {
        String videoTrack = "";
        String audioTrack = "";

        for (CapturedMedia item : capturedList) {
            if (item.isSelected) {
                if (item.mediaType == 1 && videoTrack.isEmpty()) {
                    videoTrack = item.url;
                } else if (item.mediaType == 2 && audioTrack.isEmpty()) {
                    audioTrack = item.url;
                } else if (videoTrack.isEmpty()) {
                    videoTrack = item.url;
                } else if (audioTrack.isEmpty()) {
                    audioTrack = item.url;
                }
            }
        }

        if (videoTrack.isEmpty()) {
            for (CapturedMedia item : capturedList) {
                if (item.mediaType == 1) {
                    videoTrack = item.url;
                    break;
                }
            }
        }

        if (videoTrack.isEmpty() && !capturedList.isEmpty()) {
            videoTrack = capturedList.get(0).url;
        }

        if (videoTrack.isEmpty()) {
            Toast.makeText(this, "يرجى تشغيل الفيديو وقنصه أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        if (audioTrack.isEmpty()) {
            castToTv(videoTrack);
            return;
        }

        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين IP الشاشة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "جاري إرسال البث المدمج للشاشة...", Toast.LENGTH_SHORT).show();

        try {
            String endpoint = tvIp;
            if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) endpoint = "http://" + endpoint;
            if (!endpoint.contains(":8081")) endpoint += ":8081";

            String fullUrl = endpoint + "/?video=" + URLEncoder.encode(videoTrack, "UTF-8") 
                                      + "&audio=" + URLEncoder.encode(audioTrack, "UTF-8");

            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تعذر الإرسال للشاشة", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تم بدء البث المدمج في الشاشة بنجاح ✓", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception ignored) {}
    }

    private void showMediaOptionsDialog(final CapturedMedia media) {
        String[] options = {
                "🔗 دمج وبث بصوت وصورة (Merge & Cast)",
                "📺 بث هذا المسار منفرداً (Cast Single Track)",
                "🎬 تحميل الفيديو (MP4)",
                "🎵 تحميل الصوت فقط (Clean Audio)"
        };

        new AlertDialog.Builder(this)
                .setTitle(media.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        mergeAndCastToTv();
                    } else if (which == 1) {
                        castToTv(media.url);
                    } else if (which == 2) {
                        downloadMedia(media.url, false);
                    } else if (which == 3) {
                        downloadMedia(media.url, true);
                    }
                })
                .show();
    }

    private void mergeAndCastToTv() {
        String videoTrackUrl = "";
        String audioTrackUrl = "";

        for (CapturedMedia m : capturedList) {
            if (m.isAudio && audioTrackUrl.isEmpty()) {
                audioTrackUrl = m.url;
            } else if (!m.isAudio && videoTrackUrl.isEmpty()) {
                videoTrackUrl = m.url;
            }
        }

        if (videoTrackUrl.isEmpty()) {
            Toast.makeText(this, "تعذر العثور على مسار الفيديو للدمج", Toast.LENGTH_SHORT).show();
            return;
        }

        if (audioTrackUrl.isEmpty()) {
            castToTv(videoTrackUrl);
            return;
        }

        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين IP الشاشة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "جاري إرسال البث المدمج للشاشة...", Toast.LENGTH_SHORT).show();

        try {
            String endpoint = tvIp;
            if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                endpoint = "http://" + endpoint;
            }
            if (!endpoint.contains(":8081")) {
                endpoint = endpoint + ":8081";
            }

            String fullUrl = endpoint + "/?video=" + URLEncoder.encode(videoTrackUrl, "UTF-8") 
                                      + "&audio=" + URLEncoder.encode(audioTrackUrl, "UTF-8");

            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تعذر الإرسال للشاشة، تأكد من الاتصال", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تم بدء العرض المدمج في الشاشة بنجاح ✓", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في الرابط", Toast.LENGTH_SHORT).show();
        }
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
            if (!endpoint.contains(":8081")) {
                endpoint = endpoint + ":8081";
            }

            String fullUrl = endpoint + "/?url=" + URLEncoder.encode(mediaUrl, "UTF-8");
            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تعذر الإرسال للشاشة، تأكد من الـ IP", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تم الإرسال والتشغيل بنجاح ✓", Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في الرابط", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadMedia(String mediaUrl, boolean audioOnly) {
        try {
            if (mediaUrl.contains(".m3u8") || mediaUrl.contains("pull-") || mediaUrl.contains(".flv")) {
                Toast.makeText(this, "البث المباشر الحي يبث للشاشة فقط ولا يتم تنزيله", Toast.LENGTH_LONG).show();
                return;
            }

            String cleanUrl = mediaUrl;
            if (cleanUrl.contains("&bytestart=")) {
                cleanUrl = cleanUrl.replaceAll("&bytestart=\\d+", "").replaceAll("&byteend=\\d+", "");
            }

            String ext = audioOnly ? (cleanUrl.contains("dash_ln_heaac") || cleanUrl.contains("mp4") ? ".m4a" : ".mp3") : ".mp4";
            String title = audioOnly ? "تحميل صوت - Abo Ezzat" : "تحميل فيديو - Abo Ezzat";

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(cleanUrl));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle(title);
            request.setDescription("جاري تنزيل الملف...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AboEzzat_" + System.currentTimeMillis() + ext);

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "بدأ التنزيل بنجاح في مجلد Downloads ✓", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "تعذر بدء التنزيل", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSideMenu() {
        String[] menuItems = {
                "🔄 تحديث الصفحة الحالية",
                "📂 مجلد التنزيلات (Downloads)",
                "➕ فتح علامة تبويب جديدة",
                "🧹 مسح الكوكيز والبيانات المسجلة"
        };
        new AlertDialog.Builder(this)
                .setTitle("القائمة الجانبية")
                .setItems(menuItems, (dialog, which) -> {
                    if (which == 0) {
                        WebView current = getCurrentWebView();
                        if (current != null) current.reload();
                    } else if (which == 1) {
                        openDownloadsFolder();
                    } else if (which == 2) {
                        addNewTab("about:home");
                    } else if (which == 3) {
                        CookieManager.getInstance().removeAllCookies(null);
                        CookieManager.getInstance().flush();
                        Toast.makeText(BrowserActivity.this, "تم مسح الكوكيز بنجاح ✓", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void openDownloadsFolder() {
        try {
            Intent intent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Uri uri = Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath());
                intent.setDataAndType(uri, "*/*");
                startActivity(Intent.createChooser(intent, "فتح التنزيلات"));
            } catch (Exception ex) {
                Toast.makeText(this, "تعذر فتح مجلد التنزيلات تلقائياً", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showTabsDialog() {
        String[] titles = new String[tabList.size()];
        for (int i = 0; i < tabList.size(); i++) {
            titles[i] = (i + 1) + ". " + tabTitles.get(i);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("علامات التبويب المفتوحة (" + tabList.size() + ")");
        builder.setItems(titles, (dialog, which) -> switchToTab(which));
        builder.setPositiveButton("➕ علامة تبويب جديدة", (dialog, which) -> addNewTab("about:home"));
        if (tabList.size() > 1) {
            builder.setNegativeButton("✖ إغلاق التبويب الحالي", (dialog, which) -> closeCurrentTab());
        }
        builder.show();
    }

    private void closeCurrentTab() {
        if (tabList.size() <= 1) return;
        WebView toRemove = tabList.remove(currentTabIndex);
        tabTitles.remove(currentTabIndex);
        webContainer.removeView(toRemove);
        toRemove.destroy();
        switchToTab(Math.max(0, currentTabIndex - 1));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire(60 * 60 * 1000L);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
            }
            return;
        }
        WebView current = getCurrentWebView();
        if (current != null && current.canGoBack()) {
            current.goBack();
        } else if (tabList.size() > 1) {
            closeCurrentTab();
        } else {
            super.onBackPressed();
        }
    }
}
