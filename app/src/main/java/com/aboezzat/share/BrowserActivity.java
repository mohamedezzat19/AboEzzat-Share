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
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BrowserActivity extends Activity {

    private WebView webView;
    private Button btnSniff;
    private EditText etUrl;
    private LinearLayout homeShortcuts;
    private String currentActiveMediaUrl = "";
    private String currentActiveTitle = "";
    private final OkHttpClient client = new OkHttpClient();
    private String tvIp = "";

    // قائمة سيرفرات الإعلانات لحجبها تلقائياً
    private final List<String> adDomains = Arrays.asList(
            "doubleclick.net", "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "adservice.google.com", "googletagmanager.com", "adcolony.com", "criteo.com"
    );

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        tvIp = prefs.getString("tv_ip", "").trim();

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#F5F5F5"));

        // 1. شريط التحكم العلوي المتطور
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(10, 10, 10, 10);
        topBar.setBackgroundColor(Color.parseColor("#FFFFFF"));

        Button btnHome = new Button(this);
        btnHome.setText("🏠");
        btnHome.setTextSize(16);
        btnHome.setOnClickListener(v -> showHomePage());
        topBar.addView(btnHome, new LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnRefresh = new Button(this);
        btnRefresh.setText("🔄");
        btnRefresh.setTextSize(16);
        btnRefresh.setOnClickListener(v -> webView.reload());
        topBar.addView(btnRefresh, new LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT));

        etUrl = new EditText(this);
        etUrl.setHint("اكتب الرابط أو ابحث...");
        etUrl.setTextSize(14);
        etUrl.setSingleLine(true);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        etParams.setMargins(5, 0, 5, 0);
        topBar.addView(etUrl, etParams);

        Button btnGo = new Button(this);
        btnGo.setText("فتح");
        btnGo.setOnClickListener(v -> {
            String target = etUrl.getText().toString().trim();
            loadTargetUrl(target);
        });
        topBar.addView(btnGo, new LinearLayout.LayoutParams(140, ViewGroup.LayoutParams.WRAP_CONTENT));
        mainLayout.addView(topBar);

        // 2. اختصارات المواقع السريعة
        homeShortcuts = new LinearLayout(this);
        homeShortcuts.setOrientation(LinearLayout.HORIZONTAL);
        homeShortcuts.setPadding(10, 15, 10, 15);
        homeShortcuts.setBackgroundColor(Color.parseColor("#EAEAEA"));

        HorizontalScrollView scrollShortcuts = new HorizontalScrollView(this);
        scrollShortcuts.addView(homeShortcuts);

        addShortcut("تيك توك", "https://www.tiktok.com");
        addShortcut("إنستجرام", "https://www.instagram.com");
        addShortcut("فيسبوك", "https://www.facebook.com");
        addShortcut("يوتيوب", "https://m.youtube.com");
        addShortcut("ثريد", "https://www.threads.net");
        addShortcut("تويتر / X", "https://x.com");
        mainLayout.addView(scrollShortcuts);

        // 3. مساحة المتصفح والزر العائم
        FrameLayout webContainer = new FrameLayout(this);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // ضبط الشاشة لمقاس الموبايل بالضبط
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // هوية متصفح أندرويد حديث لمقاسات طبيعية
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        // حفظ الكوكيز وتسجيل الدخول بشكل دائم
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // جسر جافاسكريبت لالتقاط الفيديو الذي يتم تشغيله أمام عينك فقط
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void onVideoPlaying(String mediaUrl, String title) {
                if (mediaUrl != null && !mediaUrl.isEmpty() && mediaUrl.startsWith("http")) {
                    currentActiveMediaUrl = mediaUrl;
                    currentActiveTitle = (title != null && !title.isEmpty()) ? title : "الفيديو المعروض حالياً";
                    runOnUiThread(() -> {
                        btnSniff.setText("▶ بث الفيديو الحالي الشغال");
                        btnSniff.setVisibility(View.VISIBLE);
                    });
                }
            }
        }, "AboEzzatPlayer");

        webContainer.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // الزر العائم الأنيق (يظهر فقط مع الفيديو الشغال حالياً)
        btnSniff = new Button(this);
        btnSniff.setText("▶ بث الفيديو الحالي");
        btnSniff.setBackgroundColor(Color.parseColor("#00897B"));
        btnSniff.setTextColor(Color.WHITE);
        btnSniff.setPadding(30, 15, 30, 15);
        btnSniff.setVisibility(View.GONE);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        fabParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        fabParams.setMargins(0, 0, 0, 50);
        btnSniff.setLayoutParams(fabParams);
        btnSniff.setOnClickListener(v -> showActionDialog());
        webContainer.addView(btnSniff);

        mainLayout.addView(webContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(mainLayout);

        setupSnifferAndAdBlock();

        showHomePage();
    }

    private void addShortcut(String name, String url) {
        Button btn = new Button(this);
        btn.setText(name);
        btn.setTextSize(12);
        btn.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 0, 8, 0);
        btn.setLayoutParams(params);
        btn.setOnClickListener(v -> loadTargetUrl(url));
        homeShortcuts.addView(btn);
    }

    private void showHomePage() {
        btnSniff.setVisibility(View.GONE);
        currentActiveMediaUrl = "";
        String homeHtml = "<html dir='rtl'><body style='background:#121212;color:white;font-family:sans-serif;text-align:center;padding:50px 20px;'>" +
                "<h2 style='color:#00B0FF;'>Abo Ezzat Browser</h2>" +
                "<p style='color:#AAA;'>اختر من المواقع بالأعلى أو اكتب أي رابط للتصفح بدون إعلانات</p>" +
                "</body></html>";
        webView.loadDataWithBaseURL("https://www.google.com.eg", homeHtml, "text/html", "UTF-8", null);
        etUrl.setText("");
    }

    private void loadTargetUrl(String target) {
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            target = "https://www.google.com.eg/search?q=" + URLEncoder.encode(target);
        }
        etUrl.setText(target);
        btnSniff.setVisibility(View.GONE);
        currentActiveMediaUrl = "";

        // تثبيت لغة وتوجيه مصر عند طلب الموقع
        java.util.Map<String, String> extraHeaders = new java.util.HashMap<>();
        extraHeaders.put("Accept-Language", "ar-EG,ar;q=0.9,en-US;q=0.8");
        extraHeaders.put("X-Forwarded-For", "156.210.12.1"); // نطاق IP مصري لتثبيت الدولة

        webView.loadUrl(target, extraHeaders);
    }

    private void setupSnifferAndAdBlock() {
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String reqUrl = request.getUrl().toString().toLowerCase();

                // 1. حجب الإعلانات المزعجة
                for (String ad : adDomains) {
                    if (reqUrl.contains(ad)) {
                        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream("".getBytes()));
                    }
                }

                // 2. التقاط البثوث الحية المباشرة (Master Playlist أو FLV Live)
                if ((reqUrl.contains("pull-flv") || reqUrl.contains("pull-hls") || reqUrl.contains(".m3u8")) &&
                        !reqUrl.contains(".ts") && !reqUrl.contains("seg-") && !reqUrl.contains("bytestart")) {
                    currentActiveMediaUrl = request.getUrl().toString();
                    runOnUiThread(() -> {
                        btnSniff.setText("▶ بث مباشر جاهز للتشغيل");
                        btnSniff.setVisibility(View.VISIBLE);
                    });
                }

                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                etUrl.setText(url);

                // كود جافاسكريبت يربط كل وسوم الفيديو بحدث Play الفعلي فقط
                String js = "(function() { " +
                        "   function attachListeners() { " +
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
                        "   attachListeners(); " +
                        "   setInterval(attachListeners, 1500); " +
                        "})();";
                view.evaluateJavascript(js, null);
            }
        });
    }

    private void showActionDialog() {
        if (currentActiveMediaUrl.isEmpty()) return;

        String[] actions = {"بث للشاشة الآن (Cast)", "تحميل إلى الهاتف (Download)"};
        new AlertDialog.Builder(this)
                .setTitle(currentActiveTitle)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        castToTv(currentActiveMediaUrl);
                    } else {
                        downloadMedia(currentActiveMediaUrl);
                    }
                })
                .show();
    }

    private void castToTv(String mediaUrl) {
        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين عنوان IP الشاشة من القائمة الرئيسية أولاً", Toast.LENGTH_SHORT).show();
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
                    runOnUiThread(() -> Toast.makeText(BrowserActivity.this, "تعذر الإرسال: تأكد من الشاشة والـ IP", Toast.LENGTH_SHORT).show());
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

    private void downloadMedia(String mediaUrl) {
        try {
            if (mediaUrl.contains(".m3u8") || mediaUrl.contains("pull-flv")) {
                Toast.makeText(this, "البث المباشر الحي يبث للشاشة فقط ولا يتم تنزيله كملف ثابت", Toast.LENGTH_LONG).show();
                return;
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mediaUrl));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle("تحميل فيديو - Abo Ezzat");
            request.setDescription("جاري تنزيل الملف المباشر...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AboEzzat_" + System.currentTimeMillis() + ".mp4");

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "بدأ التحميل بنجاح في مجلد Downloads ✓", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "تعذر التنزيل المباشر", Toast.LENGTH_SHORT).show();
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
