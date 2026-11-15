package com.glyphlens.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private Button toggleServiceBtn;
    private RadioGroup maxGlyphsGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceButtonState();
    }

    private View buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#10141D"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(20);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("GLYPHLENS");
        title.setTextColor(Color.parseColor("#00E676"));
        title.setTextSize(24f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Оптическая шпаргалка-линза глифов для Ingress");
        subtitle.setTextColor(Color.parseColor("#90A4AE"));
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, dpToPx(4), 0, dpToPx(18));
        root.addView(subtitle);

        toggleServiceBtn = new Button(this);
        toggleServiceBtn.setTextSize(15f);
        toggleServiceBtn.setTextColor(Color.WHITE);
        toggleServiceBtn.setOnClickListener(v -> onToggleServiceClicked());
        root.addView(toggleServiceBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(54)));

        addSectionHeader(root, "1. КОЛИЧЕСТВО ГЛИФОВ И ПОПЛАВОК");

        Switch askSwitch = new Switch(this);
        askSwitch.setText("Спрашивать перед взломом (на кружке горит «?», а максимум глифов выбирается при нажатии на него, а не в настройках)");
        askSwitch.setTextColor(Color.WHITE);
        askSwitch.setChecked(AppPrefs.isAskBeforeHack(this));
        root.addView(askSwitch);

        final TextView fixedHint = new TextView(this);
        fixedHint.setText("Максимальное количество глифов (в фиксированном режиме выбранная цифра отображается на кружке):");
        fixedHint.setTextColor(Color.parseColor("#B0BEC5"));
        fixedHint.setTextSize(13f);
        fixedHint.setPadding(0, dpToPx(12), 0, dpToPx(6));
        root.addView(fixedHint);

        maxGlyphsGroup = new RadioGroup(this);
        String[] glyphLabels = { "1 Глиф (Портал L1)", "2 Глифа (Портал L2)", "3 Глифа (Портал L3 – L5)", "4 Глифа (Портал L6 – L7)", "5 Глифов (Портал L8)" };
        int currentMax = AppPrefs.getFixedMaxGlyphs(this);
        for (int i = 0; i < 5; i++) {
            final int count = i + 1;
            RadioButton rb = new RadioButton(this);
            rb.setText(glyphLabels[i]);
            rb.setTextColor(Color.WHITE);
            rb.setId(100 + count);
            rb.setChecked(count == currentMax);
            rb.setOnClickListener(v -> { AppPrefs.setFixedMaxGlyphs(MainActivity.this, count); notifyServicePrefsChanged(); });
            maxGlyphsGroup.addView(rb);
        }
        root.addView(maxGlyphsGroup);
        updateFixedGroupEnabled(!AppPrefs.isAskBeforeHack(this));
        askSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setAskBeforeHack(MainActivity.this, isChecked);
            updateFixedGroupEnabled(!isChecked);
            notifyServicePrefsChanged();
        });

        addSectionHeader(root, "2. ВНЕШНИЙ ВИД И ЦВЕТ ЛИНИИ");

        Switch guideSwitch = new Switch(this);
        guideSwitch.setText("Показывать сетку из 11 точек (Show glyph guide)");
        guideSwitch.setTextColor(Color.WHITE);
        guideSwitch.setChecked(AppPrefs.isShowGuide(this));
        guideSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> { AppPrefs.setShowGuide(MainActivity.this, isChecked); notifyServicePrefsChanged(); });
        root.addView(guideSwitch);

        TextView colorLabel = new TextView(this);
        colorLabel.setText("Цвет линии глифа:");
        colorLabel.setTextColor(Color.WHITE);
        colorLabel.setPadding(0, dpToPx(12), 0, dpToPx(8));
        root.addView(colorLabel);

        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        final int[] presetColors = { Color.parseColor("#00E676"), Color.parseColor("#00B0FF"), Color.parseColor("#00E5FF"), Color.parseColor("#FFD600"), Color.parseColor("#FF4081"), Color.parseColor("#FFFFFF") };
        for (final int c : presetColors) {
            View colorSwatch = new View(this);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL); gd.setColor(c); gd.setStroke(dpToPx(2), Color.WHITE);
            colorSwatch.setBackground(gd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40));
            lp.rightMargin = dpToPx(12);
            colorSwatch.setOnClickListener(v -> { AppPrefs.setLineColor(MainActivity.this, c); notifyServicePrefsChanged(); Toast.makeText(MainActivity.this, "Цвет линии обновлён", Toast.LENGTH_SHORT).show(); });
            colorRow.addView(colorSwatch, lp);
        }
        root.addView(colorRow);

        final TextView alphaLabel = new TextView(this);
        int currAlpha = AppPrefs.getBgAlphaPercent(this);
        alphaLabel.setText("Непрозрачность фона линзы: " + currAlpha + "%");
        alphaLabel.setTextColor(Color.WHITE);
        alphaLabel.setPadding(0, dpToPx(14), 0, dpToPx(4));
        root.addView(alphaLabel);

        SeekBar alphaSeek = new SeekBar(this);
        alphaSeek.setMax(60); // 15% .. 75%
        alphaSeek.setProgress(currAlpha - 15);
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 15 + progress; alphaLabel.setText("Непрозрачность фона линзы: " + val + "%"); AppPrefs.setBgAlphaPercent(MainActivity.this, val); notifyServicePrefsChanged();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {} @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(alphaSeek);

        addSectionHeader(root, "3. КАЛИБРОВКА СЕТКИ ПОД INGRESS");

        TextView calibHint = new TextView(this);
        calibHint.setText("Запустите GlyphLens кнопкой выше, затем откройте калибровку. Загрузите скриншот окна Glyph Hack и совместите кольца.");
        calibHint.setTextColor(Color.parseColor("#B0BEC5"));
        calibHint.setTextSize(13f);
        calibHint.setPadding(0, dpToPx(2), 0, dpToPx(10));
        root.addView(calibHint);

        Button openCalibBtn = new Button(this);
        openCalibBtn.setText("КАЛИБРОВАТЬ ПО СКРИНШОТУ");
        openCalibBtn.setTextSize(14f);
        openCalibBtn.setTextColor(Color.WHITE);
        openCalibBtn.setBackgroundColor(Color.parseColor("#0277BD"));
        openCalibBtn.setOnClickListener(v -> {
            if (!OverlayService.isRunning()) {
                Toast.makeText(this, "Сначала нажмите START OVERLAY!", Toast.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(MainActivity.this, CalibrationActivity.class));
            }
        });
        LinearLayout.LayoutParams calibBtnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46));
        calibBtnLp.bottomMargin = dpToPx(10);
        root.addView(openCalibBtn, calibBtnLp);

        TextView creditsView = new TextView(this);
        creditsView.setText("GlyphLens v1.8.1 • Created by EstherTMB\nCrafted with Gemini AI assistance for Ingress Agents");
        creditsView.setTextColor(Color.parseColor("#607D8B"));
        creditsView.setTextSize(11.5f);
        creditsView.setGravity(Gravity.CENTER_HORIZONTAL);
        creditsView.setPadding(0, dpToPx(32), 0, dpToPx(12));
        root.addView(creditsView);

        updateServiceButtonState();
        return scrollView;
    }

    private void addSectionHeader(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextColor(Color.parseColor("#4FC3F7")); tv.setTextSize(14f); tv.setTypeface(null, android.graphics.Typeface.BOLD); tv.setPadding(0, dpToPx(22), 0, dpToPx(8));
        parent.addView(tv);
    }

    private void updateFixedGroupEnabled(boolean enabled) {
        for (int i = 0; i < maxGlyphsGroup.getChildCount(); i++) {
            View child = maxGlyphsGroup.getChildAt(i); child.setEnabled(enabled); child.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }

    private void onToggleServiceClicked() {
        if (OverlayService.isRunning()) {
            Intent stopIntent = new Intent(this, OverlayService.class);
            stopIntent.setAction(OverlayService.ACTION_STOP);
            startService(stopIntent);
            toggleServiceBtn.postDelayed(this::updateServiceButtonState, 150);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Выдайте разрешение «Поверх других окон» для GlyphLens", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }

        Intent startIntent = new Intent(this, OverlayService.class);
        startIntent.setAction(OverlayService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(startIntent); else startService(startIntent);

        toggleServiceBtn.postDelayed(this::updateServiceButtonState, 150);
    }

    private void updateServiceButtonState() {
        boolean running = OverlayService.isRunning();
        if (running) {
            toggleServiceBtn.setText("TERMINATE (ОСТАНОВИТЬ GLYPHLENS)"); toggleServiceBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
        } else {
            toggleServiceBtn.setText("START OVERLAY (ЗАПУСТИТЬ GLYPHLENS)"); toggleServiceBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
        }
    }

    private void notifyServicePrefsChanged() {
        if (OverlayService.isRunning()) {
            Intent intent = new Intent(this, OverlayService.class);
            intent.setAction(OverlayService.ACTION_REFRESH_PREFS);
            startService(intent);
        }
    }

    private int dpToPx(int dp) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()); }
}
