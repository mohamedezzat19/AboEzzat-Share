package com.aboezzat.share;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShareActivity extends Activity {

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (sharedText != null) {
                String extractedUrl = extractUrl(sharedText);
                if (extractedUrl != null) {
                    processAndSend(extractedUrl);
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

    private void processAndSend(String url) {
        SharedPreferences prefs = getSharedPreferences("AboEzzatSharePrefs", MODE_PRIVATE);
        String tvIp = prefs.getString("tv_ip", "").trim();

        if (tvIp.isEmpty()) {
            Toast.makeText(this, "يرجى فتح التطبيق وتعيين عنوان الشاشة أولاً", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 1. يوتيوب يُرسل مباشرة
        if (url.contains("youtube.com") || url.contains("youtu.be")) {
            sendToTv(tvIp, url);
            return;
        }

        // 2. إذا كان الرابط بث مباشر لتيك توك (TikTok LIVE)
        if (url.contains("tiktok.com") && url.contains("/live")) {
            Toast.makeText(this, "جاري استخراج رابط البث المباشر...", Toast.LENGTH_SHORT).show();
            extractTikTokLive(url, tvIp);
            return;
        }

        // 3. فيديوهات تيك توك العادية
        if (url.contains("tiktok.com")) {
            Toast.makeText(this, "جاري استخراج فيديو تيك توك...", Toast.LENGTH_SHORT).show();
            extractTikTokVideo(url, tvIp);
            return;
        }

        // 4. البث المباشر أو الفيديوهات لفيسبوك، إنستجرام، وتويتر
        Toast.makeText(this, "جاري معالجة الرابط وإرساله...", Toast.LENGTH_SHORT).show();
        extractGeneralMedia(url, tvIp);
    }

    // استخراج تدفق البث المباشر (HLS / FLV)
    private void extractTikTokLive(String url, String tvIp) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                sendToTv(tvIp, url);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    String html = response.body().string();
                    
                    // البحث عن رابط البث بصيغة m3u8 أو flv داخل صفحة البث
                    Pattern streamPattern = Pattern.compile("(https?://[^\"'\\s\\\\]+?\\.(?:m3u8|flv)[^\"'\\s\\\\]*)");
                    Matcher matcher = streamPattern.matcher(html);
                    if (matcher.find()) {
                        String streamUrl = matcher.group(1).replace("\\u0026", "&");
                        sendToTv(tvIp, streamUrl);
                        return;
                    }

                    // البحث في كائن البث المباشر JSON الافتراضي لـ TikTok
                    Pattern jsonPattern = Pattern.compile("\"streamData\":\"(.*?)\"");
                    Matcher jsonMatcher = jsonPattern.matcher(html);
                    if (jsonMatcher.find()) {
                        String rawJson = jsonMatcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
                        JSONObject streamObj = new JSONObject(rawJson);
                        JSONObject data = streamObj.getJSONObject("data");
                        for (java.util.Iterator<String> it = data.keys(); it.hasNext(); ) {
                            String key = it.next();
                            JSONObject quality = data.getJSONObject(key).getJSONObject("main");
                            String flvUrl = quality.optString("flv");
                            String hlsUrl = quality.optString("hls");
                            if (!flvUrl.isEmpty()) {
                                sendToTv(tvIp, flvUrl);
                                return;
                            } else if (!hlsUrl.isEmpty()) {
                                sendToTv(tvIp, hlsUrl);
                                return;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                sendToTv(tvIp, url);
            }
        });
    }

    private void extractTikTokVideo(String url, String tvIp) {
        try {
            String apiUrl = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(url, "UTF-8");
            Request request = new Request.Builder().url(apiUrl).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    sendToTv(tvIp, url);
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
                                sendToTv(tvIp, play);
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                    sendToTv(tvIp, url);
                }
            });
        } catch (Exception e) {
            sendToTv(tvIp, url);
        }
    }

    private void extractGeneralMedia(String url, String tvIp) {
        try {
            String apiUrl = "https://api.vkrdown.com/api/get?url=" + URLEncoder.encode(url, "UTF-8");
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    sendToTv(tvIp, url);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = response.body().string();
                        JSONObject json = new JSONObject(body);
                        if (json.has("data")) {
                            JSONObject data = json.getJSONObject("data");
                            if (data.has("url")) {
                                sendToTv(tvIp, data.getString("url"));
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                    sendToTv(tvIp, url);
                }
            });
        } catch (Exception e) {
            sendToTv(tvIp, url);
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
                        Toast.makeText(ShareActivity.this, "تعذر الإرسال: تأكد من الشاشة والـ IP", Toast.LENGTH_SHORT).show();
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
}
