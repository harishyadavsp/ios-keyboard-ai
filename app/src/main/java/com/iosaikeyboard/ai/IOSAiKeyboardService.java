package com.iosaikeyboard.ai;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.Gravity;

public class IOSAiKeyboardService extends InputMethodService {

    @Override
    public View onCreateInputView() {
        // Create the main keyboard container
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setBackgroundColor(Color.parseColor("#151515")); // Dark theme
        mainLayout.setPadding(0, 16, 0, 16);

        // Keyboard Row (Simplified QWERTY)
        String[] row = {"Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"};
        
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER);

        for (String key : row) {
            Button keyBtn = new Button(this);
            keyBtn.setText(key);
            keyBtn.setTextColor(Color.WHITE);
            keyBtn.setBackgroundColor(Color.parseColor("#333333"));
            keyBtn.setAllCaps(false);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            params.setMargins(6, 8, 6, 8);
            keyBtn.setLayoutParams(params);
            
            keyBtn.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(key.toLowerCase(), 1);
                }
            });
            
            rowLayout.addView(keyBtn);
        }
        
        mainLayout.addView(rowLayout);
        return mainLayout;
    }
}
