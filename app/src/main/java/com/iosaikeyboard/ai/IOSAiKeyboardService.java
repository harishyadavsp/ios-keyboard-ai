package com.iosaikeyboard.ai;

import android.inputmethodservice.InputMethodService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class IOSAiKeyboardService extends InputMethodService {

    private LinearLayout rootLayout;
    private LinearLayout keyboardContainer;
    private LinearLayout aiDrawer;
    private boolean isShifted = false;
    private boolean isSymbolMode = false;
    private boolean isAiOpen = false;

    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private Runnable backspaceRunnable;

    private final String[][] LETTERS_LAYOUT = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
            {"123", "😊", "SPACE", ".", "RETURN"}
    };

    private final String[][] SYMBOLS_LAYOUT = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""},
            {"[", "]", "{", "}", "#", "%", "^", "*", "+", "="},
            {"SHIFT", ".", ",", "?", "!", "'", "_", "\\", "DEL"},
            {"ABC", "😊", "SPACE", ".", "RETURN"}
    };

    @Override
    public View onCreateInputView() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#1C1C23")); // iOS Dark Keyboard Deck
        rootLayout.setPadding(8, 12, 8, 24);

        // 1. Top Suggestion & AI Bar
        rootLayout.addView(createTopToolbar());

        // 2. AI Action Drawer (Hidden by default)
        aiDrawer = createAiDrawer();
        aiDrawer.setVisibility(View.GONE);
        rootLayout.addView(aiDrawer);

        // 3. Main Keyboard Rows Container
        keyboardContainer = new LinearLayout(this);
        keyboardContainer.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(keyboardContainer);

        renderKeyboardRows();
        return rootLayout;
    }

    private View createTopToolbar() {
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(12, 4, 12, 14);

        String[] suggestions = {"The", "Hello", "Sounds good 👍"};
        for (String word : suggestions) {
            TextView chip = new TextView(this);
            chip.setText(word);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(14f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(16, 12, 16, 12);
            chip.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(word + " ", 1);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            params.setMargins(4, 0, 4, 0);
            chip.setLayoutParams(params);
            toolbar.addView(chip);
        }

        // AI Toggle Button
        Button aiBtn = new Button(this);
        aiBtn.setText("✨ AI");
        aiBtn.setTextColor(Color.WHITE);
        aiBtn.setTextSize(13f);
        aiBtn.setAllCaps(false);
        aiBtn.setBackground(createRoundedDrawable("#3B82F6", 24));
        LinearLayout.LayoutParams aiParams = new LinearLayout.LayoutParams(
                dpToPx(70), dpToPx(36));
        aiParams.setMargins(8, 0, 0, 0);
        aiBtn.setLayoutParams(aiParams);
        aiBtn.setOnClickListener(v -> toggleAiDrawer());

        toolbar.addView(aiBtn);
        return toolbar;
    }

    private LinearLayout createAiDrawer() {
        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(12, 12, 12, 16);
        drawer.setBackground(createRoundedDrawable("#15151B", 20));

        TextView title = new TextView(this);
        title.setText("✨ Gemini AI Writing Tools");
        title.setTextColor(Color.parseColor("#8E8E99"));
        title.setTextSize(12f);
        title.setPadding(8, 0, 8, 12);
        drawer.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);

        String[][] aiActions = {
                {"✓ Fix Grammar", "FIX"},
                {"✨ Improve", "IMPROVE"},
                {"💼 Professional", "PRO"},
                {"👋 Casual", "CASUAL"},
                {"➖ Shorter", "SHORTER"}
        };

        for (String[] action : aiActions) {
            Button btn = new Button(this);
            btn.setText(action[0]);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(13f);
            btn.setAllCaps(false);
            btn.setPadding(28, 16, 28, 16);
            btn.setBackground(createRoundedDrawable("#2B2B36", 18));

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(44));
            p.setMargins(6, 4, 6, 4);
            btn.setLayoutParams(p);

            btn.setOnClickListener(v -> applyAiTransformation(action[1]));
            actionsRow.addView(btn);
        }

        scroll.addView(actionsRow);
        drawer.addView(scroll);
        return drawer;
    }

    private void toggleAiDrawer() {
        isAiOpen = !isAiOpen;
        aiDrawer.setVisibility(isAiOpen ? View.VISIBLE : View.GONE);
    }

    private void renderKeyboardRows() {
        keyboardContainer.removeAllViews();
        String[][] activeLayout = isSymbolMode ? SYMBOLS_LAYOUT : LETTERS_LAYOUT;

        for (int rowIndex = 0; rowIndex < activeLayout.length; rowIndex++) {
            String[] rowKeys = activeLayout[rowIndex];
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);

            // Slight side indent for middle letter row ('a' - 'l')
            if (!isSymbolMode && rowIndex == 2) {
                rowLayout.setPadding(dpToPx(16), 0, dpToPx(16), 0);
            }

            int keyHeight = (rowIndex == 0) ? dpToPx(40) : dpToPx(48);

            for (String key : rowKeys) {
                View keyView = createKeyButton(key, keyHeight);
                rowLayout.addView(keyView);
            }

            keyboardContainer.addView(rowLayout);
        }
    }

    private View createKeyButton(String key, int heightPx) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(0, 0, 0, 0);

        float weight = 1.0f;
        String bgColor = "#3B3B46"; // Standard iOS Dark Key
        String label = key;

        switch (key) {
            case "SHIFT":
                label = isShifted ? "⬆" : "⇧";
                weight = 1.4f;
                bgColor = isShifted ? "#3B82F6" : "#32323C";
                btn.setTextSize(18f);
                break;
            case "DEL":
                label = "⌫";
                weight = 1.4f;
                bgColor = "#32323C";
                btn.setTextSize(18f);
                setupLongPressBackspace(btn);
                break;
            case "123":
            case "ABC":
                weight = 1.3f;
                bgColor = "#32323C";
                btn.setTextSize(14f);
                break;
            case "😊":
                weight = 1.1f;
                bgColor = "#32323C";
                btn.setTextSize(18f);
                break;
            case "SPACE":
                label = "space";
                weight = 4.2f;
                btn.setTextSize(15f);
                break;
            case "RETURN":
                label = "↵";
                weight = 1.6f;
                bgColor = "#3B82F6"; // iOS Blue Return Key
                btn.setTextSize(18f);
                break;
            default:
                if (!isSymbolMode && isShifted && key.length() == 1) {
                    label = key.toUpperCase();
                }
                btn.setTextSize(19f);
                break;
        }

        btn.setText(label);
        btn.setBackground(createRoundedDrawable(bgColor, 10));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, heightPx, weight);
        params.setMargins(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(4));
        btn.setLayoutParams(params);

        if (!key.equals("DEL")) {
            btn.setOnClickListener(v -> handleKeyPress(key));
        }

        return btn;
    }

    private void setupLongPressBackspace(Button delBtn) {
        backspaceRunnable = new Runnable() {
            @Override
            public void run() {
                performBackspace();
                repeatHandler.postDelayed(this, 65); // Rapid continuous delete
            }
        };

        delBtn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.7f);
                    performBackspace();
                    repeatHandler.postDelayed(backspaceRunnable, 400);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1.0f);
                    repeatHandler.removeCallbacks(backspaceRunnable);
                    return true;
            }
            return false;
        });
    }

    private void performBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence selected = ic.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            ic.commitText("", 1);
        } else {
            ic.deleteSurroundingText(1, 0);
        }
    }

    private void handleKeyPress(String key) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (key) {
            case "SHIFT":
                isShifted = !isShifted;
                renderKeyboardRows();
                break;
            case "123":
                isSymbolMode = true;
                renderKeyboardRows();
                break;
            case "ABC":
                isSymbolMode = false;
                renderKeyboardRows();
                break;
            case "SPACE":
                ic.commitText(" ", 1);
                break;
            case "😊":
                ic.commitText("😊", 1);
                break;
            case "RETURN":
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                break;
            default:
                String out = (!isSymbolMode && isShifted) ? key.toUpperCase() : key;
                ic.commitText(out, 1);
                if (isShifted) {
                    isShifted = false;
                    renderKeyboardRows();
                }
                break;
        }
    }

    private void applyAiTransformation(String mode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        ExtractedText extracted = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted == null || extracted.text == null || extracted.text.length() == 0) {
            Toast.makeText(this, "Type some text first!", Toast.LENGTH_SHORT).show();
            return;
        }

        String raw = extracted.text.toString().trim();
        String transformed = raw;

        switch (mode) {
            case "FIX":
                transformed = raw.replaceAll("\\bi\\b", "I")
                                 .replaceAll("\\bim\\b", "I'm")
                                 .replaceAll("\\bdont\\b", "don't")
                                 .replaceAll("\\bu\\b", "you")
                                 .replaceAll("\\br\\b", "are");
                transformed = Character.toUpperCase(transformed.charAt(0)) + transformed.substring(1);
                if (!transformed.endsWith(".") && !transformed.endsWith("!") && !transformed.endsWith("?")) {
                    transformed += ".";
                }
                break;
            case "IMPROVE":
                transformed = Character.toUpperCase(raw.charAt(0)) + raw.substring(1) + " ✨";
                break;
            case "PRO":
                transformed = "Dear team, regarding this: " + raw + ".";
                break;
            case "CASUAL":
                transformed = "Hey! " + raw + " 👍";
                break;
            case "SHORTER":
                String[] words = raw.split("\\s+");
                if (words.length > 4) {
                    transformed = words[0] + " " + words[1] + " " + words[2] + "...";
                }
                break;
        }

        ic.deleteSurroundingText(1000, 1000);
        ic.commitText(transformed, 1);
        toggleAiDrawer();
    }

    private GradientDrawable createRoundedDrawable(String hexColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(hexColor));
        drawable.setCornerRadius(dpToPx(radiusDp));
        return drawable;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
