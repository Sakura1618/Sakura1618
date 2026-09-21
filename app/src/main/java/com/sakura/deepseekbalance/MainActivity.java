package com.sakura.deepseekbalance;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Window;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new ApiBridge(), "AndroidApi");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private final class ApiBridge {
        @JavascriptInterface
        public void query(String apiKey) {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                deliver("{\"error\":\"API Key 为空\"}");
                return;
            }
            final String key = apiKey.trim();
            new Thread(() -> {
                try {
                    Response balance = get("https://api.deepseek.com/user/balance", key);
                    Response models = get("https://api.deepseek.com/models", key);
                    String json = "{"
                            + "\"balanceStatus\":" + balance.status + ","
                            + "\"modelsStatus\":" + models.status + ","
                            + "\"balance\":" + asJson(balance.body) + ","
                            + "\"models\":" + asJson(models.body)
                            + "}";
                    deliver(json);
                } catch (Exception e) {
                    deliver("{\"error\":" + quote(e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage())) + "}");
                }
            }, "DeepSeekApiQuery").start();
        }
    }

    private static final class Response {
        final int status;
        final String body;
        Response(int status, String body) { this.status = status; this.body = body; }
    }

    private static Response get(String endpoint, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "DeepSeekApiDashboard-Android/1.1");
        int status = c.getResponseCode();
        InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
        }
        c.disconnect();
        return new Response(status, sb.toString());
    }

    private static String asJson(String s) {
        if (s == null || s.trim().isEmpty()) return "null";
        String t = s.trim();
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) return t;
        return quote(t);
    }

    private static String quote(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': out.append("\\\\"); break;
                case '\"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (ch < 32) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
            }
        }
        return out.append('\"').toString();
    }

    private void deliver(String json) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript("window.onNativeResult(" + quote(json) + ")", null);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidApi");
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
