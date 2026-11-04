package com.iosaikeyboard.ai;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.json.JSONObject;

public class IOSAiKeyboardService extends InputMethodService {

    private WebView webView;
    private SharedPreferences prefs;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sharedPreferences, key) -> pushLivePrefsToWebView();

    private final BroadcastReceiver settingsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pushLivePrefsToWebView();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("KeyboardPrefs", MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        try {
            IntentFilter filter = new IntentFilter("com.iosaikeyboard.ai.SETTINGS_CHANGED");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(settingsReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        try { unregisterReceiver(settingsReceiver); } catch (Exception ignored) {}
    }

    // BUG FIX #10: Report exact top Y-coordinate of the keyboard so Android's adjustResize shrinks the app above it and never clips sliders
    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (webView != null && webView.getHeight() > 0 && getWindow() != null && getWindow().getWindow() != null) {
            View decor = getWindow().getWindow().getDecorView();
            int decorHeight = decor.getHeight();
            int kbHeight = webView.getHeight();
            int topInset = Math.max(0, decorHeight - kbHeight);
            outInsets.contentTopInsets = topInset;
            outInsets.visibleTopInsets = topInset;
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_CONTENT;
        }
    }

    // BUG FIX #2: Match the Android bottom system navigation bar color to the active Keyboard Theme (Light=#D1D4DB, Dark=#1C1D24, Nothing=#000000)
    private void applyImeSystemBarColors(String theme) {
        if (webView == null) return;
        webView.post(() -> {
            try {
                Dialog dialog = getWindow();
                if (dialog == null || dialog.getWindow() == null) return;
                Window window = dialog.getWindow();
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

                boolean isLight = "light".equalsIgnoreCase(theme);
                int navColor;
                if (isLight) {
                    navColor = Color.parseColor("#D1D4DB");
                } else if ("nothing".equalsIgnoreCase(theme)) {
                    navColor = Color.parseColor("#000000");
                } else {
                    navColor = Color.parseColor("#1C1D24");
                }
                window.setNavigationBarColor(navColor);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.setNavigationBarContrastEnforced(false);
                }

                View decor = window.getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isLight) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    } else {
                        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                }
                decor.setSystemUiVisibility(flags);

                // Strip any default black background from parent IME frames
                ViewParent parent = webView.getParent();
                while (parent instanceof View) {
                    ((View) parent).setBackgroundColor(Color.TRANSPARENT);
                    parent = parent.getParent();
                }
            } catch (Exception ignored) {}
        });
    }

    private void pushLivePrefsToWebView() {
        if (webView != null) {
            applyImeSystemBarColors(prefs.getString("theme", "dark"));
            webView.post(() -> webView.evaluateJavascript("if(window.syncNativePrefs) syncNativePrefs();", null));
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    public View onCreateInputView() {
        if (prefs == null) prefs = getSharedPreferences("KeyboardPrefs", MODE_PRIVATE);

        // One-time migration to reset any overly rounded 12px key radius back to crisp 5px iOS standard
        if (!prefs.getBoolean("ios_v3_radius_migrated", false)) {
            prefs.edit().putInt("radius", 5).putBoolean("ios_v3_radius_migrated", true).apply();
        }

        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(Color.TRANSPARENT);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        int savedHeight = prefs.getInt("height", 216);
        boolean numRow = prefs.getBoolean("numberRow", false);
        int initialTotalDp = savedHeight + (numRow ? 38 : 0) + 74;

        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(initialTotalDp)
        ));

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidIME");

        webView.loadDataWithBaseURL("https://ioskeyboard.local/", getKeyboardHtml(), "text/html", "UTF-8", null);

        container.addView(webView);
        applyImeSystemBarColors(prefs.getString("theme", "dark"));
        return container;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        applyImeSystemBarColors(prefs.getString("theme", "dark"));
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript("if(window.onFieldFocused) onFieldFocused();", null));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void resizeInputView(final int totalCssPx) {
            if (webView == null) return;
            webView.post(() -> {
                int targetPx = dpToPx(Math.max(210, Math.min(430, totalCssPx)));
                ViewGroup.LayoutParams lp = webView.getLayoutParams();
                if (lp != null && lp.height != targetPx) {
                    lp.height = targetPx;
                    webView.setLayoutParams(lp);
                    webView.requestLayout();
                }
            });
        }

        @JavascriptInterface
        public void updateImeThemeBar(final String effectiveTheme) {
            applyImeSystemBarColors(effectiveTheme);
        }

        @JavascriptInterface
        public String getPrefsJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("theme", prefs.getString("theme", "dark"));
                obj.put("height", prefs.getInt("height", 216));
                obj.put("width", prefs.getInt("width", 100));
                obj.put("scale", prefs.getInt("scale", 100));
                obj.put("numScale", prefs.getInt("numScale", 85));
                obj.put("radius", prefs.getInt("radius", 5));
                obj.put("gap", prefs.getInt("gap", 6));
                obj.put("glass", prefs.getInt("glass", 0));
                obj.put("vibeStrength", prefs.getInt("vibeStrength", 10));
                obj.put("numberRow", prefs.getBoolean("numberRow", false));
                obj.put("swipeType", prefs.getBoolean("swipeType", true));
                obj.put("soundEffects", prefs.getBoolean("soundEffects", true));
                obj.put("haptics", prefs.getBoolean("haptics", true));
                obj.put("spaceTrackpad", prefs.getBoolean("spaceTrackpad", true));
                obj.put("keyPopups", prefs.getBoolean("keyPopups", true));
                obj.put("autoCap", prefs.getBoolean("autoCap", true));
                obj.put("doubleSpacePeriod", prefs.getBoolean("doubleSpacePeriod", true));
                obj.put("aiLearn", prefs.getBoolean("aiLearn", true));
                obj.put("oneHanded", prefs.getString("oneHanded", "off"));
                obj.put("apiKey", prefs.getString("apiKey", ""));
                obj.put("learnedData", prefs.getString("learnedData", "{}"));
                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void savePrefString(String key, String val) {
            prefs.edit().putString(key, val).apply();
            if ("theme".equals(key)) applyImeSystemBarColors(val);
        }

        @JavascriptInterface
        public void savePrefBool(String key, boolean val) {
            prefs.edit().putBoolean(key, val).apply();
        }

        @JavascriptInterface
        public void savePrefInt(String key, int val) {
            prefs.edit().putInt(key, val).apply();
        }

        @JavascriptInterface
        public void saveLearnedData(String json) {
            prefs.edit().putString("learnedData", json).apply();
        }

        @JavascriptInterface
        public void openAppSettings() {
            try {
                Intent i = new Intent(mContext, SettingsActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(i);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void commitText(String text) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(text, 1);
        }

        @JavascriptInterface
        public void replaceCurrentWord(String replacement) {
            InputConnection ic = getCurrentInputConnection();
            if (ic == null) return;
            CharSequence before = ic.getTextBeforeCursor(60, 0);
            if (before != null) {
                String str = before.toString();
                int i = str.length() - 1;
                while (i >= 0 && !Character.isWhitespace(str.charAt(i))) {
                    i--;
                }
                int deleteLen = str.length() - 1 - i;
                if (deleteLen > 0) {
                    ic.deleteSurroundingText(deleteLen, 0);
                }
            }
            ic.commitText(replacement, 1);
        }

        @JavascriptInterface
        public String getBeforeCursor(int n) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence s = ic.getTextBeforeCursor(n, 0);
                return s != null ? s.toString() : "";
            }
            return "";
        }

        @JavascriptInterface
        public void deleteText() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                CharSequence selected = ic.getSelectedText(0);
                if (selected != null && selected.length() > 0) {
                    ic.commitText("", 1);
                } else {
                    ic.deleteSurroundingText(1, 0);
                }
            }
        }

        @JavascriptInterface
        public void sendEnter() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            }
        }

        @JavascriptInterface
        public void moveCursor(int offset) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
                if (et != null) {
                    int newPos = Math.max(0, et.selectionStart + offset);
                    ic.setSelection(newPos, newPos);
                }
            }
        }

        @JavascriptInterface
        public String getFieldText() {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
                if (et != null && et.text != null) return et.text.toString();
                CharSequence before = ic.getTextBeforeCursor(500, 0);
                CharSequence after = ic.getTextAfterCursor(500, 0);
                return (before != null ? before.toString() : "") + (after != null ? after.toString() : "");
            }
            return "";
        }

        @JavascriptInterface
        public void replaceFieldText(String newText) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
                if (et != null && et.text != null) {
                    ic.setSelection(0, et.text.length());
                    ic.commitText(newText, 1);
                } else {
                    ic.deleteSurroundingText(1000, 1000);
                    ic.commitText(newText, 1);
                }
            }
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            if (!prefs.getBoolean("haptics", true)) return;
            int strength = prefs.getInt("vibeStrength", ms);
            try {
                Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(Math.max(1, strength), VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(Math.max(1, strength));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private String getKeyboardHtml() {
        return "<!DOCTYPE html>\n" +
"<html lang='en' class='dark'>\n" +
"<head>\n" +
"<meta charset='UTF-8' />\n" +
"<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />\n" +
"<style>\n" +
":root {\n" +
"  /* BUG FIX #3 & #4: Authentic 5px iOS keycap radius & tight vertical row rhythm */\n" +
"  --kb-height: 216px; --kb-width: 100%; --key-scale: 1; --num-row-scale: 0.85;\n" +
"  --key-gap-x: 6px; --key-gap-y: 8px; --key-radius: 5px;\n" +
"  --kb-deck-bg: rgba(28, 29, 36, 0.99); --kb-deck-border: rgba(255, 255, 255, 0.11);\n" +
"  --key-bg: rgba(74, 76, 89, 0.98); --key-bg-active: rgba(108, 110, 126, 0.99);\n" +
"  --key-mod-bg: rgba(54, 56, 68, 0.98); --key-mod-active: rgba(92, 94, 110, 0.99);\n" +
"  --key-shadow: rgba(8, 8, 12, 0.88); --key-text: #ffffff; --toolbar-text: #f2f2f7;\n" +
"  --chin-icon-color: #d1d5db;\n" +
"  --toolbar-divider: rgba(255, 255, 255, 0.24); --popup-bg: #5a5c6d; --popup-border: rgba(255, 255, 255, 0.18);\n" +
"  --accent-color: #007AFF; --glass-blur: 0px;\n" +
"}\n" +
"html.light {\n" +
"  --kb-deck-bg: rgba(209, 212, 219, 0.99); --kb-deck-border: rgba(0, 0, 0, 0.1);\n" +
"  --key-bg: rgba(255, 255, 255, 0.99); --key-bg-active: rgba(225, 228, 235, 0.99);\n" +
"  --key-mod-bg: rgba(171, 177, 189, 0.99); --key-mod-active: rgba(255, 255, 255, 0.99);\n" +
"  --key-shadow: rgba(132, 136, 144, 0.92); --key-text: #000000; --toolbar-text: #1c1c1e;\n" +
"  --chin-icon-color: #4A4D57;\n" +
"  /* BUG FIX #7: High-contrast visible 1px dividers in Light Mode */\n" +
"  --toolbar-divider: rgba(0, 0, 0, 0.24); --popup-bg: #ffffff; --popup-border: rgba(0, 0, 0, 0.14);\n" +
"}\n" +
"html.nothing {\n" +
"  --kb-deck-bg: rgba(0, 0, 0, 0.99); --kb-deck-border: rgba(255, 255, 255, 0.28);\n" +
"  --key-bg: rgba(12, 12, 12, 0.98); --key-bg-active: #ffffff;\n" +
"  --key-mod-bg: rgba(24, 24, 24, 0.98); --key-mod-active: #ffffff;\n" +
"  --key-shadow: transparent; --key-text: #ffffff; --toolbar-text: #ffffff;\n" +
"  --chin-icon-color: #ffffff;\n" +
"  --toolbar-divider: rgba(255, 255, 255, 0.28); --popup-bg: #000000; --popup-border: rgba(255, 255, 255, 0.4);\n" +
"  --accent-color: #ea1d25;\n" +
"}\n" +
"html.nothing .kb-key { font-family: monospace; border: 1px solid rgba(255,255,255,0.22); }\n" +
"html.nothing .kb-key.pressed { color: #000000 !important; }\n" +
"* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }\n" +
"html, body {\n" +
"  margin: 0; padding: 0;\n" +
"  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', sans-serif;\n" +
"  background-color: var(--kb-deck-bg);\n" +
"  color: var(--key-text); overflow: hidden; width: 100vw; height: 100%;\n" +
"  display: flex; flex-direction: column; justify-content: flex-end;\n" +
"}\n" +
"/* BUG FIX #1: Flush 0px top edge eliminates black triangular corner wedges */\n" +
".kb-deck {\n" +
"  width: var(--kb-width); margin: 0 auto;\n" +
"  background-color: var(--kb-deck-bg);\n" +
"  backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur));\n" +
"  border-top: 0.5px solid var(--kb-deck-border);\n" +
"  border-top-left-radius: 0px; border-top-right-radius: 0px;\n" +
"  padding: 4px 4px 2px 4px; position: relative; touch-action: none;\n" +
"  transition: width 0.22s cubic-bezier(0.32, 0.72, 0, 1), background-color 0.2s ease;\n" +
"}\n" +
".kb-deck.hand-left { margin-left: 0; margin-right: auto; }\n" +
".kb-deck.hand-right { margin-left: auto; margin-right: 0; }\n" +
".toolbar { width: 100%; height: 36px; display: flex; align-items: center; justify-content: space-between; padding: 0 6px; margin-bottom: 3px; }\n" +
".tool-btn { width: 34px; height: 32px; border-radius: 99px; border: none; background: transparent; color: var(--toolbar-text); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: transform 0.1s ease; }\n" +
".tool-btn:active { transform: scale(0.86); }\n" +
".predictions { flex: 1; display: flex; align-items: center; justify-content: space-around; height: 100%; padding: 0 4px; }\n" +
".sug-btn { flex: 1; height: 28px; border: none; background: transparent; color: var(--toolbar-text); font-size: 15px; border-radius: 7px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; transition: transform 0.1s ease, background-color 0.12s ease; }\n" +
".sug-btn:active { transform: scale(0.95); background: rgba(128,128,128,0.18); }\n" +
"/* BUG FIX #7: 20px tall crisp vertical dividers */\n" +
".divider { width: 1px; height: 20px; background: var(--toolbar-divider); }\n" +
".kb-stage { position: relative; width: 100%; height: var(--kb-height); transition: height 0.2s cubic-bezier(0.32, 0.72, 0, 1); }\n" +
".kb-rows { width: 100%; height: 100%; display: flex; flex-direction: column; justify-content: space-between; gap: var(--key-gap-y); padding: 2px 2px; transition: opacity 0.16s ease; }\n" +
".kb-row { display: flex; justify-content: center; align-items: stretch; flex: 1; gap: var(--key-gap-x); width: 100%; }\n" +
".kb-row.num-row { flex: calc(0.82 * var(--num-row-scale)); }\n" +
".kb-key {\n" +
"  position: relative; flex: 1; background-color: var(--key-bg); color: var(--key-text);\n" +
"  border-radius: calc(var(--key-radius) * var(--key-scale));\n" +
"  box-shadow: 0 1.2px 0 var(--key-shadow);\n" +
"  display: flex; align-items: center; justify-content: center;\n" +
"  font-size: calc(21.5px * var(--key-scale)); font-weight: 400; cursor: pointer;\n" +
"  transform: scale(var(--key-scale));\n" +
"  transition: transform 0.045s cubic-bezier(0.2,0.9,0.3,1.15), background-color 0.05s ease, border-radius 0.16s ease;\n" +
"}\n" +
".kb-key.letter-key { padding-bottom: 2px; }\n" +
"/* BUG FIX #3: Cap number row radius so shorter keys never look like oval capsules */\n" +
".kb-row.num-row .kb-key { font-size: calc(17px * var(--key-scale) * var(--num-row-scale)); padding-bottom: 0; border-radius: min(var(--key-radius), 4.6px); }\n" +
".kb-key.mod-key { background-color: var(--key-mod-bg); font-size: calc(15.5px * var(--key-scale)); font-weight: 500; }\n" +
"/* BUG FIX #9: Classic iOS 'space' label inside spacebar */\n" +
".kb-key.space-key { font-size: calc(15.5px * var(--key-scale)); font-weight: 400; letter-spacing: -0.1px; color: var(--key-text); }\n" +
".kb-key.pressed { background-color: var(--key-bg-active); transform: scale(calc(var(--key-scale) * 0.92)); }\n" +
".kb-key.shift-active { background-color: #ffffff !important; color: #000000 !important; }\n" +
".key-popup {\n" +
"  position: absolute; bottom: calc(100% + 6px); left: 50%;\n" +
"  transform: translateX(-50%) scale(0.75) translateY(8px);\n" +
"  width: 48px; height: 56px; background-color: var(--popup-bg); color: var(--key-text);\n" +
"  border: 1px solid var(--popup-border); border-radius: 9px;\n" +
"  box-shadow: 0 8px 20px rgba(0,0,0,0.38); display: flex; align-items: center; justify-content: center;\n" +
"  font-size: 28px; opacity: 0; pointer-events: none;\n" +
"  transition: opacity 0.04s ease, transform 0.065s cubic-bezier(0.2,0.9,0.3,1.15); z-index: 60;\n" +
"}\n" +
".kb-key.show-popup .key-popup { opacity: 1; transform: translateX(-50%) scale(1) translateY(0); }\n" +
"/* BUG FIX #8: 32px chin with 26px iOS slate-grey Emoji icon aligned beneath the 123 key */\n" +
".kb-chin { height: 32px; display: flex; align-items: center; justify-content: space-between; padding: 2px 16px 0 16px; }\n" +
".chin-emoji-btn { width: 30px; height: 30px; border: none; background: transparent; color: var(--chin-icon-color); display: flex; align-items: center; justify-content: center; border-radius: 50%; transition: transform 0.1s ease; }\n" +
".chin-emoji-btn:active { transform: scale(0.84); background: rgba(128,128,128,0.16); }\n" +
"#glideCanvas { position: absolute; top: 0; left: 0; z-index: 25; pointer-events: none; display: none; }\n" +
".overlay-drawer {\n" +
"  position: absolute; inset: 0; background-color: var(--kb-deck-bg);\n" +
"  backdrop-filter: blur(22px); -webkit-backdrop-filter: blur(22px);\n" +
"  border-radius: 10px; z-index: 50; display: flex; flex-direction: column;\n" +
"  padding: 7px 9px; gap: 5px; overflow-y: auto;\n" +
"  opacity: 0; transform: translateY(10px) scale(0.97); pointer-events: none;\n" +
"  transition: opacity 0.16s cubic-bezier(0.32,0.72,0,1), transform 0.2s cubic-bezier(0.32,0.72,0,1);\n" +
"}\n" +
".overlay-drawer.open { opacity: 1; transform: translateY(0) scale(1); pointer-events: auto; }\n" +
".ai-grid { display: grid; grid-template-columns: 1.1fr 1.6fr 1.4fr; grid-template-rows: 1fr 1fr; gap: 6px; flex: 1; }\n" +
".ai-card { border: 1px solid var(--toolbar-divider); background: rgba(128,128,128,0.1); color: var(--toolbar-text); border-radius: 11px; font-size: 12px; font-weight: 500; display: flex; align-items: center; justify-content: center; gap: 5px; padding: 6px; transition: transform 0.1s ease; }\n" +
".ai-card:active { transform: scale(0.95); }\n" +
".tone-bar { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 2px; }\n" +
".tone-pill { border: 1px solid var(--toolbar-divider); background: rgba(0,122,255,0.16); color: var(--toolbar-text); border-radius: 99px; padding: 4px 11px; font-size: 11.5px; white-space: nowrap; }\n" +
".ios-mini-row { display: flex; justify-content: space-between; align-items: center; padding: 5px 4px; border-bottom: 0.5px solid var(--toolbar-divider); font-size: 12.5px; color: var(--toolbar-text); }\n" +
".ios-seg { display: flex; background: rgba(120,120,128,0.22); padding: 2px; border-radius: 8px; gap: 2px; }\n" +
".seg-btn { border: none; background: transparent; color: var(--toolbar-text); padding: 4px 9px; border-radius: 6px; font-size: 11px; font-weight: 600; transition: all 0.16s ease; }\n" +
".seg-btn.active { background: var(--accent-color); color: #fff; }\n" +
".ios-mini-switch { position: relative; width: 44px; height: 26px; border-radius: 26px; background: rgba(120,120,128,0.34); transition: background 0.22s ease; cursor: pointer; }\n" +
".ios-mini-switch.active { background: #34C759; }\n" +
".ios-mini-thumb { position: absolute; top: 2px; left: 2px; width: 22px; height: 22px; border-radius: 50%; background: #fff; box-shadow: 0 2px 5px rgba(0,0,0,0.25); transition: transform 0.22s cubic-bezier(0.32,0.72,0,1); }\n" +
".ios-mini-switch.active .ios-mini-thumb { transform: translateX(18px); }\n" +
".ios-mini-slider { -webkit-appearance: none; appearance: none; width: 120px; height: 4px; border-radius: 99px; background: rgba(120,120,128,0.34); outline: none; }\n" +
".ios-mini-slider::-webkit-slider-thumb { -webkit-appearance: none; appearance: none; width: 20px; height: 20px; border-radius: 50%; background: #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.35); }\n" +
".emoji-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 4px; overflow-y: auto; flex: 1; font-size: 22px; text-align: center; align-items: center; }\n" +
".cat-btn { border: none; background: transparent; font-size: 15px; padding: 3px 7px; border-radius: 99px; opacity: 0.6; }\n" +
".cat-btn.active { background: rgba(128,128,128,0.24); opacity: 1; }\n" +
".toast-pill { position: absolute; left: 50%; top: 4px; transform: translateX(-50%); background: var(--accent-color); color: #fff; padding: 3px 12px; border-radius: 99px; font-size: 11px; font-weight: 600; opacity: 0; pointer-events: none; transition: opacity 0.15s; z-index: 90; }\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"<section id='kbDeck' class='kb-deck'>\n" +
"  <div id='toastBanner' class='toast-pill'>Ready</div>\n" +
"  <div class='toolbar'>\n" +
"    <button id='settingsBtn' class='tool-btn'>\n" +
"      <svg width='19' height='19' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'><circle cx='12' cy='12' r='9'/><line x1='8' y1='12' x2='16' y2='12'/></svg>\n" +
"    </button>\n" +
"    <div id='predictionsContainer' class='predictions'>\n" +
"      <button class='sug-btn' data-w='I'>I</button>\n" +
"      <div class='divider'></div>\n" +
"      <button class='sug-btn' data-w='The' style='font-weight:500;'>The</button>\n" +
"      <div class='divider'></div>\n" +
"      <button class='sug-btn' data-w='You'>You</button>\n" +
"    </div>\n" +
"    <button id='aiBtn' class='tool-btn'>\n" +
"      <svg width='22' height='20' viewBox='0 0 28 24' fill='none' stroke='currentColor' stroke-linecap='round' stroke-linejoin='round'><line x1='2' y1='8' x2='7.5' y2='8' stroke-width='2'/><line x1='4' y1='12' x2='7.5' y2='12' stroke-width='2'/><line x1='2.5' y1='16' x2='7.5' y2='16' stroke-width='2'/><path d='M11.5 18.5L16.5 5.5L21.5 18.5' stroke-width='2.1'/><line x1='13.2' y1='14.2' x2='19.8' y2='14.2' stroke-width='2'/></svg>\n" +
"    </button>\n" +
"  </div>\n" +
"  <div id='kbStage' class='kb-stage'>\n" +
"    <div id='kbRowsContainer' class='kb-rows'>\n" +
"      <canvas id='glideCanvas'></canvas>\n" +
"    </div>\n" +
"    <div id='aiOverlay' class='overlay-drawer'>\n" +
"      <div style='display:flex; justify-content:space-between; align-items:center;'>\n" +
"        <span style='font-size:12px; font-weight:600; color:var(--toolbar-text);'>✨ Gemini AI Writing Tools</span>\n" +
"        <button id='closeAiBtn' class='seg-btn active'>ABC</button>\n" +
"      </div>\n" +
"      <div class='ai-grid'>\n" +
"        <button data-ai='ASK' class='ai-card' style='grid-row: span 2; flex-direction:column;'>✨<span>Ask AI</span></button>\n" +
"        <button data-ai='FIX' class='ai-card'>✓ Fix Grammar</button>\n" +
"        <button data-ai='SHORT' class='ai-card'>➖ Shorter</button>\n" +
"        <button data-ai='IMPROVE' class='ai-card'>✨ Improve</button>\n" +
"        <button data-ai='LONG' class='ai-card'>➕ Longer</button>\n" +
"      </div>\n" +
"      <div class='tone-bar'>\n" +
"        <button data-ai='PRO' class='tone-pill'>💼 Professional</button>\n" +
"        <button data-ai='CASUAL' class='tone-pill'>👋 Casual</button>\n" +
"        <button data-ai='SARCASTIC' class='tone-pill'>😏 Sarcastic</button>\n" +
"        <button data-ai='POETIC' class='tone-pill'>📜 Poetic</button>\n" +
"      </div>\n" +
"    </div>\n" +
"    <div id='settingsOverlay' class='overlay-drawer'>\n" +
"      <div style='display:flex; justify-content:space-between; align-items:center;'>\n" +
"        <span style='font-size:13px; font-weight:700; color:var(--toolbar-text);'>iOS Keyboard Settings</span>\n" +
"        <div style='display:flex; gap:5px;'>\n" +
"          <button id='fullAppBtn' class='seg-btn' style='background:rgba(120,120,128,0.25);'>⚙ All Settings</button>\n" +
"          <button id='closeSettingsBtn' class='seg-btn active'>Done</button>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div class='ios-mini-row'>\n" +
"        <span>Theme</span>\n" +
"        <div class='ios-seg'>\n" +
"          <button data-theme='dark' class='seg-btn active'>🌙 Dark</button>\n" +
"          <button data-theme='light' class='seg-btn'>☀️ Light</button>\n" +
"          <button data-theme='nothing' class='seg-btn'>🔴 Nothing</button>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div class='ios-mini-row'>\n" +
"        <span>Key Scale (<span id='scaleValLabel' style='color:#007AFF;font-weight:600;'>100%</span>)</span>\n" +
"        <input type='range' id='scaleRange' min='85' max='110' value='100' class='ios-mini-slider' />\n" +
"      </div>\n" +
"      <div class='ios-mini-row'>\n" +
"        <span>Liquid Glass (<span id='glassValLabel' style='color:#007AFF;font-weight:600;'>0%</span>)</span>\n" +
"        <input type='range' id='glassRange' min='0' max='100' value='0' class='ios-mini-slider' />\n" +
"      </div>\n" +
"      <div class='ios-mini-row'>\n" +
"        <span>Dedicated Number Row (1-0)</span>\n" +
"        <div id='numRowSwitch' class='ios-mini-switch'><div class='ios-mini-thumb'></div></div>\n" +
"      </div>\n" +
"      <div class='ios-mini-row'>\n" +
"        <span>Slide to Type (QuickPath)</span>\n" +
"        <div id='swipeSwitch' class='ios-mini-switch active'><div class='ios-mini-thumb'></div></div>\n" +
"      </div>\n" +
"      <div class='ios-mini-row'>\n" +
"        <span>Key Click Sounds</span>\n" +
"        <div id='soundSwitch' class='ios-mini-switch active'><div class='ios-mini-thumb'></div></div>\n" +
"      </div>\n" +
"    </div>\n" +
"    <div id='emojiOverlay' class='overlay-drawer'>\n" +
"      <div style='display:flex; justify-content:space-between; align-items:center;'>\n" +
"        <div style='display:flex; gap:3px; overflow-x:auto;'>\n" +
"          <button class='cat-btn active' data-cat='smileys'>😀</button>\n" +
"          <button class='cat-btn' data-cat='gestures'>👋</button>\n" +
"          <button class='cat-btn' data-cat='hearts'>❤️</button>\n" +
"          <button class='cat-btn' data-cat='animals'>🐶</button>\n" +
"          <button class='cat-btn' data-cat='food'>🍕</button>\n" +
"          <button class='cat-btn' data-cat='objects'>💡</button>\n" +
"        </div>\n" +
"        <div style='display:flex; gap:4px;'>\n" +
"          <button id='emojiBsBtn' class='seg-btn' style='background:rgba(120,120,128,0.25);'>⌫</button>\n" +
"          <button id='closeEmojiBtn' class='seg-btn active'>ABC</button>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div id='emojiGrid' class='emoji-grid'></div>\n" +
"    </div>\n" +
"  </div>\n" +
"  <!-- BUG FIX #8: 26px iOS Emoji Icon aligned cleanly beneath the 123 key -->\n" +
"  <div class='kb-chin'>\n" +
"    <button id='chinEmojiBtn' class='chin-emoji-btn'>\n" +
"      <svg width='26' height='26' viewBox='0 0 24 24' fill='currentColor'><path d='M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm-3.5 7a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm-3.5 9c-2.61 0-4.83-1.67-5.65-4h11.3c-.82 2.33-3.04 4-5.65 4z'/></svg>\n" +
"    </button>\n" +
"    <div style='width:30px;'></div>\n" +
"  </div>\n" +
"</section>\n" +
"<script>\n" +
"const state = {\n" +
"  baseHeight: 216, layout: 'letters', shiftState: 1, lastShiftTap: 0, lastSpaceTap: 0,\n" +
"  numberRow: false, swipeType: true, soundEffects: true, haptics: true,\n" +
"  spaceTrackpad: true, keyPopups: true, autoCap: true, doubleSpacePeriod: true,\n" +
"  aiLearn: true, apiKey: '', currentWord: '', prevWord: ''\n" +
"};\n" +
"let learnedDict = { words: {}, bigrams: {} };\n" +
"const LAYOUTS = {\n" +
"  letters: [['q','w','e','r','t','y','u','i','o','p'],['a','s','d','f','g','h','j','k','l'],['SHIFT','z','x','c','v','b','n','m','BACKSPACE'],['123','SPACE','RETURN']],\n" +
"  numbers: [['1','2','3','4','5','6','7','8','9','0'],['-','/',':',';','(',')','$','&','@','\"'],['#+=','.',',','?','!',\"'\",'BACKSPACE'],['ABC','SPACE','RETURN']],\n" +
"  symbols: [['[',']','{','}','#','%','^','*','+','='],['_','\\\\','|','~','<','>','€','£','¥','•'],['123','.',',','?','!',\"'\",'BACKSPACE'],['ABC','SPACE','RETURN']]\n" +
"};\n" +
"const WORD_POOL = ['I','The','You','School','iOS','that','this','have','with','from','they','will','would','there','their','what','about','which','when','make','like','time','just','know','take','people','into','year','your','good','some','could','them','other','than','then','now','look','only','come','its','over','think','also','back','after','use','two','how','our','work','first','well','way','even','new','want','because','any','these','give','day','most','awesome','beautiful','hello','please','thank','thanks','tomorrow','today','great','perfect'];\n" +
"const EMOJIS = {\n" +
"  smileys: ['😀','😃','😄','😁','😆','😅','😂','🤣','🥲','😊','😇','🙂','🙃','😉','😌','😍','🥰','😘','😋','😛','😝','😜','🤪','🤨','🧐','🤓','😎','🤩','🥳','😏','😒','😞','😔','😟','😕','🙁','😣','😖','😫','😩','🥺','😢','😭','😤','😠','😡','🤬','🤯','😳','🥵','🥶','😱','😨','🤗','🤔','🤭','🤫','🫠','😶','😐','😑','😬','🙄','😮','😲','🥱','😴','🤤','😵','🥴','🤢','🤮','🤧','😷','🤠','😈','👻','💀','🤖'],\n" +
"  gestures: ['👋','🤚','🖐️','✋','🖖','👌','🤌','🤏','✌️','🤞','🫰','🤟','🤘','🤙','👈','👉','👆','👇','☝️','🫵','👍','👎','✊','👊','🤛','🤜','👏','🙌','🫶','👐','🤲','🤝','🙏','✍️','💅','🤳','💪','👀','👄'],\n" +
"  hearts: ['❤️','🧡','💛','💚','💙','💜','🖤','🤎','💔','❤️‍🔥','❣️','💕','💞','💓','💗','💖','💘','💝','💯','💥','💫','💦','✨','🔥','🌟','⭐','🎉'],\n" +
"  animals: ['🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🙈','🙉','🙊','🐔','🐧','🐦','🐤','🦆','🦅','🦉','🦇','🐺','🐴','🦄','🐝','🦋','🐢','🐍','🦖','🐙','🐬','🐳','🦈'],\n" +
"  food: ['🍏','🍎','🍊','🍋','🍌','🍉','🍇','🍓','🍒','🍑','🥭','🍍','🥥','🥝','🍅','🥑','🥦','🌽','🥕','🥐','🍞','🧀','🥚','🍳','🥞','🥓','🍔','🍟','🍕','🌭','🥪','🌮','🌯','🥗','🍜','🍣','🍦','🍩','🍪','🎂','🍫','☕','🍺','🍷','🥤'],\n" +
"  objects: ['⌚','📱','💻','⌨️','🖥️','📷','📹','📞','📺','🔋','🔌','💡','🔦','💸','💵','💰','💳','💎','🔧','🔨','⚙️','🧲','💣','🔪','🛡️','🔮','🔭','🔬','💊','🚀','✈️','🚗','🏆','🎮','🎧','🎸']\n" +
"};\n" +
"const kbRows = document.getElementById('kbRowsContainer');\n" +
"const glideCanvas = document.getElementById('glideCanvas');\n" +
"const gCtx = glideCanvas.getContext('2d');\n" +
"let ptrDown = false, isGliding = false, gPts = [], gSeq = [], spcStart = 0;\n" +
"let bsTimeout = null, bsInterval = null, audioCtx = null;\n" +
"\n" +
"function updateStageAndWindowHeight() {\n" +
"  const extraNumHeight = (state.numberRow && state.layout === 'letters') ? 38 : 0;\n" +
"  const stagePx = state.baseHeight + extraNumHeight;\n" +
"  document.documentElement.style.setProperty('--kb-height', stagePx + 'px');\n" +
"  document.documentElement.style.setProperty('--key-gap-y', (state.numberRow && state.layout === 'letters') ? '7.5px' : '9.5px');\n" +
"  const totalDeckPx = stagePx + 74;\n" +
"  if(window.AndroidIME && AndroidIME.resizeInputView) {\n" +
"    AndroidIME.resizeInputView(totalDeckPx);\n" +
"  }\n" +
"}\n" +
"\n" +
"function normalizeWordCasing(w) {\n" +
"  if(!w) return '';\n" +
"  if(w.toLowerCase() === 'ios') return 'iOS';\n" +
"  if(w.toLowerCase() === 'i') return 'I';\n" +
"  return w;\n" +
"}\n" +
"\n" +
"function isValidWordToLearn(w) {\n" +
"  if(!w || w.length < 2 || w.length > 22) return false;\n" +
"  if(!/^[a-zA-Z']+$/.test(w)) return false;\n" +
"  if(w !== 'iOS' && /^[A-Z]{2,}[a-z]+$/.test(w)) return false;\n" +
"  if(/(.)\\1\\1/i.test(w)) return false;\n" +
"  if(!/[aeiouyAEIOUY]/.test(w)) return false;\n" +
"  return true;\n" +
"}\n" +
"\n" +
"function cleanCorruptedLearnedWords() {\n" +
"  if(!learnedDict || !learnedDict.words) return;\n" +
"  let changed = false;\n" +
"  Object.keys(learnedDict.words).forEach(w => {\n" +
"    if(w.toLowerCase() === 'ios' && w !== 'iOS') {\n" +
"      learnedDict.words['iOS'] = (learnedDict.words['iOS'] || 0) + learnedDict.words[w];\n" +
"      delete learnedDict.words[w];\n" +
"      changed = true;\n" +
"    } else if(!isValidWordToLearn(w)) {\n" +
"      delete learnedDict.words[w];\n" +
"      changed = true;\n" +
"    }\n" +
"  });\n" +
"  if(changed && window.AndroidIME) AndroidIME.saveLearnedData(JSON.stringify(learnedDict));\n" +
"}\n" +
"\n" +
"function learnWord(rawWord) {\n" +
"  if(!state.aiLearn || !rawWord) return;\n" +
"  const clean = normalizeWordCasing(rawWord.replace(/[^a-zA-Z']/g, ''));\n" +
"  if(!isValidWordToLearn(clean)) return;\n" +
"  if(!learnedDict.words) learnedDict.words = {};\n" +
"  if(!learnedDict.bigrams) learnedDict.bigrams = {};\n" +
"  learnedDict.words[clean] = (learnedDict.words[clean] || 0) + 1;\n" +
"  if(state.prevWord && isValidWordToLearn(state.prevWord)) {\n" +
"    const prev = state.prevWord.toLowerCase();\n" +
"    if(!learnedDict.bigrams[prev]) learnedDict.bigrams[prev] = {};\n" +
"    learnedDict.bigrams[prev][clean] = (learnedDict.bigrams[prev][clean] || 0) + 1;\n" +
"  }\n" +
"  state.prevWord = clean;\n" +
"  if(window.AndroidIME) AndroidIME.saveLearnedData(JSON.stringify(learnedDict));\n" +
"}\n" +
"\n" +
"/* BUG FIX #5 & #6: Automatically capitalize Q W E R T Y and reset predictions to I | The | You when field is empty */\n" +
"function onFieldFocused() {\n" +
"  syncNativePrefs();\n" +
"  if(!window.AndroidIME) return;\n" +
"  const before = AndroidIME.getBeforeCursor(50) || '';\n" +
"  if(before.trim().length === 0) {\n" +
"    state.currentWord = '';\n" +
"    state.prevWord = '';\n" +
"    if(state.autoCap) state.shiftState = 1;\n" +
"  } else if(state.autoCap && /[.!?]\\s+$/.test(before)) {\n" +
"    state.shiftState = 1;\n" +
"  }\n" +
"  renderKb();\n" +
"  updateSmartPredictions();\n" +
"}\n" +
"\n" +
"function updateSmartPredictions() {\n" +
"  const prefix = state.currentWord.toLowerCase();\n" +
"  let sugs = [];\n" +
"  const words = learnedDict.words || {};\n" +
"  const bigrams = learnedDict.bigrams || {};\n" +
"  if(!prefix && state.prevWord && bigrams[state.prevWord.toLowerCase()]) {\n" +
"    const nextMap = bigrams[state.prevWord.toLowerCase()];\n" +
"    sugs = Object.keys(nextMap).filter(isValidWordToLearn).map(normalizeWordCasing).sort((a,b) => (nextMap[b]||0) - (nextMap[a]||0));\n" +
"  } else if(prefix) {\n" +
"    const displayCurrent = normalizeWordCasing(state.currentWord);\n" +
"    const learnedMatches = Object.keys(words).filter(w => isValidWordToLearn(w) && w.toLowerCase().startsWith(prefix)).map(normalizeWordCasing).sort((a,b) => (words[b]||0) - (words[a]||0));\n" +
"    const baseMatches = WORD_POOL.filter(w => w.toLowerCase().startsWith(prefix) && !learnedMatches.includes(w));\n" +
"    sugs = [displayCurrent, ...learnedMatches, ...baseMatches];\n" +
"  } else {\n" +
"    /* BUG FIX #6: Clean capital starter words when input is empty */\n" +
"    sugs = ['I', 'The', 'You'];\n" +
"  }\n" +
"  const fallbacks = ['I', 'The', 'You', 'School'];\n" +
"  for(const f of fallbacks) {\n" +
"    if(sugs.length >= 3) break;\n" +
"    if(!sugs.some(x => x.toLowerCase() === f.toLowerCase())) sugs.push(f);\n" +
"  }\n" +
"  document.querySelectorAll('.sug-btn').forEach((b, i) => {\n" +
"    const w = sugs[i] || 'The';\n" +
"    b.textContent = w; b.dataset.w = w;\n" +
"  });\n" +
"}\n" +
"\n" +
"function playClick(type='letter') {\n" +
"  if(!state.soundEffects) return;\n" +
"  try {\n" +
"    if(!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();\n" +
"    if(audioCtx.state === 'suspended') audioCtx.resume();\n" +
"    const now = audioCtx.currentTime, osc = audioCtx.createOscillator(), gain = audioCtx.createGain();\n" +
"    osc.type = type === 'back' ? 'triangle' : 'sine';\n" +
"    osc.frequency.setValueAtTime(type === 'back' ? 210 : 420, now);\n" +
"    osc.frequency.exponentialRampToValueAtTime(120, now + 0.024);\n" +
"    gain.gain.setValueAtTime(0.14, now); gain.gain.exponentialRampToValueAtTime(0.001, now + 0.024);\n" +
"    osc.connect(gain); gain.connect(audioCtx.destination); osc.start(now); osc.stop(now + 0.03);\n" +
"  } catch(e) {}\n" +
"}\n" +
"\n" +
"function applyGlass(val) {\n" +
"  const v = Number(val) || 0;\n" +
"  document.documentElement.style.setProperty('--glass-blur', (v*0.22).toFixed(1)+'px');\n" +
"  const deckA = Math.max(0.35, 0.99 - (v/100)*0.55).toFixed(2);\n" +
"  const keyA = Math.max(0.42, 0.99 - (v/100)*0.50).toFixed(2);\n" +
"  const t = document.documentElement.className;\n" +
"  if(t === 'light') {\n" +
"    document.documentElement.style.setProperty('--kb-deck-bg', `rgba(209,212,219,${deckA})`);\n" +
"    document.documentElement.style.setProperty('--key-bg', `rgba(255,255,255,${keyA})`);\n" +
"    document.documentElement.style.setProperty('--key-mod-bg', `rgba(171,177,189,${keyA})`);\n" +
"  } else if(t === 'nothing') {\n" +
"    document.documentElement.style.setProperty('--kb-deck-bg', `rgba(0,0,0,${deckA})`);\n" +
"    document.documentElement.style.setProperty('--key-bg', `rgba(14,14,14,${keyA})`);\n" +
"    document.documentElement.style.setProperty('--key-mod-bg', `rgba(26,26,26,${keyA})`);\n" +
"  } else {\n" +
"    document.documentElement.style.setProperty('--kb-deck-bg', `rgba(28,29,36,${deckA})`);\n" +
"    document.documentElement.style.setProperty('--key-bg', `rgba(74,76,89,${keyA})`);\n" +
"    document.documentElement.style.setProperty('--key-mod-bg', `rgba(54,56,68,${keyA})`);\n" +
"  }\n" +
"  document.getElementById('glassValLabel').textContent = v + '%';\n" +
"}\n" +
"\n" +
"function syncNativePrefs() {\n" +
"  if(!window.AndroidIME) return;\n" +
"  try {\n" +
"    const p = JSON.parse(AndroidIME.getPrefsJson());\n" +
"    const mode = p.theme || 'dark';\n" +
"    const eff = mode === 'auto' ? (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : mode;\n" +
"    document.documentElement.className = eff;\n" +
"    if(AndroidIME.updateImeThemeBar) AndroidIME.updateImeThemeBar(eff);\n" +
"    document.querySelectorAll('[data-theme]').forEach(x => x.classList.toggle('active', x.dataset.theme === mode));\n" +
"\n" +
"    if(typeof p.height === 'number') state.baseHeight = p.height;\n" +
"    if(typeof p.width === 'number') document.documentElement.style.setProperty('--kb-width', p.width + '%');\n" +
"    if(typeof p.numScale === 'number') document.documentElement.style.setProperty('--num-row-scale', (p.numScale/100).toFixed(2));\n" +
"    /* BUG FIX #3: Clamp key radius so keys stay crisp iOS rectangles */\n" +
"    if(typeof p.radius === 'number') {\n" +
"      const clampedR = Math.min(8, Math.max(4, p.radius));\n" +
"      document.documentElement.style.setProperty('--key-radius', clampedR + 'px');\n" +
"    }\n" +
"    if(typeof p.gap === 'number') {\n" +
"      document.documentElement.style.setProperty('--key-gap-x', p.gap + 'px');\n" +
"    }\n" +
"    if(typeof p.scale === 'number') {\n" +
"      document.documentElement.style.setProperty('--key-scale', (p.scale/100).toFixed(2));\n" +
"      document.getElementById('scaleRange').value = p.scale;\n" +
"      document.getElementById('scaleValLabel').textContent = p.scale + '%';\n" +
"    }\n" +
"    if(typeof p.glass === 'number') {\n" +
"      document.getElementById('glassRange').value = p.glass;\n" +
"      applyGlass(p.glass);\n" +
"    }\n" +
"    const deck = document.getElementById('kbDeck');\n" +
"    deck.classList.remove('hand-left', 'hand-right');\n" +
"    if(p.oneHanded === 'left') { deck.classList.add('hand-left'); document.documentElement.style.setProperty('--kb-width', '84%'); }\n" +
"    else if(p.oneHanded === 'right') { deck.classList.add('hand-right'); document.documentElement.style.setProperty('--kb-width', '84%'); }\n" +
"\n" +
"    const prevNumRow = state.numberRow;\n" +
"    ['numberRow','swipeType','soundEffects','haptics','spaceTrackpad','keyPopups','autoCap','doubleSpacePeriod','aiLearn'].forEach(k => {\n" +
"      if(typeof p[k] === 'boolean') state[k] = p[k];\n" +
"    });\n" +
"    document.getElementById('numRowSwitch').classList.toggle('active', state.numberRow);\n" +
"    document.getElementById('swipeSwitch').classList.toggle('active', state.swipeType);\n" +
"    document.getElementById('soundSwitch').classList.toggle('active', state.soundEffects);\n" +
"    if(p.apiKey !== undefined) state.apiKey = p.apiKey;\n" +
"    if(p.learnedData) {\n" +
"      try {\n" +
"        learnedDict = JSON.parse(p.learnedData);\n" +
"        cleanCorruptedLearnedWords();\n" +
"      } catch(e) {}\n" +
"    }\n" +
"    updateStageAndWindowHeight();\n" +
"    if(prevNumRow !== state.numberRow) renderKb();\n" +
"    updateSmartPredictions();\n" +
"  } catch(e) {}\n" +
"}\n" +
"\n" +
"function vibe(ms=8) { if(window.AndroidIME) AndroidIME.vibrate(ms); }\n" +
"function toast(m) { const t = document.getElementById('toastBanner'); t.textContent = m; t.style.opacity = 1; setTimeout(()=>t.style.opacity=0, 1300); }\n" +
"function commit(s) { if(window.AndroidIME) AndroidIME.commitText(s); }\n" +
"function doBackspace() {\n" +
"  if(window.AndroidIME) AndroidIME.deleteText();\n" +
"  if(state.currentWord.length > 0) state.currentWord = state.currentWord.slice(0, -1);\n" +
"  if(window.AndroidIME) {\n" +
"    const before = AndroidIME.getBeforeCursor(20) || '';\n" +
"    if(before.length === 0) {\n" +
"      state.currentWord = ''; state.prevWord = '';\n" +
"      if(state.autoCap && state.shiftState === 0) { state.shiftState = 1; renderKb(); }\n" +
"    }\n" +
"  }\n" +
"  updateSmartPredictions();\n" +
"}\n" +
"function stopBackspace() { clearTimeout(bsTimeout); clearInterval(bsInterval); }\n" +
"\n" +
"function renderKb() {\n" +
"  updateStageAndWindowHeight();\n" +
"  Array.from(kbRows.children).forEach(c => { if(c.id !== 'glideCanvas') c.remove(); });\n" +
"  const rows = [...LAYOUTS[state.layout]];\n" +
"  if(state.numberRow && state.layout === 'letters') rows.unshift(['1','2','3','4','5','6','7','8','9','0']);\n" +
"  rows.forEach((r, idx) => {\n" +
"    const rowEl = document.createElement('div'); rowEl.className = 'kb-row';\n" +
"    if(state.numberRow && state.layout === 'letters' && idx === 0) rowEl.classList.add('num-row');\n" +
"    if(state.layout === 'letters' && r.length === 9 && r[0] === 'a') { rowEl.style.paddingLeft='5%'; rowEl.style.paddingRight='5%'; }\n" +
"    r.forEach(k => {\n" +
"      const btn = document.createElement('div'); btn.dataset.key = k; btn.className = 'kb-key';\n" +
"      const isMod = ['SHIFT','BACKSPACE','123','#+=','ABC','RETURN'].includes(k);\n" +
"      if(isMod) btn.classList.add('mod-key');\n" +
"      else if(k === 'SPACE') btn.classList.add('space-key');\n" +
"      else btn.classList.add('letter-key');\n" +
"      if(k==='SHIFT'||k==='BACKSPACE'||k==='#+=') btn.style.flex='1.32';\n" +
"      else if(k==='123'||k==='ABC') btn.style.flex='1.5';\n" +
"      else if(k==='SPACE') btn.style.flex='4.8';\n" +
"      else if(k==='RETURN') btn.style.flex='2.2';\n" +
"      if(k==='SHIFT') btn.innerHTML = state.shiftState > 0 ? `<svg width='19' height='19' viewBox='0 0 24 24' fill='currentColor'><path d='M12 4L4 12.5h5V19h6v-6.5h5L12 4z'/></svg>` : `<svg width='19' height='19' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linejoin='round'><path d='M12 4L4 12.5h5V19h6v-6.5h5L12 4z'/></svg>`;\n" +
"      else if(k==='BACKSPACE') btn.innerHTML = `<svg width='21' height='21' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.9' stroke-linecap='round' stroke-linejoin='round'><path d='M20 5H9.2a2 2 0 00-1.48.66L3.3 11.34a1 1 0 000 1.32l4.42 5.68A2 2 0 009.2 19H20a2 2 0 002-2V7a2 2 0 00-2-2z'/><line x1='16.2' y1='9.5' x2='11.2' y2='14.5'/><line x1='11.2' y1='9.5' x2='16.2' y2='14.5'/></svg>`;\n" +
"      else if(k==='RETURN') btn.innerHTML = `<svg width='20' height='20' viewBox='0 0 26 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M16 7h4a2 2 0 012 2v5a2 2 0 01-2 2H6'/><polyline points='10 12 6 16 10 20'/></svg>`;\n" +
"      /* BUG FIX #9: Authentic iOS 'space' label inside the spacebar */\n" +
"      else if(k==='SPACE') btn.textContent = 'space';\n" +
"      else if(isMod) btn.textContent = k;\n" +
"      else {\n" +
"        const c = (state.layout === 'letters' && state.shiftState > 0) ? k.toUpperCase() : k;\n" +
"        btn.innerHTML = `<span>${c}</span><div class='key-popup'>${c}</div>`;\n" +
"      }\n" +
"      if(k==='SHIFT' && state.shiftState > 0) btn.classList.add('shift-active');\n" +
"      rowEl.appendChild(btn);\n" +
"    });\n" +
"    kbRows.appendChild(rowEl);\n" +
"  });\n" +
"}\n" +
"\n" +
"kbRows.addEventListener('pointerdown', e => {\n" +
"  const kEl = e.target.closest('.kb-key'); if(!kEl) return;\n" +
"  e.preventDefault(); kEl.setPointerCapture(e.pointerId); ptrDown = true;\n" +
"  const k = kEl.dataset.key;\n" +
"  kEl.classList.add('pressed');\n" +
"  if(state.keyPopups && kEl.classList.contains('letter-key')) kEl.classList.add('show-popup');\n" +
"  vibe(8); playClick(k==='BACKSPACE'?'back':'letter');\n" +
"  if(k === 'BACKSPACE') {\n" +
"    doBackspace();\n" +
"    bsTimeout = setTimeout(() => { bsInterval = setInterval(() => { doBackspace(); vibe(4); playClick('back'); }, 65); }, 380);\n" +
"  } else if(k === 'SPACE') { spcStart = e.clientX; }\n" +
"  else if(state.swipeType && state.layout === 'letters' && kEl.classList.contains('letter-key')) {\n" +
"    isGliding = false; gPts = [{x: e.clientX, y: e.clientY}]; gSeq = [k];\n" +
"  }\n" +
"});\n" +
"\n" +
"kbRows.addEventListener('pointermove', e => {\n" +
"  if(!ptrDown) return;\n" +
"  const kEl = e.target.closest('.kb-key'); if(!kEl) return;\n" +
"  const k = kEl.dataset.key;\n" +
"  if(k === 'SPACE' && state.spaceTrackpad) {\n" +
"    const dx = e.clientX - spcStart;\n" +
"    if(Math.abs(dx) > 14) { if(window.AndroidIME) AndroidIME.moveCursor(dx > 0 ? 1 : -1); spcStart = e.clientX; vibe(4); }\n" +
"  } else if(state.swipeType && gPts.length > 0) {\n" +
"    if(!isGliding && Math.hypot(e.clientX - gPts[0].x, e.clientY - gPts[0].y) > 9) {\n" +
"      isGliding = true; glideCanvas.style.display = 'block'; glideCanvas.width = kbRows.clientWidth; glideCanvas.height = kbRows.clientHeight;\n" +
"    }\n" +
"    if(isGliding) {\n" +
"      const r = kbRows.getBoundingClientRect(); gPts.push({x: e.clientX, y: e.clientY});\n" +
"      const over = document.elementFromPoint(e.clientX, e.clientY)?.closest('.kb-key');\n" +
"      if(over && over.dataset.key && over.classList.contains('letter-key')) {\n" +
"        if(gSeq[gSeq.length-1] !== over.dataset.key) gSeq.push(over.dataset.key);\n" +
"      }\n" +
"      gCtx.clearRect(0,0,glideCanvas.width,glideCanvas.height);\n" +
"      gCtx.beginPath(); gCtx.moveTo(gPts[0].x - r.left, gPts[0].y - r.top);\n" +
"      for(let i=1; i<gPts.length; i++) gCtx.lineTo(gPts[i].x - r.left, gPts[i].y - r.top);\n" +
"      gCtx.strokeStyle = '#007AFF'; gCtx.lineWidth = 5; gCtx.lineCap = 'round'; gCtx.stroke();\n" +
"    }\n" +
"  }\n" +
"});\n" +
"\n" +
"kbRows.addEventListener('pointerup', e => {\n" +
"  ptrDown = false; document.querySelectorAll('.kb-key.pressed').forEach(el => el.classList.remove('pressed', 'show-popup'));\n" +
"  stopBackspace();\n" +
"  if(isGliding) {\n" +
"    isGliding = false; glideCanvas.style.display = 'none'; gCtx.clearRect(0,0,glideCanvas.width,glideCanvas.height);\n" +
"    if(gSeq.length >= 2) {\n" +
"      const allWords = [...Object.keys(learnedDict.words || {}), ...WORD_POOL];\n" +
"      let reg = new RegExp('^' + gSeq.join('.*') + '$', 'i');\n" +
"      let match = allWords.find(w => reg.test(w)) || allWords.find(w => w.toLowerCase().startsWith(gSeq[0]) && w.toLowerCase().endsWith(gSeq[gSeq.length-1]));\n" +
"      if(match) {\n" +
"        match = normalizeWordCasing(match);\n" +
"        commit(match + ' '); learnWord(match); state.currentWord = '';\n" +
"        if(state.shiftState === 1) { state.shiftState = 0; renderKb(); }\n" +
"        updateSmartPredictions();\n" +
"      }\n" +
"    }\n" +
"    return;\n" +
"  }\n" +
"  const kEl = e.target.closest('.kb-key'); if(!kEl) return;\n" +
"  const k = kEl.dataset.key;\n" +
"  if(k === 'SHIFT') {\n" +
"    const now = Date.now();\n" +
"    state.shiftState = (now - state.lastShiftTap < 300) ? 2 : (state.shiftState === 0 ? 1 : 0);\n" +
"    state.lastShiftTap = now; renderKb();\n" +
"  } else if(k === '123') { state.layout = 'numbers'; renderKb(); }\n" +
"  else if(k === '#+=') { state.layout = 'symbols'; renderKb(); }\n" +
"  else if(k === 'ABC') { state.layout = 'letters'; renderKb(); }\n" +
"  else if(k === 'RETURN') {\n" +
"    if(state.currentWord) { learnWord(state.currentWord); state.currentWord = ''; }\n" +
"    if(window.AndroidIME) AndroidIME.sendEnter();\n" +
"    if(state.autoCap) { state.shiftState = 1; renderKb(); }\n" +
"    updateSmartPredictions();\n" +
"  } else if(k === 'SPACE') {\n" +
"    const now = Date.now();\n" +
"    if(state.doubleSpacePeriod && (now - state.lastSpaceTap < 280) && state.currentWord === '') {\n" +
"      if(window.AndroidIME) { AndroidIME.deleteText(); AndroidIME.commitText('. '); }\n" +
"      if(state.autoCap) { state.shiftState = 1; renderKb(); }\n" +
"    } else {\n" +
"      if(state.currentWord) {\n" +
"        if(state.currentWord.toLowerCase() === 'ios' && window.AndroidIME) AndroidIME.replaceCurrentWord('iOS ');\n" +
"        else commit(' ');\n" +
"        learnWord(state.currentWord);\n" +
"        state.currentWord = '';\n" +
"      } else {\n" +
"        commit(' ');\n" +
"      }\n" +
"    }\n" +
"    state.lastSpaceTap = now;\n" +
"    updateSmartPredictions();\n" +
"  } else if(k !== 'BACKSPACE') {\n" +
"    const c = (state.layout === 'letters' && state.shiftState > 0) ? k.toUpperCase() : k;\n" +
"    commit(c);\n" +
"    if(/^[a-zA-Z']$/.test(c)) state.currentWord += c;\n" +
"    else if(state.currentWord) { learnWord(state.currentWord); state.currentWord = ''; }\n" +
"    if(state.shiftState === 1) { state.shiftState = 0; renderKb(); }\n" +
"    else if(state.autoCap && (c === '.' || c === '!' || c === '?')) { state.shiftState = 1; renderKb(); }\n" +
"    updateSmartPredictions();\n" +
"  }\n" +
"});\n" +
"\n" +
"document.getElementById('predictionsContainer').onclick = e => {\n" +
"  const b = e.target.closest('.sug-btn');\n" +
"  if(b) {\n" +
"    vibe(8);\n" +
"    const w = normalizeWordCasing(b.dataset.w);\n" +
"    if(state.currentWord && window.AndroidIME) AndroidIME.replaceCurrentWord(w + ' ');\n" +
"    else commit(w + ' ');\n" +
"    learnWord(w); state.currentWord = '';\n" +
"    if(state.shiftState === 1) { state.shiftState = 0; renderKb(); }\n" +
"    updateSmartPredictions();\n" +
"  }\n" +
"};\n" +
"\n" +
"function closeAllDrawers() {\n" +
"  document.querySelectorAll('.overlay-drawer').forEach(d => d.classList.remove('open'));\n" +
"  kbRows.style.opacity = '1'; kbRows.style.pointerEvents = 'auto';\n" +
"}\n" +
"function toggleDrawer(id) {\n" +
"  vibe(8); playClick('letter');\n" +
"  const el = document.getElementById(id);\n" +
"  const wasOpen = el.classList.contains('open');\n" +
"  closeAllDrawers();\n" +
"  if(!wasOpen) { el.classList.add('open'); kbRows.style.opacity = '0.08'; kbRows.style.pointerEvents = 'none'; }\n" +
"}\n" +
"\n" +
"document.getElementById('aiBtn').onclick = () => toggleDrawer('aiOverlay');\n" +
"document.getElementById('closeAiBtn').onclick = closeAllDrawers;\n" +
"document.getElementById('settingsBtn').onclick = () => toggleDrawer('settingsOverlay');\n" +
"document.getElementById('closeSettingsBtn').onclick = closeAllDrawers;\n" +
"document.getElementById('chinEmojiBtn').onclick = () => toggleDrawer('emojiOverlay');\n" +
"document.getElementById('closeEmojiBtn').onclick = closeAllDrawers;\n" +
"document.getElementById('emojiBsBtn').onclick = () => { vibe(6); playClick('back'); doBackspace(); };\n" +
"document.getElementById('fullAppBtn').onclick = () => { if(window.AndroidIME) AndroidIME.openAppSettings(); };\n" +
"\n" +
"document.querySelectorAll('[data-theme]').forEach(btn => {\n" +
"  btn.onclick = () => {\n" +
"    vibe(8); const t = btn.dataset.theme; document.documentElement.className = t;\n" +
"    if(window.AndroidIME) {\n" +
"      AndroidIME.savePrefString('theme', t);\n" +
"      if(AndroidIME.updateImeThemeBar) AndroidIME.updateImeThemeBar(t);\n" +
"    }\n" +
"    document.querySelectorAll('[data-theme]').forEach(x => x.classList.remove('active'));\n" +
"    btn.classList.add('active');\n" +
"    applyGlass(document.getElementById('glassRange').value);\n" +
"  };\n" +
"});\n" +
"\n" +
"document.getElementById('scaleRange').oninput = e => {\n" +
"  const v = Number(e.target.value);\n" +
"  document.documentElement.style.setProperty('--key-scale', (v/100).toFixed(2));\n" +
"  document.getElementById('scaleValLabel').textContent = v + '%';\n" +
"  if(window.AndroidIME) AndroidIME.savePrefInt('scale', v);\n" +
"};\n" +
"document.getElementById('glassRange').oninput = e => {\n" +
"  const v = Number(e.target.value);\n" +
"  applyGlass(v);\n" +
"  if(window.AndroidIME) AndroidIME.savePrefInt('glass', v);\n" +
"};\n" +
"document.getElementById('numRowSwitch').onclick = function() {\n" +
"  state.numberRow = !state.numberRow;\n" +
"  this.classList.toggle('active', state.numberRow);\n" +
"  vibe(8);\n" +
"  if(window.AndroidIME) AndroidIME.savePrefBool('numberRow', state.numberRow);\n" +
"  renderKb();\n" +
"};\n" +
"document.getElementById('swipeSwitch').onclick = function() {\n" +
"  state.swipeType = !state.swipeType;\n" +
"  this.classList.toggle('active', state.swipeType);\n" +
"  vibe(8);\n" +
"  if(window.AndroidIME) AndroidIME.savePrefBool('swipeType', state.swipeType);\n" +
"};\n" +
"document.getElementById('soundSwitch').onclick = function() {\n" +
"  state.soundEffects = !state.soundEffects;\n" +
"  this.classList.toggle('active', state.soundEffects);\n" +
"  vibe(8);\n" +
"  if(window.AndroidIME) AndroidIME.savePrefBool('soundEffects', state.soundEffects);\n" +
"};\n" +
"\n" +
"const eg = document.getElementById('emojiGrid');\n" +
"function loadEmojiCat(cat) {\n" +
"  eg.innerHTML = '';\n" +
"  (EMOJIS[cat] || EMOJIS.smileys).forEach(em => {\n" +
"    const b = document.createElement('div'); b.textContent = em; b.style.cursor = 'pointer'; b.style.padding = '4px 0';\n" +
"    b.onclick = () => { vibe(6); playClick('letter'); commit(em); }; eg.appendChild(b);\n" +
"  });\n" +
"}\n" +
"document.querySelectorAll('.cat-btn').forEach(btn => {\n" +
"  btn.onclick = () => {\n" +
"    vibe(6); document.querySelectorAll('.cat-btn').forEach(x => x.classList.remove('active'));\n" +
"    btn.classList.add('active'); loadEmojiCat(btn.dataset.cat);\n" +
"  };\n" +
"});\n" +
"\n" +
"document.querySelectorAll('[data-ai]').forEach(btn => {\n" +
"  btn.onclick = async () => {\n" +
"    vibe(12);\n" +
"    const raw = window.AndroidIME ? AndroidIME.getFieldText() : '';\n" +
"    if(!raw || !raw.trim()) { toast('Type text first!'); return; }\n" +
"    const mode = btn.dataset.ai;\n" +
"    const apiKey = state.apiKey;\n" +
"    toast('AI Processing...');\n" +
"    if(apiKey) {\n" +
"      try {\n" +
"        const prompts = {\n" +
"          ASK: 'Answer concisely:',\n" +
"          FIX: 'Fix grammar and spelling, return ONLY corrected text:',\n" +
"          IMPROVE: 'Improve fluency and polish, return ONLY improved text:',\n" +
"          SHORT: 'Make shorter and concise, return ONLY text:',\n" +
"          LONG: 'Expand with detail, return ONLY text:',\n" +
"          PRO: 'Rewrite in a professional tone, return ONLY text:',\n" +
"          CASUAL: 'Rewrite in a casual friendly tone, return ONLY text:',\n" +
"          SARCASTIC: 'Rewrite in a witty sarcastic tone, return ONLY text:',\n" +
"          POETIC: 'Rewrite in a poetic tone, return ONLY text:'\n" +
"        };\n" +
"        const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`, {\n" +
"          method: 'POST', headers: {'Content-Type':'application/json'},\n" +
"          body: JSON.stringify({ contents: [{ parts: [{ text: prompts[mode] + '\\n\\n' + raw }] }] })\n" +
"        });\n" +
"        const data = await res.json();\n" +
"        const out = data.candidates?.[0]?.content?.parts?.[0]?.text;\n" +
"        if(out && window.AndroidIME) {\n" +
"          AndroidIME.replaceFieldText(out.trim());\n" +
"          closeAllDrawers(); toast('Done! ✨'); return;\n" +
"        }\n" +
"      } catch(err) {}\n" +
"    }\n" +
"    let out = raw.trim();\n" +
"    if(mode === 'FIX') {\n" +
"      out = out.replace(/\\bi\\b/g,'I').replace(/\\bios\\b/gi,'iOS').replace(/\\bim\\b/gi,\"I'm\").replace(/\\bdont\\b/gi,\"don't\").replace(/\\bu\\b/gi,'you').replace(/\\br\\b/gi,'are');\n" +
"      out = out.charAt(0).toUpperCase() + out.slice(1);\n" +
"      if(!/[.!?]$/.test(out)) out += '.';\n" +
"    } else if(mode === 'IMPROVE') { out = out.charAt(0).toUpperCase() + out.slice(1) + ' ✨'; }\n" +
"    else if(mode === 'SHORT') { out = out.split(/\\s+/).slice(0,4).join(' ') + '...'; }\n" +
"    else if(mode === 'LONG') { out = out + ' — please let me know your thoughts on this when you get a moment.'; }\n" +
"    else if(mode === 'PRO') { out = 'Dear team, regarding ' + out + '. Best regards.'; }\n" +
"    else if(mode === 'CASUAL') { out = 'Hey! ' + out + ' 😊'; }\n" +
"    else if(mode === 'SARCASTIC') { out = 'Oh sure, because ' + out + ' is totally how that works 🙄'; }\n" +
"    else if(mode === 'POETIC') { out = 'Like whispers in the wind, ' + out + ' ✨'; }\n" +
"    if(window.AndroidIME) AndroidIME.replaceFieldText(out);\n" +
"    closeAllDrawers(); toast('AI Applied! ✨');\n" +
"  };\n" +
"});\n" +
"\n" +
"loadEmojiCat('smileys');\n" +
"onFieldFocused();\n" +
"</script>\n" +
"</body>\n" +
"</html>";
    }
}
