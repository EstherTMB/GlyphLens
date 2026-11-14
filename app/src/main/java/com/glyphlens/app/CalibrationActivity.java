package com.glyphlens.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;
import java.io.InputStream;

public class CalibrationActivity extends Activity {

    private static final int REQ_PICK_SCREENSHOT = 2001;

    private ImageView screenshotView;
    private TextView scaleValueText;
    private TextView squashValueText;
    private TextView offsetValueText;

    // ВАЖНО: Мы больше не создаем здесь GlyphCanvasView!
    // Мы только управляем ползунками и отправляем команды в работающий OverlayService.
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        screenshotView = new ImageView(this);
        screenshotView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(screenshotView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(14);
        controlPanel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#EE10141D"));
        panelBg.setCornerRadius(dpToPx(14));
        panelBg.setStroke(dpToPx(1), Color.parseColor("#4FC3F7"));
        controlPanel.setBackground(panelBg);

        TextView panelTitle = new TextView(this);
        panelTitle.setText("Совместите зелёные кольца со скриншотом");
        panelTitle.setTextColor(Color.parseColor("#4FC3F7"));
        panelTitle.setTextSize(13f);
        panelTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        controlPanel.addView(panelTitle);

        // Ползунок общего масштаба
        int currScale = AppPrefs.getGridScalePercent(this);
        scaleValueText = new TextView(this);
        scaleValueText.setText("Размер сетки точек: " + currScale + "%");
        scaleValueText.setTextColor(Color.WHITE);
        scaleValueText.setTextSize(13f);
        scaleValueText.setPadding(0, dpToPx(8), 0, dpToPx(2));
        controlPanel.addView(scaleValueText);

        SeekBar scaleSeek = new SeekBar(this);
        scaleSeek.setMax(100); 
        scaleSeek.setProgress(currScale - 50);
        scaleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 50 + progress;
                scaleValueText.setText("Размер сетки точек: " + val + "%");
                AppPrefs.setGridScalePercent(CalibrationActivity.this, val);
                notifyServiceRefresh();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {} @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controlPanel.addView(scaleSeek);

        // Ползунок ширины сетки (squash)
        int currSquash = AppPrefs.getGridSquashPercent(this);
        squashValueText = new TextView(this);
        squashValueText.setText("Ширина сетки: " + currSquash + "%");
        squashValueText.setTextColor(Color.WHITE);
        squashValueText.setTextSize(13f);
        squashValueText.setPadding(0, dpToPx(6), 0, dpToPx(2));
        controlPanel.addView(squashValueText);

        SeekBar squashSeek = new SeekBar(this);
        squashSeek.setMax(90); 
        squashSeek.setProgress(currSquash - 60);
        squashSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 60 + progress;
                squashValueText.setText("Ширина сетки: " + val + "%");
                AppPrefs.setGridSquashPercent(CalibrationActivity.this, val);
                notifyServiceRefresh();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {} @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controlPanel.addView(squashSeek);

        // Ползунок сдвига Y
        int currOffset = AppPrefs.getCanvasOffsetYDp(this);
        offsetValueText = new TextView(this);
        offsetValueText.setText("Сдвиг по вертикали: " + currOffset + " dp");
        offsetValueText.setTextColor(Color.WHITE);
        offsetValueText.setTextSize(13f);
        offsetValueText.setPadding(0, dpToPx(6), 0, dpToPx(2));
        controlPanel.addView(offsetValueText);

        SeekBar offsetSeek = new SeekBar(this);
        offsetSeek.setMax(700); // Расширяем лимит от -350 до +350 dp для точной подгонки на длинных экранах
        offsetSeek.setProgress(currOffset + 350);
        offsetSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress - 350;
                offsetValueText.setText("Сдвиг по вертикали: " + val + " dp");
                AppPrefs.setCanvasOffsetYDp(CalibrationActivity.this, val);
                notifyServiceRefresh();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {} @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controlPanel.addView(offsetSeek);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dpToPx(10), 0, 0);

        Button pickBtn = new Button(this);
        pickBtn.setText("Скриншот");
        pickBtn.setTextSize(13f);
        pickBtn.setTextColor(Color.WHITE);
        pickBtn.setBackgroundColor(Color.parseColor("#37474F"));
        pickBtn.setOnClickListener(v -> launchScreenshotPicker());
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(0, dpToPx(42), 1f);
        pickLp.rightMargin = dpToPx(8);
        btnRow.addView(pickBtn, pickLp);

        Button doneBtn = new Button(this);
        doneBtn.setText("Готово");
        doneBtn.setTextSize(13f);
        doneBtn.setTextColor(Color.WHITE);
        doneBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
        doneBtn.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(0, dpToPx(42), 1f);
        btnRow.addView(doneBtn, doneLp);

        controlPanel.addView(btnRow);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.BOTTOM;
        int margin = dpToPx(12);
        panelLp.setMargins(margin, margin, margin, margin);
        root.addView(controlPanel, panelLp);

        setContentView(root);
        launchScreenshotPicker();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OverlayService.isRunning()) {
            Toast.makeText(this, "Сначала запустите оверлей GlyphLens!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_CALIBRATION_ENTER);
        startService(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (OverlayService.isRunning()) {
            Intent intent = new Intent(this, OverlayService.class);
            intent.setAction(OverlayService.ACTION_CALIBRATION_EXIT);
            startService(intent);
        }
    }

    private void notifyServiceRefresh() {
        if (!OverlayService.isRunning()) return;
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_REFRESH_PREFS);
        startService(intent);
    }

    private void launchScreenshotPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Выберите скриншот"), REQ_PICK_SCREENSHOT);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть галерею", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_SCREENSHOT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    Bitmap bmp = BitmapFactory.decodeStream(is);
                    if (is != null) is.close();
                    screenshotView.setImageBitmap(bmp);
                } catch (Exception e) {
                    Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
