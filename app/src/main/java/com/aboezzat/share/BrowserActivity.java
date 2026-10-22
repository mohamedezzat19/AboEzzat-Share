package com.aboezzat.share;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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
    private FrameLayout webContainer;
    private FrameLayout fullScreenContainer;

    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private EditText etUrl;
    private Button btnSniff;
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

    private final List<CapturedMedia> capturedList = Collections.synchronizedList(new ArrayList<CapturedMedia>());
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
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSideMenu();
            }
        });
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
        etUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                loadTargetUrl(etUrl.getText().toString().trim());
                return true;
            }
        });
        topBar.addView(etUrl, urlParams);

        btnSniff = new Button(this);
        btnSniff.setText("🎯");
        btnSniff.setTextSize(16);
        btnSniff.setTextColor(Color.WHITE);
        btnSniff.setBackgroundColor(Color.parseColor("#D32F2F"));
        btnSniff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                triggerManualSniff();
            }
        });
        LinearLayout.LayoutParams sniffParams = new LinearLayout.LayoutParams(100, ViewGroup.LayoutParams.WRAP_CONTENT);
        sniffParams.setMargins(0, 0, 5, 0);
        topBar.addView(btnSniff, sniffParams);

        btnCast = new Button(this);
        btnCast.setText("📺 0");
        btnCast.setTextSize(13);
        btnCast.setTextColor(Color.LTGRAY);
        btnCast.setBackgroundColor(Color.parseColor("#333333"));
        btnCast.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMediaListDialog();
            }
        });
        topBar.addView(btnCast, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout.addView(topBar);

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

        bottomBar.addView(createBottomButton("◀", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebView currentWeb = getCurrentWebView();
                if (currentWeb != null && currentWeb.canGoBack()) currentWeb.goBack();
            }
        }));

        bottomBar.addView(createBottomButton("▶", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebView currentWeb = getCurrentWebView();
                if (currentWeb != null && currentWeb.canGoForward()) currentWeb.goForward();
            }
        }));

        bottomBar.addView(createBottomButton("🏠", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadHomePage();
            }
        }));

        btnTabs = createBottomButton("[ 1 ]", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTabsDialog();
            }
        });
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
        final WebView webView = new WebView(this);

        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.requestFocus(View.FOCUS_DOWN);
        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_UP:
                        if (!v.hasFocus()) {
                            v.requestFocus();
                        }
                        break;
                }
                return false;
            }
        });

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
        settings.setGeolocationEnabled(true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new JsBridge(), "AboEzzatBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            // تفعيل الميكروفون للبحث الصوتي
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
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
                if (targetUrl.contains("onelink.me") || targetUrl.contains("snssdk") || targetUrl.startsWith("intent://") || !targetUrl.startsWith("http")) {
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

                // فلترة دقيقة: استبعاد التقطيع والملفات المؤقتة والإعلانات
                boolean isChunkOrAd = lowerUrl.contains(".ts") || lowerUrl.contains("seg-") || 
                                      lowerUrl.contains("bytestart") || lowerUrl.contains("adformat") || 
                                      lowerUrl.contains("init-") || lowerUrl.contains("/dash/") ||
                                      lowerUrl.contains("preview");

                if (!isChunkOrAd) {
                    // قنص روابط البث الكاملة والمباشرة
                    if (lowerUrl.contains(".mp4") || lowerUrl.contains(".m3u8") || lowerUrl.contains("pull-flv")) {
                        addCapturedMedia(reqUrl, "مقطع فيديو كامل");
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

                // قنص فوري لروابط يوتيوب الرسمية لتشغيلها على SmartTube مباشرة
                if (loadedUrl.contains("youtube.com/watch") || loadedUrl.contains("youtu.be")) {
                    addCapturedMedia(loadedUrl, view.getTitle() != null ? view.getTitle() : "فيديو يوتيوب");
                }

                String js = "javascript:(function(){" +
                        "try{" +
                        "navigator.geolocation.getCurrentPosition = function(s){s({coords:{latitude:31.0409,longitude:31.3785,accuracy:20}});};" +
                        "}catch(e){}" +
                        "var skipBtn=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern');" +
                        "if(skipBtn){skipBtn.click();}" +
                        "})()";
                view.loadUrl(js);
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
        public void onVideoPlaying(String mediaUrl, String title) {
            if (mediaUrl != null && !mediaUrl.isEmpty() && mediaUrl.startsWith("http")) {
                addCapturedMedia(mediaUrl, title);
            }
        }

        @JavascriptInterface
        public void onNewCustomSite(String name, String siteUrl) {
            saveCustomSite(name, siteUrl);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadHomePage();
                }
            });
        }
    }

    private synchronized void addCapturedMedia(String url, String title) {
        for (CapturedMedia item : capturedList) {
            if (item.url.equalsIgnoreCase(url)) return;
        }
        String cleanTitle = (title != null && !title.isEmpty()) ? title : "مقطع فيديو";
        if (cleanTitle.length() > 50) {
            cleanTitle = cleanTitle.substring(0, 50) + "...";
        }
        capturedList.add(0, new CapturedMedia(url, cleanTitle));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnCast.setText("📺 " + capturedList.size());
                btnCast.setTextColor(Color.BLACK);
                btnCast.setBackgroundColor(Color.parseColor("#FFD700"));
            }
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
            try {
                target = "https://www.google.com.eg/search?q=" + URLEncoder.encode(target, "UTF-8") + "&gl=eg&hl=ar";
            } catch (Exception e) {
                target = "https://www.google.com.eg/search?q=" + target + "&gl=eg&hl=ar";
            }
        }
        etUrl.setText(target);

        Map<String, String> extraHeaders = new HashMap<String, String>();
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
        Set<String> customSites = prefs.getStringSet("custom_sites", new HashSet<String>());

        StringBuilder customCards = new StringBuilder();
        if (customSites != null) {
            for (String entry : customSites) {
                String[] parts = entry.split("\\|", 2);
                if (parts.length == 2) {
                    customCards.append("<a href='").append(parts[1]).append("' class='card'>").append(parts[0]).append("</a>");
                }
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
                "<input type='hidden' name='gl' value='eg'>" +
                "<input type='hidden' name='hl' value='ar'>" +
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
        Set<String> set = new HashSet<String>(prefs.getStringSet("custom_sites", new HashSet<String>()));
        set.add(name + "|" + url);
        prefs.edit().putStringSet("custom_sites", set).apply();
    }

    private void triggerManualSniff() {
        final WebView current = getCurrentWebView();
        if (current == null) return;
        Toast.makeText(this, "جاري فحص الفيديو الصافي...", Toast.LENGTH_SHORT).show();

        String curUrl = current.getUrl();
        if (curUrl != null && (curUrl.contains("youtube.com/watch") || curUrl.contains("youtu.be"))) {
            addCapturedMedia(curUrl, current.getTitle() != null ? current.getTitle() : "فيديو يوتيوب");
            return;
        }

        String js = "javascript:(function(){" +
                "var vids=document.getElementsByTagName('video');" +
                "for(var i=0;i<vids.length;i++){" +
                "  var src=vids[i].currentSrc||vids[i].src;" +
                "  if(src&&src.indexOf('http')===0&&src.indexOf('blob:')!==0){" +
                "    window.AboEzzatBridge.onVideoPlaying(src,document.title);" +
                "  }" +
                "}" +
                "})()";
        current.loadUrl(js);
    }

    private void showMediaListDialog() {
        if (capturedList.isEmpty()) {
            Toast.makeText(this, "لا توجد فيديوهات، شغل الفيديو واضغط 🎯", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[capturedList.size()];
        for (int i = 0; i < capturedList.size(); i++) {
            items[i] = (i + 1) + ". " + capturedList.get(i).title;
        }

        new AlertDialog.Builder(this)
                .setTitle("الفيديوهات المكتشفة (" + capturedList.size() + ")")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showMediaOptionsDialog(capturedList.get(which));
                    }
                })
                .setPositiveButton("مسح القائمة", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        capturedList.clear();
                        btnCast.setText("📺 0");
                        btnCast.setTextColor(Color.LTGRAY);
                        btnCast.setBackgroundColor(Color.parseColor("#333333"));
                    }
                })
                .show();
    }

    private void showMediaOptionsDialog(final CapturedMedia media) {
        String[] options = {
                "📺 بث للشاشة الآن (Cast to TV)",
                "🎬 تحميل الفيديو بأعلى جودة (Full HD MP4)",
                "🎵 تحميل الصوت فقط (Audio MP3)"
        };

        new AlertDialog.Builder(this)
                .setTitle(media.title)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            castToTv(media.url);
                        } else if (which == 1) {
                            downloadMedia(media.url, false);
                        } else if (which == 2) {
                            downloadMedia(media.url, true);
                        }
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

            String fullUrl = endpoint + "/?url=" + URLEncoder.encode(mediaUrl, "UTF-8");
            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BrowserActivity.this, "تعذر الإرسال للشاشة، تأكد من الـ IP", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BrowserActivity.this, "تم الإرسال والتشغيل بنجاح ✓", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "خطأ في الرابط", Toast.LENGTH_SHORT).show();
        }
    }

    private void downloadMedia(String mediaUrl, boolean audioOnly) {
        try {
            if (mediaUrl.contains(".m3u8") || mediaUrl.contains("pull-flv")) {
                Toast.makeText(this, "البث المباشر الحي يبث للشاشة فقط ولا يتم تنزيله", Toast.LENGTH_LONG).show();
                return;
            }

            String ext = audioOnly ? ".mp3" : ".mp4";
            String title = audioOnly ? "تحميل صوت - Abo Ezzat" : "تحميل فيديو - Abo Ezzat";

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mediaUrl));
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
                "➕ فتح علامة تبويب جديدة",
                "🧹 مسح الكوكيز والبيانات المسجلة"
        };
        new AlertDialog.Builder(this)
                .setTitle("القائمة الجانبية")
                .setItems(menuItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            WebView current = getCurrentWebView();
                            if (current != null) current.reload();
                        } else if (which == 1) {
                            addNewTab("about:home");
                        } else if (which == 2) {
                            CookieManager.getInstance().removeAllCookies(null);
                            CookieManager.getInstance().flush();
                            Toast.makeText(BrowserActivity.this, "تم مسح الكوكيز بنجاح ✓", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void showTabsDialog() {
        String[] titles = new String[tabList.size()];
        for (int i = 0; i < tabList.size(); i++) {
            titles[i] = (i + 1) + ". " + tabTitles.get(i);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("علامات التبويب المفتوحة (" + tabList.size() + ")");
        builder.setItems(titles, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switchToTab(which);
            }
        });
        builder.setPositiveButton("➕ علامة تبويب جديدة", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                addNewTab("about:home");
            }
        });
        if (tabList.size() > 1) {
            builder.setNegativeButton("✖ إغلاق التبويب الحالي", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    closeCurrentTab();
                }
            });
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
