package com.aboezzat.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
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

import java.io.ByteArrayInputStream;
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

    private FrameLayout webContainer;
    private EditText etUrl;
    private Button btnCast;
    private Button btnTabs;

    private static class CapturedMedia {
        String url;
        String title;

        CapturedMedia(String url, String title) {
            this.url = url;
            this.title = title;
        }
    }

    private final List<CapturedMedia> capturedList = Collections.synchronizedList(new ArrayList<>());
    private final OkHttpClient client = new OkHttpClient();
    private String tvIp = "";

    private final List<String> adDomains = Arrays.asList(
            "doubleclick.net", "googleads", "pagead2", "adservice.google",
            "googletagmanager", "adcolony", "criteo", "adnxs", "outbrain", "taboola"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        tvIp = prefs.getString("tv_ip", "").trim();

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#121212"));

        LinearLayout topBar = new LinearLayout(this);
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
        topBar.addView(btnMenu, new LinearLayout.LayoutParams(110, ViewGroup.LayoutParams.WRAP_CONTENT));

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

        Button btnRefresh = new Button(this);
        btnRefresh.setText("🔄");
        btnRefresh.setTextSize(16);
        btnRefresh.setTextColor(Color.WHITE);
        btnRefresh.setBackgroundColor(Color.TRANSPARENT);
        btnRefresh.setOnClickListener(v -> {
            WebView currentWeb = getCurrentWebView();
            if (currentWeb != null) currentWeb.reload();
        });
        topBar.addView(btnRefresh, new LinearLayout.LayoutParams(110, ViewGroup.LayoutParams.WRAP_CONTENT));

        btnCast = new Button(this);
        btnCast.setText("📺 0");
        btnCast.setTextSize(13);
        btnCast.setTextColor(Color.LTGRAY);
        btnCast.setBackgroundColor(Color.parseColor("#333333"));
        btnCast.setOnClickListener(v -> showMediaListDialog());
        topBar.addView(btnCast, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout.addView(topBar);

        webContainer = new FrameLayout(this);
        rootLayout.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout bottomBar = new LinearLayout(this);
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

        addNewTab("about:home");
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
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");
        settings.setGeolocationEnabled(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new WebAppInterface(), "AboEzzatBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String targetUrl = request.getUrl().toString();

                if (targetUrl.startsWith("intent://") || targetUrl.startsWith("snssdk") || !targetUrl.startsWith("http")) {
                    if (targetUrl.contains("threads.net") || targetUrl.contains("threads.com")) {
                        view.loadUrl("https://www.threads.net");
                        return true;
                    }
                    if (targetUrl.contains("tiktok.com")) {
                        view.loadUrl("https://www.tiktok.com");
                        return true;
                    }
                    try {
                        Intent intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME);
                        String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                        if (fallbackUrl != null) {
                            view.loadUrl(fallbackUrl);
                            return true;
                        }
                    } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();

                for (String ad : adDomains) {
                    if (reqUrl.contains(ad)) {
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                    }
                }

                if ((reqUrl.contains("pull-flv") || reqUrl.contains("pull-hls") || reqUrl.contains(".m3u8")) &&
                        !reqUrl.contains(".ts") && !reqUrl.contains("seg-") && !reqUrl.contains("bytestart")) {
                    addCapturedMedia(request.getUrl().toString(), view.getTitle());
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                if (getCurrentWebView() == view) {
                    etUrl.setText(loadedUrl.equals("about:home") ? "" : loadedUrl);
                    tabTitles.set(currentTabIndex, view.getTitle() != null ? view.getTitle() : "علامة تبويب");
                }
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

    public class WebAppInterface {
        @JavascriptInterface
        public void onVideoPlaying(String mediaUrl, String title) {
            if (mediaUrl != null && !mediaUrl.isEmpty() && mediaUrl.startsWith("http")) {
                addCapturedMedia(mediaUrl, title);
            }
        }

        @JavascriptInterface
        public void onNewCustomSite(String name, String siteUrl) {
            saveCustomSite(name, siteUrl);
            runOnUiThread(() -> loadHomePage());
        }
    }

    private synchronized void addCapturedMedia(String url, String title) {
        for (CapturedMedia item : capturedList) {
            if (item.url.equalsIgnoreCase(url)) return;
        }
        String cleanTitle = (title != null && !title.isEmpty()) ? title : "مقطع فيديو";
        capturedList.add(0, new CapturedMedia(url, cleanTitle));
        runOnUiThread(() -> {
            btnCast.setText("📺 " + capturedList.size());
            btnCast.setTextColor(Color.BLACK);
            btnCast.setBackgroundColor(Color.parseColor("#FFD700"));
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
        btnCast.setText("📺 0");
        btnCast.setTextColor(Color.LTGRAY);
        btnCast.setBackgroundColor(Color.parseColor("#333333"));

        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://www.google.com.eg/search?q=" + URLEncoder.encode(target);
        }
        etUrl.setText(target);

        Map<String, String> extraHeaders = new HashMap<>();
        extraHeaders.put("Accept-Language", "ar-EG,ar;q=0.9,en-US;q=0.8");
        extraHeaders.put("X-Forwarded-For", "156.210.12.1");

        current.loadUrl(target, extraHeaders);
    }

    private void loadHomePage() {
        WebView current = getCurrentWebView();
        if (current == null) return;

        capturedList.clear();
        btnCast.setText("📺 0");
        btnCast.setTextColor(Color.LTGRAY);
        btnCast.setBackgroundColor(Color.parseColor("#333333"));
        etUrl.setText("");

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        Set<String> customSites = prefs.getStringSet("custom_sites", new HashSet<>());

        StringBuilder customCards = new StringBuilder();
        for (String entry : customSites) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length == 2) {
                customCards.append("<a href='").append(parts[1]).append("' class='card'>").append(parts[0]).append("</a>");
            }
        }

        String homeHtml = "<html dir='rtl'><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>body{background:#121212;color:white;font-family:sans-serif;text-align:center;padding:15px;margin:0;}" +
                ".grid{display:grid;grid-template-columns:repeat(3, 1fr);gap:12px;margin-top:20px;}" +
                ".card{background:#1E1E1E;padding:14px 5px;border-radius:10px;text-decoration:none;color:white;font-size:13px;display:block;border:1px solid #333;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}" +
                ".add-btn{background:#00897B;padding:12px;border-radius:10px;color:white;display:block;margin-top:20px;text-decoration:none;font-weight:bold;cursor:pointer;}" +
                ".search-box{width:92%;padding:12px;border-radius:25px;border:none;margin-top:20px;font-size:14px;text-align:center;background:#2A2A2A;color:white;outline:none;}" +
                "</style></head><body>" +
                "<h3 style='color:#00B0FF;margin-top:15px;'>Abo Ezzat Browser</h3>" +
                "<form action='https://www.google.com.eg/search' method='get'>" +
                "<input type='text' name='q' class='search-box' placeholder='ابحث في جوجل مصر...'>" +
                "</form>" +
                "<div class='grid'>" +
                "<a href='https://www.tiktok.com' class='card'>تيك توك</a>" +
                "<a href='https://www.facebook.com' class='card'>فيسبوك</a>" +
                "<a href='https://www.instagram.com' class='card'>إنستجرام</a>" +
                "<a href='https://m.youtube.com' class='card'>يوتيوب</a>" +
                "<a href='https://www.threads.net' class='card'>ثريد</a>" +
                "<a href='https://x.com' class='card'>تويتر / X</a>" +
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

    private void showMediaListDialog() {
        if (capturedList.isEmpty()) {
            Toast.makeText(this, "لا توجد فيديوهات مكتشفة في هذه الصفحة", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[capturedList.size()];
        for (int i = 0; i < capturedList.size(); i++) {
            items[i] = (i + 1) + ". " + capturedList.get(i).title;
        }

        new AlertDialog.Builder(this)
                .setTitle("اختر الفيديو (" + capturedList.size() + ")")
                .setItems(items, (dialog, which) -> showMediaOptionsDialog(capturedList.get(which)))
                .setPositiveButton("مسح القائمة", (dialog, which) -> {
                    capturedList.clear();
                    btnCast.setText("📺 0");
                    btnCast.setTextColor(Color.LTGRAY);
                    btnCast.setBackgroundColor(Color.parseColor("#333333"));
                })
                .show();
    }

    private void showMediaOptionsDialog(CapturedMedia media) {
        String[] options = {
                "📺 بث للشاشة الآن (Cast to TV)",
                "🎬 تحميل الفيديو بأعلى جودة (Full HD MP4)",
                "🎵 تحميل الصوت فقط (Audio MP3)"
        };

        new AlertDialog.Builder(this)
                .setTitle(media.title)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        castToTv(media.url);
                    } else if (which == 1) {
                        downloadMedia(media.url, false);
                    } else if (which == 2) {
                        downloadMedia(media.url, true);
                    }
                })
                .show();
    }

    private void castToTv(String mediaUrl) {
        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين IP الشاشة من القائمة الرئيسية أولاً", Toast.LENGTH_SHORT).show();
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

            String
