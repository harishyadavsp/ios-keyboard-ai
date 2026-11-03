package com.iosaikeyboard.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("KeyboardPrefs", MODE_PRIVATE);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0B0B0E"));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(32), pad, pad);

        // App Title Header
        TextView title = new TextView(this);
        title.setText("⌨️ IOS Keyboard AI");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("iOS 18 Liquid Glass & Nothing OS Edition");
        subtitle.setTextColor(Color.parseColor("#8E8E99"));
        subtitle.setTextSize(13);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        root.addView(subtitle);

        // 1. QUICK SETUP CARD
        LinearLayout setupCard = createCard();
        setupCard.addView(createSectionHeader("1. Enable & Select Keyboard"));

        Button enableBtn = createStyledButton("Step 1: Enable in System Settings", "#2B2B36");
        enableBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });
        setupCard.addView(enableBtn);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(10)));
        setupCard.addView(spacer);

        Button switchBtn = createStyledButton("Step 2: Switch Active Keyboard", "#3B82F6");
        switchBtn.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        setupCard.addView(switchBtn);
        root.addView(setupCard);

        // 2. THEME & APPEARANCE CARD
        LinearLayout themeCard = createCard();
        themeCard.addView(createSectionHeader("2. Keyboard Theme"));

        LinearLayout themeRow = new LinearLayout(this);
        themeRow.setOrientation(LinearLayout.HORIZONTAL);

        String currentTheme = prefs.getString("theme", "dark");
        Button darkBtn = createThemeChoice("🌙 Dark", "dark", currentTheme);
        Button lightBtn = createThemeChoice("☀️ Light", "light", currentTheme);
        Button nothingBtn = createThemeChoice("🔴 Nothing", "nothing", currentTheme);

        View.OnClickListener themeClick = v -> {
            String chosen = (String) v.getTag();
            prefs.edit().putString("theme", chosen).apply();
            updateThemeButtons(darkBtn, lightBtn, nothingBtn, chosen);
            Toast.makeText(this, "Theme saved: " + chosen.toUpperCase(), Toast.LENGTH_SHORT).show();
        };
        darkBtn.setOnClickListener(themeClick);
        lightBtn.setOnClickListener(themeClick);
        nothingBtn.setOnClickListener(themeClick);

        themeRow.addView(darkBtn);
        themeRow.addView(lightBtn);
        themeRow.addView(nothingBtn);
        themeCard.addView(themeRow);

        // Glassmorphism Blur Slider
        int glassVal = prefs.getInt("glass", 0);
        TextView glassLabel = new TextView(this);
        glassLabel.setText("Liquid Glass Effect: " + glassVal + "%");
        glassLabel.setTextColor(Color.WHITE);
        glassLabel.setTextSize(13);
        glassLabel.setPadding(0, dp(16), 0, dp(6));
        themeCard.addView(glassLabel);

        SeekBar glassSlider = new SeekBar(this);
        glassSlider.setMax(100);
        glassSlider.setProgress(glassVal);
        glassSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                glassLabel.setText("Liquid Glass Effect: " + progress + "%");
                prefs.edit().putInt("glass", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        themeCard.addView(glassSlider);

        // Key Scale Slider
        int scaleVal = prefs.getInt("scale", 100);
        TextView scaleLabel = new TextView(this);
        scaleLabel.setText("Key Size Scale: " + scaleVal + "%");
        scaleLabel.setTextColor(Color.WHITE);
        scaleLabel.setTextSize(13);
        scaleLabel.setPadding(0, dp(14), 0, dp(6));
        themeCard.addView(scaleLabel);

        SeekBar scaleSlider = new SeekBar(this);
        scaleSlider.setMax(108);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            scaleSlider.setMin(85);
        }
        scaleSlider.setProgress(scaleVal);
        scaleSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int p = Math.max(85, progress);
                scaleLabel.setText("Key Size Scale: " + p + "%");
                prefs.edit().putInt("scale", p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        themeCard.addView(scaleSlider);

        root.addView(themeCard);

        // 3. TYPING & AI FEATURES CARD
        LinearLayout featCard = createCard();
        featCard.addView(createSectionHeader("3. Typing & Gemini AI"));

        featCard.addView(createToggleRow("Dedicated Number Row (1-0)", "numberRow", true));
        featCard.addView(createToggleRow("QuickPath Swipe-to-Type", "swipeType", true));
        featCard.addView(createToggleRow("Key Click Sounds", "soundEffects", true));
        featCard.addView(createToggleRow("Haptic Vibration", "haptics", true));

        TextView apiTitle = new TextView(this);
        apiTitle.setText("Google Gemini API Key (For Live AI Tools):");
        apiTitle.setTextColor(Color.parseColor("#8E8E99"));
        apiTitle.setTextSize(12);
        apiTitle.setPadding(0, dp(14), 0, dp(6));
        featCard.addView(apiTitle);

        EditText apiInput = new EditText(this);
        apiInput.setHint("Paste your Gemini API Key here...");
        apiInput.setHintTextColor(Color.parseColor("#666677"));
        apiInput.setTextColor(Color.WHITE);
        apiInput.setTextSize(13);
        apiInput.setSingleLine(true);
        apiInput.setText(prefs.getString("apiKey", ""));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#0B0B0E"));
        inputBg.setCornerRadius(dp(10));
        inputBg.setStroke(dp(1), Color.parseColor("#2E2E3A"));
        apiInput.setBackground(inputBg);
        apiInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        apiInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString("apiKey", s.toString().trim()).apply();
            }
        });
        featCard.addView(apiInput);
        root.addView(featCard);

        // 4. LIVE TEST SANDBOX CARD
        LinearLayout testCard = createCard();
        testCard.addView(createSectionHeader("4. Test Your Keyboard Here"));

        EditText testBox = new EditText(this);
        testBox.setHint("Tap here to pop up IOS Keyboard AI and test swiping, themes & AI...");
        testBox.setHintTextColor(Color.parseColor("#666677"));
        testBox.setTextColor(Color.WHITE);
        testBox.setTextSize(15);
        testBox.setMinLines(3);
        testBox.setGravity(Gravity.TOP | Gravity.START);
        GradientDrawable testBg = new GradientDrawable();
        testBg.setColor(Color.parseColor("#0B0B0E"));
        testBg.setCornerRadius(dp(12));
        testBg.setStroke(dp(1), Color.parseColor("#3B82F6"));
        testBox.setBackground(testBg);
        testBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        testCard.addView(testBox);
        root.addView(testCard);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#16161D"));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.parseColor("#262632"));
        card.setBackground(bg);
        int p = dp(16);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(16));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView createSectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#3B82F6"));
        tv.setTextSize(14);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 0, 0, dp(12));
        return tv;
    }

    private Button createStyledButton(String text, String hexColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setAllCaps(false);
        btn.setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(hexColor));
        bg.setCornerRadius(dp(12));
        btn.setBackground(bg);
        btn.setPadding(dp(12), dp(12), dp(12), dp(12));
        return btn;
    }

    private Button createThemeChoice(String label, String tag, String current) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTag(tag);
        btn.setAllCaps(false);
        btn.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1.0f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        btn.setLayoutParams(lp);
        styleThemeButton(btn, tag.equals(current));
        return btn;
    }

    private void updateThemeButtons(Button b1, Button b2, Button b3, String activeTag) {
        styleThemeButton(b1, "dark".equals(activeTag));
        styleThemeButton(b2, "light".equals(activeTag));
        styleThemeButton(b3, "nothing".equals(activeTag));
    }

    private void styleThemeButton(Button btn, boolean active) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        if (active) {
            bg.setColor(Color.parseColor("#3B82F6"));
            btn.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#22222C"));
            bg.setStroke(dp(1), Color.parseColor("#343444"));
            btn.setTextColor(Color.parseColor("#CCCCDD"));
        }
        btn.setBackground(bg);
    }

    private View createToggleRow(String title, String key, boolean defVal) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean(key, defVal));
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(key, isChecked).apply());

        row.addView(tv);
        row.addView(sw);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
