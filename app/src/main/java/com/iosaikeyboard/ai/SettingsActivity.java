package com.iosaikeyboard.ai;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private WebView webView;
    private SharedPreferences prefs;

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("KeyboardPrefs", MODE_PRIVATE);

        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(Color.parseColor("#000000"));
            window.setNavigationBarColor(Color.parseColor("#000000"));
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#000000"));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new SettingsBridge(this), "AndroidApp");

        webView.loadDataWithBaseURL("https://ioskeyboard.local/", getSettingsHtml(), "text/html", "UTF-8", null);
        setContentView(webView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.post(() -> webView.evaluateJavascript("if(window.refreshAll) refreshAll();", null));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && webView != null) {
            webView.post(() -> webView.evaluateJavascript("if(window.refreshAll) refreshAll();", null));
        }
    }

    private void notifyKeyboardChanged() {
        try {
            Intent intent = new Intent("com.iosaikeyboard.ai.SETTINGS_CHANGED");
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    public class SettingsBridge {
        Context mContext;

        SettingsBridge(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void enableKeyboard() {
            try {
                Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void selectKeyboard() {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
            } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public String getImeStatus() {
            try {
                boolean enabled = false;
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    List<InputMethodInfo> list = imm.getEnabledInputMethodList();
                    for (InputMethodInfo info : list) {
                        if (info.getPackageName().equals(getPackageName())) {
                            enabled = true;
                            break;
                        }
                    }
                }
                String defaultIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
                boolean selected = defaultIme != null && defaultIme.contains(getPackageName());
                JSONObject o = new JSONObject();
                o.put("enabled", enabled);
                o.put("selected", selected);
                return o.toString();
            } catch (Exception e) {
                return "{\"enabled\":false,\"selected\":false}";
            }
        }

        @JavascriptInterface
        public String getPrefsJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("theme", prefs.getString("theme", "dark"));
                obj.put("height", prefs.getInt("height", 220));
                obj.put("width", prefs.getInt("width", 100));
                obj.put("scale", prefs.getInt("scale", 100));
                obj.put("numScale", prefs.getInt("numScale", 85));
                obj.put("radius", prefs.getInt("radius", 9));
                obj.put("gap", prefs.getInt("gap", 5));
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
            notifyKeyboardChanged();
        }

        @JavascriptInterface
        public void savePrefInt(String key, int val) {
            prefs.edit().putInt(key, val).apply();
            notifyKeyboardChanged();
        }

        @JavascriptInterface
        public void savePrefBool(String key, boolean val) {
            prefs.edit().putBoolean(key, val).apply();
            notifyKeyboardChanged();
        }

        @JavascriptInterface
        public void resetDictionary() {
            prefs.edit().putString("learnedData", "{}").apply();
            notifyKeyboardChanged();
        }

        @JavascriptInterface
        public void vibrate(int ms) {
            if (!prefs.getBoolean("haptics", true)) return;
            try {
                Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(Math.max(1, ms), VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        v.vibrate(Math.max(1, ms));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private String getSettingsHtml() {
        return "<!DOCTYPE html>\n" +
"<html lang='en' class='dark'>\n" +
"<head>\n" +
"<meta charset='UTF-8' />\n" +
"<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no' />\n" +
"<style>\n" +
":root {\n" +
"  --ios-bg: #000000;\n" +
"  --ios-card: #1C1C1E;\n" +
"  --ios-card-active: #2C2C2E;\n" +
"  --ios-text: #FFFFFF;\n" +
"  --ios-sub: #8E8E93;\n" +
"  --ios-sep: rgba(84, 84, 88, 0.36);\n" +
"  --ios-blue: #007AFF;\n" +
"  --ios-green: #34C759;\n" +
"  --ios-red: #FF453A;\n" +
"  --seg-bg: rgba(118, 118, 128, 0.24);\n" +
"  --seg-pill: #636366;\n" +
"}\n" +
"html.light {\n" +
"  --ios-bg: #F2F2F7;\n" +
"  --ios-card: #FFFFFF;\n" +
"  --ios-card-active: #E5E5EA;\n" +
"  --ios-text: #000000;\n" +
"  --ios-sub: #6D6D72;\n" +
"  --ios-sep: rgba(60, 60, 67, 0.18);\n" +
"  --seg-bg: rgba(118, 118, 128, 0.12);\n" +
"  --seg-pill: #FFFFFF;\n" +
"}\n" +
"html.nothing {\n" +
"  --ios-bg: #000000;\n" +
"  --ios-card: #0D0D0D;\n" +
"  --ios-text: #FFFFFF;\n" +
"  --ios-sub: #999999;\n" +
"  --ios-sep: rgba(255, 255, 255, 0.22);\n" +
"  --ios-blue: #EA1D25;\n" +
"  --ios-green: #EA1D25;\n" +
"}\n" +
"* { box-sizing: border-box; -webkit-tap-highlight-color: transparent; user-select: none; }\n" +
"body {\n" +
"  margin: 0; padding: 0 0 40px 0;\n" +
"  font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'SF Pro Display', sans-serif;\n" +
"  background-color: var(--ios-bg); color: var(--ios-text);\n" +
"  overflow-x: hidden; overflow-y: auto;\n" +
"  transition: background-color 0.3s cubic-bezier(0.22, 1, 0.36, 1), color 0.2s ease;\n" +
"}\n" +
"/* Classic iOS Sticky Navigation Header */\n" +
".ios-navbar {\n" +
"  position: sticky; top: 0; z-index: 50;\n" +
"  background: rgba(28, 28, 30, 0.78);\n" +
"  backdrop-filter: blur(25px); -webkit-backdrop-filter: blur(25px);\n" +
"  border-bottom: 0.5px solid var(--ios-sep);\n" +
"  padding: 14px 18px 12px 18px;\n" +
"  display: flex; align-items: center; justify-content: space-between;\n" +
"}\n" +
"html.light .ios-navbar { background: rgba(242, 242, 247, 0.82); }\n" +
".nav-title { font-size: 20px; font-weight: 700; letter-spacing: -0.4px; display: flex; align-items: center; gap: 8px; }\n" +
".ios-badge { font-size: 10.5px; font-weight: 700; padding: 2px 7px; border-radius: 99px; background: var(--ios-blue); color: #fff; }\n" +
".container { max-width: 540px; margin: 0 auto; padding: 10px 16px; }\n" +
".sec-header { font-size: 12.5px; font-weight: 500; text-transform: uppercase; color: var(--ios-sub); padding: 16px 14px 6px 14px; letter-spacing: 0.2px; }\n" +
".sec-footer { font-size: 12px; color: var(--ios-sub); padding: 6px 14px 4px 14px; line-height: 1.4; }\n" +
".ios-group {\n" +
"  background-color: var(--ios-card);\n" +
"  border-radius: 12px;\n" +
"  overflow: hidden;\n" +
"  transition: background-color 0.25s ease;\n" +
"}\n" +
"html.nothing .ios-group { border: 1px solid var(--ios-sep); }\n" +
".ios-row {\n" +
"  display: flex; align-items: center; justify-content: space-between;\n" +
"  padding: 12px 16px; min-height: 48px;\n" +
"  position: relative;\n" +
"  transition: background-color 0.15s ease;\n" +
"}\n" +
".ios-row:not(:last-child)::after {\n" +
"  content: ''; position: absolute; bottom: 0; left: 16px; right: 0;\n" +
"  height: 0.5px; background-color: var(--ios-sep);\n" +
"}\n" +
".row-left { display: flex; flex-direction: column; gap: 2px; padding-right: 12px; }\n" +
".row-title { font-size: 16px; font-weight: 400; letter-spacing: -0.2px; }\n" +
".row-sub { font-size: 12px; color: var(--ios-sub); }\n" +
"/* Classic iOS UISwitch */\n" +
".ios-switch {\n" +
"  position: relative; width: 51px; height: 31px; border-radius: 31px;\n" +
"  background-color: rgba(120, 120, 128, 0.32);\n" +
"  cursor: pointer; flex-shrink: 0;\n" +
"  transition: background-color 0.25s cubic-bezier(0.22, 1, 0.36, 1);\n" +
"}\n" +
".ios-switch.active { background-color: var(--ios-green); }\n" +
".ios-switch-thumb {\n" +
"  position: absolute; top: 2px; left: 2px;\n" +
"  width: 27px; height: 27px; border-radius: 50%;\n" +
"  background-color: #FFFFFF;\n" +
"  box-shadow: 0 3px 8px rgba(0,0,0,0.22), 0 1px 1px rgba(0,0,0,0.16);\n" +
"  transition: transform 0.26s cubic-bezier(0.32, 0.72, 0, 1), width 0.15s ease;\n" +
"}\n" +
".ios-switch:active .ios-switch-thumb { width: 32px; }\n" +
".ios-switch.active:active .ios-switch-thumb { transform: translateX(15px); }\n" +
".ios-switch.active .ios-switch-thumb { transform: translateX(20px); }\n" +
"/* Classic iOS UISlider */\n" +
".slider-block { padding: 12px 16px 14px 16px; position: relative; }\n" +
".slider-block:not(:last-child)::after {\n" +
"  content: ''; position: absolute; bottom: 0; left: 16px; right: 0;\n" +
"  height: 0.5px; background-color: var(--ios-sep);\n" +
"}\n" +
".slider-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }\n" +
".slider-val { font-size: 14px; font-weight: 600; color: var(--ios-blue); font-variant-numeric: tabular-nums; }\n" +
".ios-slider {\n" +
"  -webkit-appearance: none; appearance: none;\n" +
"  width: 100%; height: 4px; border-radius: 99px;\n" +
"  background: linear-gradient(to right, var(--ios-blue) 0%, var(--ios-blue) var(--pct, 50%), rgba(120,120,128,0.3) var(--pct, 50%), rgba(120,120,128,0.3) 100%);\n" +
"  outline: none;\n" +
"}\n" +
".ios-slider::-webkit-slider-thumb {\n" +
"  -webkit-appearance: none; appearance: none;\n" +
"  width: 28px; height: 28px; border-radius: 50%;\n" +
"  background: #FFFFFF;\n" +
"  box-shadow: 0 3px 8px rgba(0,0,0,0.28), 0 0 1px rgba(0,0,0,0.2);\n" +
"  cursor: pointer;\n" +
"  transition: transform 0.14s cubic-bezier(0.2, 0.9, 0.3, 1.2);\n" +
"}\n" +
".ios-slider::-webkit-slider-thumb:active { transform: scale(1.14); }\n" +
"/* Classic iOS Segmented Control */\n" +
".ios-segmented {\n" +
"  display: flex; background-color: var(--seg-bg);\n" +
"  padding: 3px; border-radius: 9px; gap: 3px; width: 100%;\n" +
"}\n" +
".seg-item {\n" +
"  flex: 1; text-align: center; padding: 7px 6px;\n" +
"  font-size: 13px; font-weight: 600; border-radius: 7px;\n" +
"  color: var(--ios-text); cursor: pointer;\n" +
"  transition: all 0.22s cubic-bezier(0.32, 0.72, 0, 1);\n" +
"}\n" +
".seg-item.active {\n" +
"  background-color: var(--ios-blue); color: #FFFFFF;\n" +
"  box-shadow: 0 2px 6px rgba(0,0,0,0.22);\n" +
"}\n" +
"/* Test Input Box to Pop Up System Keyboard right inside Settings */\n" +
".live-test-input {\n" +
"  width: 100%; padding: 12px 14px; border-radius: 10px;\n" +
"  border: 1px solid var(--ios-sep); background: rgba(120,120,128,0.12);\n" +
"  color: var(--ios-text); font-size: 16px; outline: none;\n" +
"  transition: border-color 0.2s ease;\n" +
"}\n" +
".live-test-input:focus { border-color: var(--ios-blue); }\n" +
".ios-btn {\n" +
"  width: 100%; padding: 11px 14px; border-radius: 10px; border: none;\n" +
"  background-color: var(--ios-blue); color: #FFFFFF;\n" +
"  font-size: 15px; font-weight: 600; cursor: pointer;\n" +
"  transition: transform 0.12s ease, opacity 0.15s ease;\n" +
"}\n" +
".ios-btn:active { transform: scale(0.97); opacity: 0.88; }\n" +
".ios-btn.done { background-color: rgba(52, 199, 89, 0.18); color: #34C759; }\n" +
".word-chips { display: flex; flex-wrap: wrap; gap: 6px; padding-top: 6px; }\n" +
".word-chip {\n" +
"  font-size: 12px; padding: 4px 10px; border-radius: 99px;\n" +
"  background: rgba(0, 122, 255, 0.15); color: var(--ios-blue); font-weight: 500;\n" +
"}\n" +
".toast {\n" +
"  position: fixed; top: 14px; left: 50%; transform: translateX(-50%) translateY(-20px);\n" +
"  background: var(--ios-blue); color: #fff; padding: 6px 16px; border-radius: 99px;\n" +
"  font-size: 12.5px; font-weight: 600; opacity: 0; pointer-events: none; z-index: 100;\n" +
"  box-shadow: 0 6px 18px rgba(0,0,0,0.3);\n" +
"  transition: opacity 0.2s ease, transform 0.25s cubic-bezier(0.22, 1, 0.36, 1);\n" +
"}\n" +
".toast.show { opacity: 1; transform: translateX(-50%) translateY(0); }\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"  <div id='toast' class='toast'>Saved</div>\n" +
"  <nav class='ios-navbar'>\n" +
"    <div class='nav-title'><span>iOS Keyboard</span><span class='ios-badge'>AI Pro</span></div>\n" +
"    <span id='statusText' style='font-size:12.5px; font-weight:600; color:#34C759;'>Active</span>\n" +
"  </nav>\n" +
"\n" +
"  <div class='container'>\n" +
"    <!-- LIVE TEST FIELD (Keep keyboard open while adjusting sliders!) -->\n" +
"    <div class='sec-header'>LIVE KEYBOARD TESTER</div>\n" +
"    <div class='ios-group' style='padding: 12px;'>\n" +
"      <input id='testInput' type='text' class='live-test-input' placeholder='Tap here to open keyboard & test sliders live...' />\n" +
"    </div>\n" +
"\n" +
"    <!-- 1. SYSTEM ACTIVATION -->\n" +
"    <div class='sec-header'>1. SYSTEM KEYBOARD SETUP</div>\n" +
"    <div class='ios-group' style='padding: 12px; display:flex; gap:8px;'>\n" +
"      <button id='enableBtn' class='ios-btn'>1. Enable Keyboard</button>\n" +
"      <button id='selectBtn' class='ios-btn'>2. Switch Keyboard</button>\n" +
"    </div>\n" +
"\n" +
"    <!-- 2. APPEARANCE & THEME -->\n" +
"    <div class='sec-header'>2. APPEARANCE & THEME</div>\n" +
"    <div class='ios-group'>\n" +
"      <div style='padding: 12px 16px; border-bottom: 0.5px solid var(--ios-sep);'>\n" +
"        <div class='ios-segmented'>\n" +
"          <div data-theme='dark' class='seg-item active'>🌙 Dark</div>\n" +
"          <div data-theme='light' class='seg-item'>☀️ Light</div>\n" +
"          <div data-theme='nothing' class='seg-item'>🔴 Nothing</div>\n" +
"          <div data-theme='auto' class='seg-item'>⚡ Auto</div>\n" +
"        </div>\n" +
"      </div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Liquid Glass Blur</span><span id='glassVal' class='slider-val'>0%</span></div>\n" +
"        <input id='glassSlider' type='range' min='0' max='100' value='0' class='ios-slider' />\n" +
"      </div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Keyboard Height</span><span id='heightVal' class='slider-val'>220px</span></div>\n" +
"        <input id='heightSlider' type='range' min='180' max='290' value='220' class='ios-slider' />\n" +
"      </div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Keyboard Width</span><span id='widthVal' class='slider-val'>100%</span></div>\n" +
"        <input id='widthSlider' type='range' min='82' max='100' value='100' class='ios-slider' />\n" +
"      </div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Key Size Scale</span><span id='scaleVal' class='slider-val'>100%</span></div>\n" +
"        <input id='scaleSlider' type='range' min='85' max='110' value='100' class='ios-slider' />\n" +
"      </div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Number Row Height</span><span id='numScaleVal' class='slider-val'>85%</span></div>\n" +
"        <input id='numScaleSlider' type='range' min='65' max='115' value='85' class='ios-slider' />\n" +
"      </div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Key Corner Radius</span><span id='radiusVal' class='slider-val'>9px</span></div>\n" +
"        <input id='radiusSlider' type='range' min='4' max='18' value='9' class='ios-slider' />\n" +
"      </div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Key Spacing Gap</span><span id='gapVal' class='slider-val'>5px</span></div>\n" +
"        <input id='gapSlider' type='range' min='3' max='10' value='5' class='ios-slider' />\n" +
"      </div>\n" +
"    </div>\n" +
"\n" +
"    <!-- 3. TYPING & KEYBOARD CUSTOMIZATION -->\n" +
"    <div class='sec-header'>3. TYPING & GESTURES</div>\n" +
"    <div class='ios-group'>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Dedicated Number Row (1-0)</span><span class='row-sub'>Always show numbers above QWERTY</span></div><div data-tog='numberRow' class='ios-switch'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Slide to Type (QuickPath)</span><span class='row-sub'>Smooth trajectory glide typing</span></div><div data-tog='swipeType' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Character Preview Popups</span><span class='row-sub'>Show iOS callout bubble on key press</span></div><div data-tog='keyPopups' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Auto-Capitalization</span><span class='row-sub'>Capitalize new sentences automatically</span></div><div data-tog='autoCap' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>“.” Shortcut</span><span class='row-sub'>Double-tap spacebar inserts a period</span></div><div data-tog='doubleSpacePeriod' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Spacebar Cursor Trackpad</span><span class='row-sub'>Slide across spacebar to move cursor</span></div><div data-tog='spaceTrackpad' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div style='padding: 12px 16px;'>\n" +
"        <div class='row-title' style='margin-bottom:8px;'>One-Handed Mode</div>\n" +
"        <div class='ios-segmented'>\n" +
"          <div data-hand='left' class='seg-item'>⬅️ Left</div>\n" +
"          <div data-hand='off' class='seg-item active'>Centered</div>\n" +
"          <div data-hand='right' class='seg-item'>Right ➡️</div>\n" +
"        </div>\n" +
"      </div>\n" +
"    </div>\n" +
"\n" +
"    <!-- 4. SOUNDS & HAPTICS -->\n" +
"    <div class='sec-header'>4. SOUNDS & HAPTICS</div>\n" +
"    <div class='ios-group'>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Key Click Sounds</span><span class='row-sub'>Synthesized iOS acoustic ticks</span></div><div data-tog='soundEffects' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Haptic Vibration</span><span class='row-sub'>Tactile response on key touch</span></div><div data-tog='haptics' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div class='slider-block'>\n" +
"        <div class='slider-head'><span class='row-title'>Vibration Strength</span><span id='vibeVal' class='slider-val'>10ms</span></div>\n" +
"        <input id='vibeSlider' type='range' min='2' max='35' value='10' class='ios-slider' />\n" +
"      </div>\n" +
"    </div>\n" +
"\n" +
"    <!-- 5. SELF-LEARNING AI DICTIONARY & GEMINI -->\n" +
"    <div class='sec-header'>5. AUTOMATIC LEARNING AI DICTIONARY</div>\n" +
"    <div class='ios-group'>\n" +
"      <div class='ios-row'><div class='row-left'><span class='row-title'>Auto-Learn Typed Words</span><span class='row-sub'>Learns your vocabulary & next-word habits</span></div><div data-tog='aiLearn' class='ios-switch active'><div class='ios-switch-thumb'></div></div></div>\n" +
"      <div style='padding: 12px 16px; border-top: 0.5px solid var(--ios-sep);'>\n" +
"        <div style='display:flex; justify-content:space-between; align-items:center;'>\n" +
"          <span class='row-sub' id='learnedCountLabel'>0 Personal Words Learned</span>\n" +
"          <span id='clearDictBtn' style='font-size:13px; color:var(--ios-red); font-weight:600; cursor:pointer;'>Reset Dictionary</span>\n" +
"        </div>\n" +
"        <div id='learnedWordsBox' class='word-chips'></div>\n" +
"      </div>\n" +
"      <div style='padding: 12px 16px; border-top: 0.5px solid var(--ios-sep);'>\n" +
"        <div class='row-title' style='margin-bottom:6px;'>Google Gemini API Key</div>\n" +
"        <input id='apiKeyInput' type='password' class='live-test-input' placeholder='Paste Gemini API Key (optional)...' style='font-size:14px; padding:10px 12px;' />\n" +
"      </div>\n" +
"    </div>\n" +
"    <div class='sec-footer'>Changes apply immediately in real-time to the active keyboard below.</div>\n" +
"  </div>\n" +
"\n" +
"<script>\n" +
"const root = document.documentElement;\n" +
"function vibe(ms=8) { if(window.AndroidApp) AndroidApp.vibrate(ms); }\n" +
"function showToast(msg) {\n" +
"  const t = document.getElementById('toast');\n" +
"  t.textContent = msg; t.classList.add('show');\n" +
"  clearTimeout(t._tm); t._tm = setTimeout(() => t.classList.remove('show'), 1200);\n" +
"}\n" +
"function updateSliderFill(el) {\n" +
"  const pct = ((el.value - el.min) / (el.max - el.min)) * 100;\n" +
"  el.style.setProperty('--pct', pct + '%');\n" +
"}\n" +
"\n" +
"function setThemeUI(mode, save=false) {\n" +
"  const eff = mode === 'auto' ? (window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : mode;\n" +
"  root.className = eff;\n" +
"  document.querySelectorAll('[data-theme]').forEach(el => el.classList.toggle('active', el.dataset.theme === mode));\n" +
"  if(save && window.AndroidApp) {\n" +
"    AndroidApp.savePrefString('theme', mode);\n" +
"    showToast('Theme: ' + mode.toUpperCase());\n" +
"  }\n" +
"}\n" +
"\n" +
"document.querySelectorAll('[data-theme]').forEach(el => {\n" +
"  el.onclick = () => { vibe(8); setThemeUI(el.dataset.theme, true); };\n" +
"});\n" +
"\n" +
"document.querySelectorAll('[data-hand]').forEach(el => {\n" +
"  el.onclick = () => {\n" +
"    vibe(8);\n" +
"    document.querySelectorAll('[data-hand]').forEach(x => x.classList.toggle('active', x === el));\n" +
"    if(window.AndroidApp) AndroidApp.savePrefString('oneHanded', el.dataset.hand);\n" +
"  };\n" +
"});\n" +
"\n" +
"function bindSlider(sliderId, valId, prefKey, suffix) {\n" +
"  const s = document.getElementById(sliderId), v = document.getElementById(valId);\n" +
"  s.addEventListener('input', () => {\n" +
"    const num = parseInt(s.value, 10);\n" +
"    v.textContent = num + suffix;\n" +
"    updateSliderFill(s);\n" +
"    if(window.AndroidApp) AndroidApp.savePrefInt(prefKey, num);\n" +
"  });\n" +
"}\n" +
"bindSlider('glassSlider', 'glassVal', 'glass', '%');\n" +
"bindSlider('heightSlider', 'heightVal', 'height', 'px');\n" +
"bindSlider('widthSlider', 'widthVal', 'width', '%');\n" +
"bindSlider('scaleSlider', 'scaleVal', 'scale', '%');\n" +
"bindSlider('numScaleSlider', 'numScaleVal', 'numScale', '%');\n" +
"bindSlider('radiusSlider', 'radiusVal', 'radius', 'px');\n" +
"bindSlider('gapSlider', 'gapVal', 'gap', 'px');\n" +
"bindSlider('vibeSlider', 'vibeVal', 'vibeStrength', 'ms');\n" +
"\n" +
"document.querySelectorAll('.ios-switch').forEach(sw => {\n" +
"  sw.onclick = () => {\n" +
"    const key = sw.dataset.tog;\n" +
"    const next = !sw.classList.contains('active');\n" +
"    sw.classList.toggle('active', next);\n" +
"    vibe(10);\n" +
"    if(window.AndroidApp) AndroidApp.savePrefBool(key, next);\n" +
"  };\n" +
"});\n" +
"\n" +
"document.getElementById('apiKeyInput').addEventListener('change', e => {\n" +
"  if(window.AndroidApp) { AndroidApp.savePrefString('apiKey', e.target.value.trim()); showToast('API Key Saved'); }\n" +
"});\n" +
"\n" +
"document.getElementById('clearDictBtn').onclick = () => {\n" +
"  vibe(12);\n" +
"  if(window.AndroidApp) AndroidApp.resetDictionary();\n" +
"  renderLearnedWords('{}');\n" +
"  showToast('AI Dictionary Reset');\n" +
"};\n" +
"\n" +
"function renderLearnedWords(jsonStr) {\n" +
"  const box = document.getElementById('learnedWordsBox');\n" +
"  const lbl = document.getElementById('learnedCountLabel');\n" +
"  box.innerHTML = '';\n" +
"  try {\n" +
"    const data = JSON.parse(jsonStr || '{}');\n" +
"    const words = data.words || {};\n" +
"    const sorted = Object.keys(words).sort((a,b) => words[b] - words[a]);\n" +
"    lbl.textContent = sorted.length + ' Personal Words Learned';\n" +
"    sorted.slice(0, 18).forEach(w => {\n" +
"      const sp = document.createElement('span');\n" +
"      sp.className = 'word-chip';\n" +
"      sp.textContent = w + ' (' + words[w] + ')';\n" +
"      box.appendChild(sp);\n" +
"    });\n" +
"  } catch(e) { lbl.textContent = '0 Personal Words Learned'; }\n" +
"}\n" +
"\n" +
"function refreshAll() {\n" +
"  if(!window.AndroidApp) return;\n" +
"  try {\n" +
"    const st = JSON.parse(AndroidApp.getImeStatus());\n" +
"    const b1 = document.getElementById('enableBtn'), b2 = document.getElementById('selectBtn'), stTxt = document.getElementById('statusText');\n" +
"    b1.textContent = st.enabled ? '✓ 1. Enabled' : '1. Enable Keyboard';\n" +
"    b1.className = st.enabled ? 'ios-btn done' : 'ios-btn';\n" +
"    b2.textContent = st.selected ? '✓ 2. Default Active' : '2. Switch Keyboard';\n" +
"    b2.className = st.selected ? 'ios-btn done' : 'ios-btn';\n" +
"    stTxt.textContent = st.selected ? '● Active' : '● Setup Needed';\n" +
"    stTxt.style.color = st.selected ? '#34C759' : '#FF9F0A';\n" +
"\n" +
"    const p = JSON.parse(AndroidApp.getPrefsJson());\n" +
"    setThemeUI(p.theme || 'dark', false);\n" +
"    const map = [\n" +
"      ['glassSlider','glassVal',p.glass,0,'%'],\n" +
"      ['heightSlider','heightVal',p.height,220,'px'],\n" +
"      ['widthSlider','widthVal',p.width,100,'%'],\n" +
"      ['scaleSlider','scaleVal',p.scale,100,'%'],\n" +
"      ['numScaleSlider','numScaleVal',p.numScale,85,'%'],\n" +
"      ['radiusSlider','radiusVal',p.radius,9,'px'],\n" +
"      ['gapSlider','gapVal',p.gap,5,'px'],\n" +
"      ['vibeSlider','vibeVal',p.vibeStrength,10,'ms']\n" +
"    ];\n" +
"    map.forEach(([sId, vId, val, def, suf]) => {\n" +
"      const s = document.getElementById(sId), v = document.getElementById(vId);\n" +
"      const finalVal = (typeof val === 'number') ? val : def;\n" +
"      s.value = finalVal; v.textContent = finalVal + suf; updateSliderFill(s);\n" +
"    });\n" +
"    ['numberRow','swipeType','soundEffects','haptics','spaceTrackpad','keyPopups','autoCap','doubleSpacePeriod','aiLearn'].forEach(k => {\n" +
"      const el = document.querySelector(`[data-tog='${k}']`);\n" +
"      if(el && typeof p[k] === 'boolean') el.classList.toggle('active', p[k]);\n" +
"    });\n" +
"    document.querySelectorAll('[data-hand]').forEach(x => x.classList.toggle('active', x.dataset.hand === (p.oneHanded || 'off')));\n" +
"    if(p.apiKey !== undefined) document.getElementById('apiKeyInput').value = p.apiKey;\n" +
"    renderLearnedWords(p.learnedData || '{}');\n" +
"  } catch(e) {}\n" +
"}\n" +
"\n" +
"document.getElementById('enableBtn').onclick = () => { vibe(8); if(window.AndroidApp) AndroidApp.enableKeyboard(); };\n" +
"document.getElementById('selectBtn').onclick = () => { vibe(8); if(window.AndroidApp) AndroidApp.selectKeyboard(); };\n" +
"refreshAll();\n" +
"</script>\n" +
"</body>\n" +
"</html>";
    }
}
