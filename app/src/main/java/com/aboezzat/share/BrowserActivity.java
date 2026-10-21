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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private String activeMediaUrl = "";
    private String activeMediaTitle = "";

    private final OkHttpClient client = new OkHttpClient();
    private String tvIp = "";

    private final List<String> adDomains = Arrays.asList(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "adservice.google.com", "googletagmanager.com", "adcolony.com", "criteo.com"
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        tvIp = prefs.getString("tv_ip", "").trim();

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#121212"));

        // 1. الشريط العلوي (Top Bar)
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
        btnCast.setOnClickListener(v -> showMediaOptionsDialog());
        topBar.addView(btnCast, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout.addView(topBar);

        // 2. حاوية صفحات التبويبات المتعددة
        webContainer = new FrameLayout(this);
        rootLayout.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // 3. الشريط السفلي (Bottom Bar)
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#1F1F1F"));
        bottomBar.setPadding(10, 5, 10, 5);
        bottomBar.setGravity(Gravity.CENTER);

        Button btnBack = createBottomButton("◀", v -> {
            WebView currentWeb = getCurrentWebView();
            if (currentWeb != null && currentWeb.canGoBack()) currentWeb.goBack();
        });
        bottomBar.addView(btnBack);

        Button btnForward = createBottomButton("▶", v -> {
            WebView currentWeb = getCurrentWebView();
            if (currentWeb != null && currentWeb.canGoForward()) currentWeb.goForward();
        });
        bottomBar.addView(btnForward);

        Button btnHome = createBottomButton("🏠", v -> loadHomePage());
        bottomBar.addView(btnHome);

        btnTabs = createBottomButton("[ 1 ]", v -> showTabsDialog());
        bottomBar.addView(btnTabs);

        rootLayout.addView(bottomBar);

        setContentView(rootLayout);

        // فتح أول علامة تبويب افتراضياً
        addNewTab("https://www.google.com");
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

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void addNewTab(String url) {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // ضبط الصفحة لتناسب شاشة الموبايل تماماً وتمنع الخروج يميناً ويساراً
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onVideoPlaying(String mediaUrl, String title) {
                if (mediaUrl != null && !mediaUrl.isEmpty() && mediaUrl.startsWith("http")) {
                    activeMediaUrl = mediaUrl;
                    activeMediaTitle = (title != null && !title.isEmpty()) ? title : "الفيديو المعروض حالياً";
                    runOnUiThread(() -> {
                        btnCast.setText("📺 1");
                        btnCast.setTextColor(Color.BLACK);
                        btnCast.setBackgroundColor(Color.parseColor("#FFD700"));
                    });
                }
            }
        }, "AboEzzatPlayer");

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();

                // حجب الإعلانات المزعجة
                for (String ad : adDomains) {
                    if (reqUrl.contains(ad)) {
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                    }
                }

                // قنص البث الحي الصريح
                if ((reqUrl.contains("pull-flv") || reqUrl.contains("pull-hls") || reqUrl.contains(".m3u8")) &&
                        !reqUrl.contains(".ts") && !reqUrl.contains("seg-") && !reqUrl.contains("bytestart")) {
                    activeMediaUrl = request.getUrl().toString();
                    runOnUiThread(() -> {
                        btnCast.setText("📺 1");
                        btnCast.setTextColor(Color.BLACK);
                        btnCast.setBackgroundColor(Color.parseColor("#FFD700"));
                    });
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String loadedUrl) {
                super.onPageFinished(view, loadedUrl);
                if (getCurrentWebView() == view) {
                    etUrl.setText(loadedUrl);
                    tabTitles.set(currentTabIndex, view.getTitle() != null ? view.getTitle() : "علامة تبويب");
                }

                // ربط حدث Play بالزر العلوي فقط للفيديو الشغال
                String js = "(function() { " +
                        "   function scan() { " +
                        "       var vids = document.getElementsByTagName('video'); " +
                        "       for (var i = 0; i < vids.length; i++) { " +
                        "           if (!vids[i].hasAttribute('data-sniffed')) { " +
                        "               vids[i].setAttribute('data-sniffed', 'true'); " +
                        "               vids[i].addEventListener('play', function(e) { " +
                        "                   var src = e.target.currentSrc || e.target.src; " +
                        "                   if (src && src.startsWith('http')) { " +
                        "                       window.AboEzzatPlayer.onVideoPlaying(src, document.title); " +
                        "                   } " +
                        "               }); " +
                        "           } " +
                        "       } " +
                        "   } " +
                        "   scan(); " +
                        "   setInterval(scan, 1200); " +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });

        tabList.add(webView);
        tabTitles.add("علامة تبويب");
        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        switchToTab(tabList.size() - 1);
        loadTargetUrl(url);
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
            etUrl.setText(current.getUrl() != null ? current.getUrl() : "");
        }
        btnTabs.setText("[ " + tabList.size() + " ]");
        resetCastButton();
    }

    private void resetCastButton() {
        activeMediaUrl = "";
        btnCast.setText("📺 0");
        btnCast.setTextColor(Color.LTGRAY);
        btnCast.setBackgroundColor(Color.parseColor("#333333"));
    }

    private void loadTargetUrl(String target) {
        WebView current = getCurrentWebView();
        if (current == null || target.isEmpty()) return;

        resetCastButton();

        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://www.google.com.eg/search?q=" + URLEncoder.encode(target);
        }
        etUrl.setText(target);

        java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
        extraHeaders.put("Accept-Language", "ar-EG,ar;q=0.9,en-US;q=0.8");
        extraHeaders.put("X-Forwarded-For", "156.210.12.1");

        current.loadUrl(target, extraHeaders);
    }

    private void loadHomePage() {
        WebView current = getCurrentWebView();
        if (current == null) return;
        resetCastButton();
        etUrl.setText("");

        String homeHtml = "<html dir='rtl'><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>body{background:#121212;color:white;font-family:sans-serif;text-align:center;padding:20px;}" +
                ".grid{display:grid;grid-template-columns:repeat(3, 1fr);gap:15px;margin-top:25px;}" +
                ".card{background:#1E1E1E;padding:15px;border-radius:10px;text-decoration:none;color:white;font-size:14px;display:block;border:1px solid #333;}" +
                ".search-box{width:90%;padding:12px;border-radius:25px;border:none;margin-top:20px;font-size:15px;text-align:center;background:#2A2A2A;color:white;}" +
                "</style></head><body>" +
                "<h2 style='color:#00B0FF;margin-top:20px;'>Abo Ezzat Browser</h2>" +
                "<form action='https://www.google.com/search' method='get'>" +
                "<input type='text' name='q' class='search-box' placeholder='ابحث في جوجل...'>" +
                "</form>" +
                "<div class='grid'>" +
                "<a href='https://www.tiktok.com' class='card'>تيك توك</a>" +
                "<a href='https://www.facebook.com' class='card'>فيسبوك</a>" +
                "<a href='https://www.instagram.com' class='card'>إنستجرام</a>" +
                "<a href='https://m.youtube.com' class='card'>يوتيوب</a>" +
                "<a href='https://www.threads.net' class='card'>ثريد</a>" +
                "<a href='https://x.com' class='card'>تويتر / X</a>" +
                "</div></body></html>";

        current.loadDataWithBaseURL("https://www.google.com.eg", homeHtml, "text/html", "UTF-8", null);
    }

    private void showSideMenu() {
        String[] menuItems = {
                "➕ فتح علامة تبويب جديدة",
                "🔄 تحديث الصفحة",
                "🧹 مسح الكوكيز والبيانات المسجلة"
        };
        new AlertDialog.Builder(this)
                .setTitle("القائمة الجانبية")
                .setItems(menuItems, (dialog, which) -> {
                    if (which == 0) {
                        addNewTab("https://www.google.com");
                    } else if (which == 1) {
                        WebView current = getCurrentWebView();
                        if (current != null) current.reload();
                    } else if (which == 2) {
                        CookieManager.getInstance().removeAllCookies(null);
                        CookieManager.getInstance().flush();
                        Toast.makeText(this, "تم مسح الكوكيز بنجاح ✓", Toast.LENGTH_SHORT).show();
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
        builder.setItems(titles, (dialog, which) -> switchToTab(which));
        builder.setPositiveButton("➕ علامة تبويب جديدة", (dialog, which) -> addNewTab("https://www.google.com"));
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

    private void showMediaOptionsDialog() {
        if (activeMediaUrl.isEmpty()) {
            Toast.makeText(this, "لا يوجد فيديو شغال حالياً في الصفحة", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] options = {
                "📺 بث للشاشة الآن (Cast to TV)",
                "🎬 تحميل الفيديو بأعلى جودة (Full HD MP4)",
                "🎵 تحميل الصوت فقط (Audio MP3)"
        };

        new AlertDialog.Builder(this)
                .setTitle(activeMediaTitle)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        castToTv(activeMediaUrl);
                    } else if (which == 1) {
                        downloadMedia(activeMediaUrl, false);
                    } else if (which == 2) {
                        downloadMedia(activeMediaUrl, true);
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
                public void onFailure(Call call, java.io.IOException e) {
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

    @Override
    public void onBackPressed() {
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
