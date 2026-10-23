package com.aboezzat.share;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    private static final int PICK_MEDIA_REQUEST = 101;
    private static final int PICK_SLIDESHOW_REQUEST = 102;

    private EditText etTvIp;
    private TextView tvStatusBadge;
    private SharedPreferences prefs;
    private final OkHttpClient client = new OkHttpClient();
    private LocalMediaServer localServer;
    private Uri selectedMediaUri = null;

    private final List<Uri> slideshowQueue = new ArrayList<>();
    private int currentSlideIndex = 0;
    private final Handler slideHandler = new Handler(Looper.getMainLooper());
    private final Runnable slideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!slideshowQueue.isEmpty()) {
                currentSlideIndex = (currentSlideIndex + 1) % slideshowQueue.size();
                selectedMediaUri = slideshowQueue.get(currentSlideIndex);
                castLocalMediaToTv(false);
                slideHandler.postDelayed(this, 5000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        startLocalServer();

        if (handleIncomingShareIntent(getIntent())) {
            return;
        }

        initViews();
    }

    private void initViews() {
        etTvIp = findViewById(R.id.et_ip);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
        Button btnSave = findViewById(R.id.btn_save);

        String savedIp = prefs.getString("tv_ip", "");
        etTvIp.setText(savedIp);
        if (!savedIp.isEmpty()) {
            tvStatusBadge.setText("● متصل: " + savedIp);
        }

        tvStatusBadge.setOnClickListener(v -> scanForTvIp());

        btnSave.setOnClickListener(v -> {
            String ip = etTvIp.getText().toString().trim();
            prefs.edit().putString("tv_ip", ip).apply();
            tvStatusBadge.setText("● تم الحفظ");
            Toast.makeText(MainActivity.this, "تم حفظ IP الشاشة ✓", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.card_browser).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, BrowserActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.card_local_media).setOnClickListener(v -> openMediaPicker(false));
        findViewById(R.id.card_slideshow).setOnClickListener(v -> openMediaPicker(true));

        // التحكم بالتشغيل والتقديم/التأخير
        findViewById(R.id.btn_play_pause).setOnClickListener(v -> sendRemoteCommand("toggle_play"));
        findViewById(R.id.btn_rewind).setOnClickListener(v -> sendRemoteCommand("rewind"));
        findViewById(R.id.btn_forward).setOnClickListener(v -> sendRemoteCommand("forward"));

        // أزرار التالي والسابق الجديدة
        findViewById(R.id.btn_next).setOnClickListener(v -> sendRemoteCommand("next"));
        findViewById(R.id.btn_prev).setOnClickListener(v -> sendRemoteCommand("prev"));

        // أزرار التحكم بالصوت
        findViewById(R.id.btn_vol_up).setOnClickListener(v -> sendRemoteCommand("vol_up"));
        findViewById(R.id.btn_vol_down).setOnClickListener(v -> sendRemoteCommand("vol_down"));
        findViewById(R.id.btn_mute).setOnClickListener(v -> sendRemoteCommand("mute"));
    }

    private void scanForTvIp() {
        tvStatusBadge.setText("🔍 جاري البحث...");
        Toast.makeText(this, "جاري فحص الشبكة لاكتشاف الشاشة...", Toast.LENGTH_SHORT).show();

        AsyncTask.execute(() -> {
            String phoneIp = getDeviceIpAddress();
            if (phoneIp.isEmpty() || !phoneIp.contains(".")) {
                runOnUiThread(() -> tvStatusBadge.setText("● تأكد من الواي فاي"));
                return;
            }

            String subnet = phoneIp.substring(0, phoneIp.lastIndexOf(".") + 1);
            String foundIp = null;

            for (int i = 1; i < 255; i++) {
                String testIp = subnet + i;
                try {
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(testIp, 8080), 35);
                    socket.close();
                    foundIp = testIp;
                    break;
                } catch (Exception ignored) {}
            }

            final String finalFound = foundIp;
            runOnUiThread(() -> {
                if (finalFound != null) {
                    etTvIp.setText(finalFound);
                    prefs.edit().putString("tv_ip", finalFound).apply();
                    tvStatusBadge.setText("● متصل: " + finalFound);
                    Toast.makeText(MainActivity.this, "تم العثور على الشاشة وحفظها: " + finalFound, Toast.LENGTH_LONG).show();
                } else {
                    tvStatusBadge.setText("● لم تكتشف");
                    Toast.makeText(MainActivity.this, "تعذر كشف الشاشة تلقائياً، تأكد أنها متصلة", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingShareIntent(intent);
    }

    private boolean handleIncomingShareIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action) && intent.getType() != null) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                Pattern pattern = Pattern.compile("https?://\\S+");
                Matcher matcher = pattern.matcher(sharedText);
                if (matcher.find()) {
                    processSharedLink(matcher.group(0));
                    return true;
                }
            }
        }
        return false;
    }

    private void processSharedLink(String url) {
        String lowerUrl = url.toLowerCase();

        // فيسبوك وإنستجرام فقط يحتاجان دخول المتصفح للاستخراج
        boolean isBrowserRequired = lowerUrl.contains("facebook.com") || lowerUrl.contains("fb.watch") || 
                                    lowerUrl.contains("fb.com") || lowerUrl.contains("instagram.com");

        if (isBrowserRequired) {
            Intent browserIntent = new Intent(this, BrowserActivity.class);
            browserIntent.setAction(Intent.ACTION_VIEW);
            browserIntent.setData(Uri.parse(url));
            startActivity(browserIntent);
            finish();
        } else {
            // ثريدز (threads.net)، يوتيوب، تيك توك، تويتر/X تذهب مباشرة للشاشة فوراً
            castUrlDirectlyToTv(url);
        }
    }

    private void castUrlDirectlyToTv(String mediaUrl) {
        String tvIp = prefs.getString("tv_ip", "").trim();
        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى تعيين عنوان الشاشة أولاً", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            String endpoint = tvIp;
            if (!endpoint.startsWith("http")) endpoint = "http://" + endpoint;
            if (!endpoint.contains(":8080")) endpoint += ":8080";

            String fullUrl = endpoint + "/?url=" + URLEncoder.encode(mediaUrl, "UTF-8");
            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "تعذر الإرسال للشاشة", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "تم الإرسال والتشغيل فوراً ✓", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        } catch (Exception ignored) {
            finish();
        }
    }

    private void openMediaPicker(boolean multipleImages) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        if (multipleImages) {
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, PICK_SLIDESHOW_REQUEST);
        } else {
            intent.setType("*/*");
            String[] mimeTypes = {"video/*", "image/*", "audio/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
            startActivityForResult(intent, PICK_MEDIA_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        slideHandler.removeCallbacks(slideRunnable);

        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_MEDIA_REQUEST && data.getData() != null) {
                selectedMediaUri = data.getData();
                takePermissions(selectedMediaUri);
                castLocalMediaToTv(true);
            } else if (requestCode == PICK_SLIDESHOW_REQUEST) {
                slideshowQueue.clear();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        Uri uri = clipData.getItemAt(i).getUri();
                        takePermissions(uri);
                        slideshowQueue.add(uri);
                    }
                } else if (data.getData() != null) {
                    takePermissions(data.getData());
                    slideshowQueue.add(data.getData());
                }

                if (!slideshowQueue.isEmpty()) {
                    currentSlideIndex = 0;
                    selectedMediaUri = slideshowQueue.get(0);
                    castLocalMediaToTv(true);
                    if (slideshowQueue.size() > 1) {
                        slideHandler.postDelayed(slideRunnable, 5000);
                        Toast.makeText(this, "بدأ العرض التقديمي للصور (" + slideshowQueue.size() + ")", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private void takePermissions(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
    }

    private String getDeviceIpAddress() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                return Formatter.formatIpAddress(ip);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void castLocalMediaToTv(boolean showToast) {
        String tvIp = prefs.getString("tv_ip", "").trim();
        if (tvIp.isEmpty()) {
            if (showToast) Toast.makeText(this, "يرجى كتابة وحفظ IP الشاشة أولاً", Toast.LENGTH_SHORT).show();
            return;
        }

        String phoneIp = getDeviceIpAddress();
        if (phoneIp.isEmpty() || phoneIp.equals("0.0.0.0")) {
            if (showToast) Toast.makeText(this, "تأكد من اتصال الهاتف بالواي فاي", Toast.LENGTH_SHORT).show();
            return;
        }

        String mime = getContentResolver().getType(selectedMediaUri);
        String targetUrl = (mime != null && mime.startsWith("image/")) ?
                "http://" + phoneIp + ":8088/media.jpg" : "http://" + phoneIp + ":8088/media.mp4";

        try {
            String endpoint = tvIp;
            if (!endpoint.startsWith("http")) endpoint = "http://" + endpoint;
            if (!endpoint.contains(":8080")) endpoint += ":8080";

            String fullUrl = endpoint + "/?url=" + URLEncoder.encode(targetUrl, "UTF-8");
            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if (showToast) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "فشل الإرسال للشاشة", Toast.LENGTH_SHORT).show());
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (showToast) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "جاري العرض على الشاشة ✓", Toast.LENGTH_SHORT).show());
                    }
                }
            });
        } catch (Exception ignored) {}
    }

    private void sendRemoteCommand(String cmd) {
        String tvIp = prefs.getString("tv_ip", "").trim();
        if (tvIp.isEmpty()) return;

        try {
            String endpoint = tvIp;
            if (!endpoint.startsWith("http")) endpoint = "http://" + endpoint;
            if (!endpoint.contains(":8080")) endpoint += ":8080";

            String fullUrl = endpoint + "/?command=" + cmd;
            Request req = new Request.Builder().url(fullUrl).build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {}
                @Override
                public void onResponse(Call call, Response response) {}
            });
        } catch (Exception ignored) {}
    }

    private void startLocalServer() {
        try {
            if (localServer == null) {
                localServer = new LocalMediaServer(8088);
                localServer.start();
            }
        } catch (Exception ignored) {}
    }

    private class LocalMediaServer extends NanoHTTPD {
        public LocalMediaServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            if (selectedMediaUri != null) {
                try {
                    AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(selectedMediaUri, "r");
                    if (afd != null) {
                        long fileLength = afd.getLength();
                        String mime = getContentResolver().getType(selectedMediaUri);
                        if (mime == null) mime = "application/octet-stream";

                        Map<String, String> headers = session.getHeaders();
                        String range = headers.get("range");

                        if (range != null && range.startsWith("bytes=")) {
                            String[] parts = range.substring("bytes=".length()).split("-");
                            long start = Long.parseLong(parts[0]);
                            long end = (parts.length > 1 && !parts[1].isEmpty()) ? Long.parseLong(parts[1]) : fileLength - 1;
                            if (end >= fileLength) end = fileLength - 1;
                            long contentLength = end - start + 1;

                            FileInputStream fis = afd.createInputStream();
                            fis.skip(start);

                            Response res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, fis, contentLength);
                            res.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
                            res.addHeader("Content-Length", String.valueOf(contentLength));
                            res.addHeader("Accept-Ranges", "bytes");
                            res.addHeader("Access-Control-Allow-Origin", "*");
                            return res;
                        } else {
                            FileInputStream fis = afd.createInputStream();
                            Response res = newFixedLengthResponse(Response.Status.OK, mime, fis, fileLength);
                            res.addHeader("Content-Length", String.valueOf(fileLength));
                            res.addHeader("Accept-Ranges", "bytes");
                            res.addHeader("Access-Control-Allow-Origin", "*");
                            return res;
                        }
                    }
                } catch (Exception e) {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error loading file");
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No media selected");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        slideHandler.removeCallbacks(slideRunnable);
        if (localServer != null) {
            localServer.stop();
        }
    }
}
