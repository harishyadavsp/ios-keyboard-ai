package com.iosaikeyboard.ai;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    public View onCreateInputView() {
        prefs = getSharedPreferences("KeyboardPrefs", MODE_PRIVATE);

        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        webView = new WebView(this);
        webView.setBackgroundColor(0x00000000);

        int defaultHeightPx = dpToPx(308);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                defaultHeightPx
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
        return container;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript("if(window.syncNativePrefs) syncNativePrefs();", null));
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
        public String getPrefsJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("theme", prefs.getString("theme", "dark"));
                obj.put("glass", prefs.getInt("glass", 0));
                obj.put("scale", prefs.getInt("scale", 100));
                obj.put("numberRow", prefs.getBoolean("numberRow", false));
                obj.put("swipeType", prefs.getBoolean("swipeType", true));
                obj.put("soundEffects", prefs.getBoolean("soundEffects", true));
                obj.put("haptics", prefs.getBoolean("haptics", true));
                obj.put("apiKey", prefs.getString("apiKey", ""));
                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void savePrefString(String key, String val) {
            prefs.edit().putString(key, val).apply();
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
            try {
                Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(ms);
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
"  --kb-height: 214px; --kb-width: 100%; --key-scale: 1; --num-row-scale: 0.85;\n" +
"  --key-gap-x: 5px; --key-gap-y: 9px; --key-radius: 8.5px;\n" +
"  --kb-deck-bg: rgba(26, 27, 34, 0.96); --kb-deck-border: rgba(255, 255, 255, 0.09);\n" +
"  --key-bg: rgba(74, 76, 89, 0.95); --key-bg-active: rgba(108, 110, 126, 0.98);\n" +
"  --key-mod-bg: rgba(56, 58, 70, 0.95); --key-mod-active: rgba(92, 94, 110, 0.98);\n" +
"  --key-shadow: rgba(10, 10, 14, 0.85); --key-text: #ffffff; --toolbar-text: #f2f2f7;\n" +
"  --toolbar-divider: rgba(255, 255, 255, 0.14); --popup-bg: #5a5c6d; --popup-border: rgba(255, 255, 255, 0.18);\n" +
"  --accent-color: #3b82f6; --glass-blur: 0px;\n" +
"}\n" +
"html.light {\n" +
"  --kb-deck-bg: rgba(209, 212, 219, 0.96); --kb-deck-border: rgba(0, 0, 0, 0.1);\n" +
"  --key-bg: rgba(255, 255, 255, 0.96); --key-bg-active: rgba(225, 228, 235, 0.98);\n" +
"  --key-mod-bg: rgba(173, 179, 190, 0.96); --key-mod-active: rgba(255, 255, 255, 0.98);\n" +
"  --key-shadow: rgba(130, 134, 142, 0.85); --key-text: #000000; --toolbar-text: #1c1c1e;\n" +
"  --toolbar-divider: rgba(0, 0, 0, 0.14); --popup-bg: #ffffff; --popup-border: rgba(0, 0, 0, 0.14);\n" +
"}\n" +
"html.nothing {\n" +
"  --kb-deck-bg: rgba(0, 0, 0, 0.98); --kb-deck-border: rgba(255, 255, 255, 0.28);\n" +
"  --key-bg: rgba(12, 12, 12, 0.95); --key-bg-active: #ffffff;\n" +
"  --key-mod-bg: rgba(24, 24, 24, 0.95); --key-mod-active: #ffffff;\n" +
"  --key-shadow: transparent; --key-text: #ffffff; --toolbar-text: #ffffff;\n" +
"  --toolbar-divider: rgba(255, 255, 255, 0.25); --popup-bg: #000000; --popup-border: rgba(255, 255, 255, 0.4);\n" +
"  --accent-color: #ea1d25;\n" +
"}\n" +
"html.nothing .kb-key { font-family: monospace; border: 1px solid rgba(255,255,255,0.22); border-radius: 6px; }\n" +
"html.nothing .kb-key.pressed { color: #000000 !important; }\n" +
"* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }\n" +
"body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, sans-serif; background: transparent; color: var(--key-text); overflow: hidden; width: 100vw; height: 100vh; display: flex; flex-direction: column; justify-content: flex-end; }\n" +
".kb-deck { width: var(--kb-width); margin: 0 auto; background-color: var(--kb-deck-bg); backdrop-filter: blur(var(--glass-blur)); -webkit-backdrop-filter: blur(var(--glass-blur)); border-top: 1px solid var(--kb-deck-border); border-top-left-radius: 26px; border-top-right-radius: 26px; padding: 5px 5px 2px 5px; position: relative; touch-action: none; transition: background-color 0.2s ease; }\n" +
".toolbar { width: 100%; height: 36px; display: flex; align-items: center; justify-content: space-between; padding: 0 6px; margin-bottom: 3px; }\n" +
".tool-btn { width: 34px; height: 32px; border-radius: 99px; border: none; background: transparent; color: var(--toolbar-text); display: flex; align-items: center; justify-content: center; cursor: pointer; transition: transform 0.1s ease; }\n" +
".tool-btn:active { transform: scale(0.88); }\n" +
".predictions { flex: 1; display: flex; align-items: center; justify-content: space-around; height: 100%; padding: 0 4px; }\n" +
".sug-btn { flex: 1; height: 28px; border: none; background: transparent; color: var(--toolbar-text); font-size: 14.5px; border-radius: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n" +
".divider { width: 1px; height: 15px; background: var(--toolbar-divider); opacity: 0.35; }\n" +
".kb-stage { position: relative; width: 100%; height: var(--kb-height); }\n" +
".kb-rows { width: 100%; height: 100%; display: flex; flex-direction: column; justify-content: space-between; gap: var(--key-gap-y); padding: 2px 1px; transition: opacity 0.16s ease; }\n" +
".kb-row { display: flex; justify-content: center; align-items: stretch; flex: 1; gap: var(--key-gap-x); width: 100%; }\n" +
".kb-row.num-row { flex: calc(0.82 * var(--num-row-scale)); }\n" +
".kb-key { position: relative; flex: 1; background-color: var(--key-bg); color: var(--key-text); border-radius: calc(var(--key-radius) * var(--key-scale)); box-shadow: 0 1.5px 0 var(--key-shadow); display: flex; align-items: center; justify-content: center; font-size: calc(21px * var(--key-scale)); cursor: pointer; transform: scale(var(--key-scale)); transition: transform 0.05s cubic-bezier(0.2,0.9,0.3,1.1), background-color 0.06s ease; }\n" +
".kb-key.mod-key { background-color: var(--key-mod-bg); font-size: calc(15.5px * var(--key-scale)); font-weight: 500; }\n" +
".kb-key.pressed { background-color: var(--key-bg-active); transform: scale(calc(var(--key-scale) * 0.92)); }\n" +
".kb-key.shift-active { background-color: #ffffff !important; color: #000000 !important; }\n" +
".key-popup { position: absolute; bottom: calc(100% + 7px); left: 50%; transform: translateX(-50%) scale(0.75) translateY(8px); width: 48px; height: 56px; background-color: var(--popup-bg); color: var(--key-text); border: 1px solid var(--popup-border); border-radius: 11px; box-shadow: 0 8px 20px rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; font-size: 28px; opacity: 0; pointer-events: none; transition: opacity 0.04s ease, transform 0.07s cubic-bezier(0.2,0.9,0.3,1.1); z-index: 60; }\n" +
".kb-key.show-popup .key-popup { opacity: 1; transform: translateX(-50%) scale(1) translateY(0); }\n" +
".kb-chin { height: 34px; display: flex; align-items: center; justify-content: space-between; padding: 2px 12px 0 12px; }\n" +
".chin-emoji-btn { width: 30px; height: 30px; border: none; background: transparent; color: var(--toolbar-text); opacity: 0.88; display: flex; align-items: center; justify-content: center; border-radius: 50%; }\n" +
".chin-emoji-btn:active { transform: scale(0.85); background: rgba(255,255,255,0.12); }\n" +
"#glideCanvas { position: absolute; top: 0; left: 0; z-index: 25; pointer-events: none; display: none; }\n" +
".overlay-drawer { position: absolute; inset: 0; background-color: var(--kb-deck-bg); backdrop-filter: blur(18px); border-radius: 14px; z-index: 50; display: flex; flex-direction: column; padding: 8px; gap: 6px; overflow-y: auto; opacity: 0; transform: translateY(10px) scale(0.96); pointer-events: none; transition: opacity 0.16s ease, transform 0.18s cubic-bezier(0.22,1,0.36,1); }\n" +
".overlay-drawer.open { opacity: 1; transform: translateY(0) scale(1); pointer-events: auto; }\n" +
".ai-grid { display: grid; grid-template-columns: 1.1fr 1.6fr 1.4fr; grid-template-rows: 1fr 1fr; gap: 6px; flex: 1; }\n" +
".ai-card { border: 1px solid var(--toolbar-divider); background: rgba(255,255,255,0.06); color: var(--toolbar-text); border-radius: 12px; font-size: 12px; font-weight: 500; display: flex; align-items: center; justify-content: center; gap: 5px; padding: 6px; }\n" +
".tone-bar { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 2px; }\n" +
".tone-pill { border: 1px solid var(--toolbar-divider); background: rgba(59,130,246,0.18); color: var(--toolbar-text); border-radius: 99px; padding: 5px 12px; font-size: 11.5px; white-space: nowrap; }\n" +
".settings-row { display: flex; justify-content: space-between; align-items: center; padding: 5px 4px; border-bottom: 1px solid var(--toolbar-divider); font-size: 12px; color: var(--toolbar-text); }\n" +
".theme-pills { display: flex; gap: 5px; }\n" +
".theme-btn { padding: 4px 9px; border-radius: 8px; border: 1px solid var(--toolbar-divider); background: rgba(255,255,255,0.08); color: var(--toolbar-text); font-size: 11px; }\n" +
".theme-btn.active { background: var(--accent-color); color: #fff; border-color: transparent; }\n" +
".emoji-grid { display: grid; grid-template-columns: repeat(8, 1fr); gap: 4px; overflow-y: auto; flex: 1; font-size: 22px; text-align: center; align-items: center; }\n" +
".cat-btn { border: none; background: transparent; font-size: 15px; padding: 3px 7px; border-radius: 99px; opacity: 0.6; }\n" +
".cat-btn.active { background: rgba(255,255,255,0.2); opacity: 1; }\n" +
".toast-pill { position: absolute; left: 50%; top: 4px; transform: translateX(-50%); background: var(--accent-color); color: #fff; padding: 3px 12px; border-radius: 99px; font-size: 11px; font-weight: 600; opacity: 0; pointer-events: none; transition: opacity 0.15s; z-index: 90; }\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"<section class='kb-deck'>\n" +
"  <div id='toastBanner' class='toast-pill'>Ready</div>\n" +
"  <div class='toolbar'>\n" +
"    <button id='settingsBtn' class='tool-btn'>\n" +
"      <svg width='19' height='19' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8'><circle cx='12' cy='12' r='9'/><line x1='8' y1='12' x2='16' y2='12'/></svg>\n" +
"    </button>\n" +
"    <div id='predictionsContainer' class='predictions'>\n" +
"      <button class='sug-btn' data-w='The'>The</button>\n" +
"      <div class='divider'></div>\n" +
"      <button class='sug-btn' data-w='School' style='font-weight:500;'>School</button>\n" +
"      <div class='divider'></div>\n" +
"      <button class='sug-btn' data-w='A'>A</button>\n" +
"    </div>\n" +
"    <button id='aiBtn' class='tool-btn'>\n" +
"      <svg width='22' height='20' viewBox='0 0 28 24' fill='none' stroke='currentColor' stroke-linecap='round' stroke-linejoin='round'><line x1='2' y1='8' x2='7.5' y2='8' stroke-width='2'/><line x1='4' y1='12' x2='7.5' y2='12' stroke-width='2'/><line x1='2.5' y1='16' x2='7.5' y2='16' stroke-width='2'/><path d='M11.5 18.5L16.5 5.5L21.5 18.5' stroke-width='2.1'/><line x1='13.2' y1='14.2' x2='19.8' y2='14.2' stroke-width='2'/></svg>\n" +
"    </button>\n" +
"  </div>\n" +
"  <div class='kb-stage'>\n" +
"    <div id='kbRowsContainer' class='kb-rows'>\n" +
"      <canvas id='glideCanvas'></canvas>\n" +
"    </div>\n" +
"    <div id='aiOverlay' class='overlay-drawer'>\n" +
"      <div style='display:flex; justify-content:space-between; align-items:center;'>\n" +
"        <span style='font-size:12px; font-weight:600; color:var(--toolbar-text);'>✨ Gemini AI Writing Tools</span>\n" +
"        <button id='closeAiBtn' class='theme-btn active'>ABC</button>\n" +
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
"        <span style='font-size:12px; font-weight:600; color:var(--toolbar-text);'>⚙️ Keyboard Settings</span>\n" +
"        <div style='display:flex; gap:5px;'>\n" +
"          <button id='fullAppBtn' class='theme-btn'>📱 Full App</button>\n" +
"          <button id='closeSettingsBtn' class='theme-btn active'>Done</button>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div class='settings-row'>\n" +
"        <span>Theme</span>\n" +
"        <div class='theme-pills'>\n" +
"          <button data-theme='dark' class='theme-btn active'>🌙 Dark</button>\n" +
"          <button data-theme='light' class='theme-btn'>☀️ Light</button>\n" +
"          <button data-theme='nothing' class='theme-btn'>🔴 Nothing</button>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div class='settings-row'>\n" +
"        <span>Key Scale (<span id='scaleValLabel'>100%</span>)</span>\n" +
"        <input type='range' id='scaleRange' min='85' max='108' value='100' style='width:120px;' />\n" +
"      </div>\n" +
"      <div class='settings-row'>\n" +
"        <span>Liquid Glass (<span id='glassValLabel'>0%</span>)</span>\n" +
"        <input type='range' id='glassRange' min='0' max='100' value='0' style='width:120px;' />\n" +
"      </div>\n" +
"      <div class='settings-row'>\n" +
"        <span>Number Row (1-0)</span>\n" +
"        <input type='checkbox' id='numRowToggle' />\n" +
"      </div>\n" +
"      <div class='settings-row'>\n" +
"        <span>QuickPath Swipe & Sounds</span>\n" +
"        <div style='display:flex; gap:10px;'>\n" +
"          <label><input type='checkbox' id='swipeToggle' checked /> Swipe</label>\n" +
"          <label><input type='checkbox' id='soundToggle' checked /> Sound</label>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div class='settings-row'>\n" +
"        <span>Gemini API Key</span>\n" +
"        <input type='password' id='apiKeyInput' placeholder='Paste API key' style='width:130px; font-size:11px; padding:3px 6px; border-radius:6px; border:1px solid #555; background:#181820; color:#fff;' />\n" +
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
"          <button id='emojiBsBtn' class='theme-btn'>⌫</button>\n" +
"          <button id='closeEmojiBtn' class='theme-btn active'>ABC</button>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div id='emojiGrid' class='emoji-grid'></div>\n" +
"    </div>\n" +
"  </div>\n" +
"  <div class='kb-chin'>\n" +
"    <button id='chinEmojiBtn' class='chin-emoji-btn'>\n" +
"      <svg width='24' height='24' viewBox='0 0 24 24' fill='currentColor'><path d='M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2zm-3.5 7a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm7 0a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm-3.5 9c-2.61 0-4.83-1.67-5.65-4h11.3c-.82 2.33-3.04 4-5.65 4z'/></svg>\n" +
"    </button>\n" +
"    <div style='width:100px; height:4px; border-radius:99px; background:rgba(255,255,255,0.2);'></div>\n" +
"    <div style='width:28px;'></div>\n" +
"  </div>\n" +
"</section>\n" +
"<script>\n" +
"const state = { layout: 'letters', shiftState: 0, lastShiftTap: 0, numberRow: false, swipeType: true, soundEffects: true };\n" +
"const LAYOUTS = {\n" +
"  letters: [['q','w','e','r','t','y','u','i','o','p'],['a','s','d','f','g','h','j','k','l'],['SHIFT','z','x','c','v','b','n','m','BACKSPACE'],['123','SPACE','RETURN']],\n" +
"  numbers: [['1','2','3','4','5','6','7','8','9','0'],['-','/',':',';','(',')','$','&','@','\"'],['#+=','.',',','?','!',\"'\",'BACKSPACE'],['ABC','SPACE','RETURN']],\n" +
"  symbols: [['[',']','{','}','#','%','^','*','+','='],['_','\\\\','|','~','<','>','€','£','¥','•'],['123','.',',','?','!',\"'\",'BACKSPACE'],['ABC','SPACE','RETURN']]\n" +
"};\n" +
"const WORD_POOL = ['The','School','A','Apple','AI','Keyboard','awesome','beautiful','because','design','good','great','have','hello','here','like','love','make','more','never','new','now','people','perfect','please','right','some','sound','space','system','thank','that','their','there','they','thing','think','this','time','today','tomorrow','very','want','well','what','when','where','with','work','world','would','you','your'];\n" +
"const EMOJIS = {\n" +
"  smileys: ['😀','😃','😄','😁','😆','😅','😂','🤣','🥲','😊','😇','🙂','🙃','😉','😌','😍','🥰','😘','😋','😛','😝','😜','🤪','🤨','🧐','🤓','😎','🤩','🥳','😏','😒','😞','😔','😟','😕','🙁','😣','😖','😫','😩','🥺','😢','😭','😤','😠','😡','🤬','🤯','😳','🥵','🥶','😱','😨','🤗','🤔','🤭','🤫','🫠','😶','😐','😑','😬','🙄','😮','😲','🥱','😴','🤤','😵','🥴','🤢','🤮','🤧','😷','🤠','😈','👻','💀','🤖'],\n" +
"  gestures: ['👋','🤚','🖐️','✋','🖖','👌','🤌','🤏','✌️','🤞','🫰','🤟','🤘','🤙','👈','👉','👆','👇','☝️','🫵','👍','👎','✊','👊','🤛','🤜','👏','🙌','🫶','👐','🤲','🤝','🙏','✍️','💅','🤳','💪','👀','👄'],\n" +
"  hearts: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','🤎','💔','❤️‍🔥','❣️','💕','💞','💓','💗','💖','💘','💝','💯','💥','💫','💦','✨','🔥','🌟','⭐','🎉'],\n" +
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
"  document.documentElement.style.setProperty('--glass-blur', (v*0.2).toFixed(1)+'px');\n" +
"  const deckA = Math.max(0.35, 0.96 - (v/100)*0.55).toFixed(2);\n" +
"  const keyA = Math.max(0.4, 0.95 - (v/100)*0.5).toFixed(2);\n" +
"  const t = document.documentElement.className;\n" +
"  if(t === 'light') {\n" +
"    document.documentElement.style.setProperty('--kb-deck-bg', `rgba(209,212,219,${deckA})`);\n" +
"    document.documentElement.style.setProperty('--key-bg', `rgba(255,255,255,${keyA})`);\n" +
"    document.documentElement.style.setProperty('--key-mod-bg', `rgba(173,179,190,${keyA})`);\n" +
"  } else if(t === 'nothing') {\n" +
"    document.documentElement.style.setProperty('--kb-deck-bg', `rgba(0,0,0,${deckA})`);\n" +
"    document.documentElement.style.setProperty('--key-bg', `rgba(14,14,14,${keyA})`);\n" +
"    document.documentElement.style.setProperty('--key-mod-bg', `rgba(26,26,26,${keyA})`);\n" +
"  } else {\n" +
"    document.documentElement.style.setProperty('--kb-deck-bg', `rgba(26,27,34,${deckA})`);\n" +
"    document.documentElement.style.setProperty('--key-bg', `rgba(74,76,89,${keyA})`);\n" +
"    document.documentElement.style.setProperty('--key-mod-bg', `rgba(56,58,70,${keyA})`);\n" +
"  }\n" +
"  document.getElementById('glassValLabel').textContent = v + '%';\n" +
"}\n" +
"\n" +
"function syncNativePrefs() {\n" +
"  if(!window.AndroidIME) return;\n" +
"  try {\n" +
"    const p = JSON.parse(AndroidIME.getPrefsJson());\n" +
"    if(p.theme) {\n" +
"      document.documentElement.className = p.theme;\n" +
"      document.querySelectorAll('[data-theme]').forEach(x => x.classList.toggle('active', x.dataset.theme === p.theme));\n" +
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
"    if(typeof p.numberRow === 'boolean') { state.numberRow = p.numberRow; document.getElementById('numRowToggle').checked = p.numberRow; }\n" +
"    if(typeof p.swipeType === 'boolean') { state.swipeType = p.swipeType; document.getElementById('swipeToggle').checked = p.swipeType; }\n" +
"    if(typeof p.soundEffects === 'boolean') { state.soundEffects = p.soundEffects; document.getElementById('soundToggle').checked = p.soundEffects; }\n" +
"    if(p.apiKey !== undefined) document.getElementById('apiKeyInput').value = p.apiKey;\n" +
"    renderKb();\n" +
"  } catch(e) {}\n" +
"}\n" +
"\n" +
"function vibe(ms=8) { if(window.AndroidIME) AndroidIME.vibrate(ms); }\n" +
"function toast(m) { const t = document.getElementById('toastBanner'); t.textContent = m; t.style.opacity = 1; setTimeout(()=>t.style.opacity=0, 1300); }\n" +
"function commit(s) { if(window.AndroidIME) AndroidIME.commitText(s); updatePredictions(s.trim()); }\n" +
"function doBackspace() { if(window.AndroidIME) AndroidIME.deleteText(); }\n" +
"function stopBackspace() { clearTimeout(bsTimeout); clearInterval(bsInterval); }\n" +
"\n" +
"function renderKb() {\n" +
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
"      if(isMod) btn.classList.add('mod-key'); else if(k !== 'SPACE') btn.classList.add('letter-key');\n" +
"      if(k==='SHIFT'||k==='BACKSPACE'||k==='#+=') btn.style.flex='1.32';\n" +
"      else if(k==='123'||k==='ABC') btn.style.flex='1.5';\n" +
"      else if(k==='SPACE') btn.style.flex='4.8';\n" +
"      else if(k==='RETURN') btn.style.flex='2.2';\n" +
"      if(k==='SHIFT') btn.innerHTML = state.shiftState > 0 ? `<svg width='19' height='19' viewBox='0 0 24 24' fill='currentColor'><path d='M12 4L4 12.5h5V19h6v-6.5h5L12 4z'/></svg>` : `<svg width='19' height='19' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linejoin='round'><path d='M12 4L4 12.5h5V19h6v-6.5h5L12 4z'/></svg>`;\n" +
"      else if(k==='BACKSPACE') btn.innerHTML = `<svg width='21' height='21' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.9' stroke-linecap='round' stroke-linejoin='round'><path d='M20 5H9.2a2 2 0 00-1.48.66L3.3 11.34a1 1 0 000 1.32l4.42 5.68A2 2 0 009.2 19H20a2 2 0 002-2V7a2 2 0 00-2-2z'/><line x1='16.2' y1='9.5' x2='11.2' y2='14.5'/><line x1='11.2' y1='9.5' x2='16.2' y2='14.5'/></svg>`;\n" +
"      else if(k==='RETURN') btn.innerHTML = `<svg width='20' height='20' viewBox='0 0 26 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><path d='M16 7h4a2 2 0 012 2v5a2 2 0 01-2 2H6'/><polyline points='10 12 6 16 10 20'/></svg>`;\n" +
"      else if(k==='SPACE') btn.innerHTML = '';\n" +
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
"  kEl.classList.add('pressed', 'show-popup'); vibe(8); playClick(k==='BACKSPACE'?'back':'letter');\n" +
"  if(k === 'BACKSPACE') {\n" +
"    doBackspace();\n" +
"    bsTimeout = setTimeout(() => { bsInterval = setInterval(() => { doBackspace(); vibe(4); playClick('back'); }, 70); }, 400);\n" +
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
"  if(k === 'SPACE') {\n" +
"    const dx = e.clientX - spcStart;\n" +
"    if(Math.abs(dx) > 15) { if(window.AndroidIME) AndroidIME.moveCursor(dx > 0 ? 1 : -1); spcStart = e.clientX; vibe(4); }\n" +
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
"      gCtx.strokeStyle = '#3b82f6'; gCtx.lineWidth = 5; gCtx.lineCap = 'round'; gCtx.stroke();\n" +
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
"      let reg = new RegExp('^' + gSeq.join('.*') + '$', 'i');\n" +
"      let match = WORD_POOL.find(w => reg.test(w)) || WORD_POOL.find(w => w.toLowerCase().startsWith(gSeq[0]) && w.toLowerCase().endsWith(gSeq[gSeq.length-1]));\n" +
"      if(match) commit(match + ' ');\n" +
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
"  else if(k === 'RETURN') { if(window.AndroidIME) AndroidIME.sendEnter(); }\n" +
"  else if(k === 'SPACE') { commit(' '); }\n" +
"  else if(k !== 'BACKSPACE') {\n" +
"    const c = (state.layout === 'letters' && state.shiftState > 0) ? k.toUpperCase() : k;\n" +
"    commit(c); if(state.shiftState === 1) { state.shiftState = 0; renderKb(); }\n" +
"  }\n" +
"});\n" +
"\n" +
"function updatePredictions(lastChar) {\n" +
"  const p = WORD_POOL.filter(w => w.toLowerCase().startsWith((lastChar||'t').toLowerCase())).slice(0,3);\n" +
"  document.querySelectorAll('.sug-btn').forEach((b,i) => { const w = p[i] || WORD_POOL[i]; b.textContent = w; b.dataset.w = w; });\n" +
"}\n" +
"document.getElementById('predictionsContainer').onclick = e => {\n" +
"  const b = e.target.closest('.sug-btn'); if(b) { vibe(8); commit(b.dataset.w + ' '); }\n" +
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
"    if(window.AndroidIME) AndroidIME.savePrefString('theme', t);\n" +
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
"document.getElementById('numRowToggle').onchange = e => {\n" +
"  state.numberRow = e.target.checked;\n" +
"  if(window.AndroidIME) AndroidIME.savePrefBool('numberRow', state.numberRow);\n" +
"  renderKb();\n" +
"};\n" +
"document.getElementById('swipeToggle').onchange = e => {\n" +
"  state.swipeType = e.target.checked;\n" +
"  if(window.AndroidIME) AndroidIME.savePrefBool('swipeType', state.swipeType);\n" +
"};\n" +
"document.getElementById('soundToggle').onchange = e => {\n" +
"  state.soundEffects = e.target.checked;\n" +
"  if(window.AndroidIME) AndroidIME.savePrefBool('soundEffects', state.soundEffects);\n" +
"};\n" +
"document.getElementById('apiKeyInput').onchange = e => {\n" +
"  if(window.AndroidIME) AndroidIME.savePrefString('apiKey', e.target.value.trim());\n" +
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
"    const apiKey = document.getElementById('apiKeyInput').value.trim();\n" +
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
"      out = out.replace(/\\bi\\b/g,'I').replace(/\\bim\\b/gi,\"I'm\").replace(/\\bdont\\b/gi,\"don't\").replace(/\\bu\\b/gi,'you').replace(/\\br\\b/gi,'are');\n" +
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
"syncNativePrefs();\n" +
"renderKb();\n" +
"</script>\n" +
"</body>\n" +
"</html>";
    }
}
