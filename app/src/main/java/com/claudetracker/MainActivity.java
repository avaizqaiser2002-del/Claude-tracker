package com.claudetracker;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextView usageText;

    private static final String TRACKER_JS =
        "(function() {" +
        "  if (window._ct) return;" +
        "  window._ct = true;" +
        "  var LIMIT = 45000;" +
        "  var timer = null;" +
        "  var SELECTORS = [" +
        "    '[data-testid=\"human-turn-content\"]'," +
        "    '[data-testid=\"assistant-turn-content\"]'," +
        "    '.font-claude-message'," +
        "    '.whitespace-pre-wrap'" +
        "  ];" +
        "  function scan() {" +
        "    try {" +
        "      var text = '';" +
        "      for (var i = 0; i < SELECTORS.length; i++) {" +
        "        var els = document.querySelectorAll(SELECTORS[i]);" +
        "        if (els.length > 0) {" +
        "          els.forEach(function(e) { text += (e.innerText || '') + ' '; });" +
        "          break;" +
        "        }" +
        "      }" +
        "      var tokens = Math.round(text.length / 4);" +
        "      var pct = Math.min(99, Math.round(tokens * 100 / LIMIT));" +
        "      var humanTurns = document.querySelectorAll(" +
        "        '[data-testid=\"human-turn\"], [class*=\"human-turn\"]'" +
        "      );" +
        "      var msgCount = Math.max(1, humanTurns.length);" +
        "      var avgPerMsg = tokens / msgCount;" +
        "      var rem = avgPerMsg > 0" +
        "        ? Math.max(0, Math.round((LIMIT - tokens) / avgPerMsg))" +
        "        : 0;" +
        "      Android.updateUsage(pct, rem);" +
        "    } catch(e) {}" +
        "  }" +
        "  new MutationObserver(function() {" +
        "    clearTimeout(timer);" +
        "    timer = setTimeout(scan, 700);" +
        "  }).observe(document.body, {childList:true, subtree:true, characterData:true});" +
        "  var _fetch = window.fetch;" +
        "  window.fetch = function() {" +
        "    return _fetch.apply(this, arguments).then(function(r) {" +
        "      setTimeout(scan, 1500);" +
        "      return r;" +
        "    }).catch(function(e) { throw e; });" +
        "  };" +
        "  scan();" +
        "})();";

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 10; Mobile) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Mobile Safari/537.36"
        );

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new UsageBridge(), "Android");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                injectTracker();
                view.postDelayed(() -> injectTracker(), 8000);
            }
        });

        webView.loadUrl("https://claude.ai");

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout overlay = buildOverlay();
        FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        root.addView(overlay, overlayLp);

        overlay.setX(24f);
        overlay.setY(140f);

        final float[] dragOffset = new float[2];
        overlay.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragOffset[0] = event.getRawX() - v.getX();
                    dragOffset[1] = event.getRawY() - v.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    v.setX(event.getRawX() - dragOffset[0]);
                    v.setY(event.getRawY() - dragOffset[1]);
                    break;
            }
            return true;
        });

        setContentView(root);
    }

    private LinearLayout buildOverlay() {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(32, 20, 32, 20);
        pill.setAlpha(0.92f);
        pill.setElevation(16f);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(220, 18, 18, 28));
        bg.setCornerRadius(40f);
        bg.setStroke(2, Color.argb(80, 255, 255, 255));
        pill.setBackground(bg);

        usageText = new TextView(this);
        usageText.setText("⏳  Loading...");
        usageText.setTextColor(Color.WHITE);
        usageText.setTextSize(12f);
        usageText.setLineSpacing(6f, 1f);
        pill.addView(usageText);

        return pill;
    }

    private void injectTracker() {
        webView.evaluateJavascript(TRACKER_JS, null);
    }

    class UsageBridge {
        @JavascriptInterface
        public void updateUsage(final int pct, final int remaining) {
            runOnUiThread(() -> {
                String dot = pct < 50 ? "🟢" : pct < 75 ? "🟡" : "🔴";
                usageText.setText(dot + "  " + pct + "% used\n~" + remaining + " msgs left");
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
