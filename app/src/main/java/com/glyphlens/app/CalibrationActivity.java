package com.glyphlens.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;

/**
 * Полноэкранный режим точной подгонки сетки из 11 точек поверх скриншота из Ingress.
 * Использует системный фотопикер (ACTION_GET_CONTENT), поэтому НЕ требует никаких
 * разрешений на доступ к памяти телефона!
 */
public class CalibrationActivity extends Activity {

    private static final int REQ_PICK_SCREENSHOT = 2001;

    private ImageView screenshotView;
    private GlyphCanvasView previewCanvas;
    private FrameLayout.LayoutParams canvasLp;
    private TextView scaleValueText;
    private TextView offsetValueText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 1. Фоновый ImageView со скриншотом из игры
        screenshotView = new ImageView(this);
        screenshotView.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(screenshotView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // 2. Само поле GlyphCanvasView с 11 точками
        previewCanvas = new GlyphCanvasView(this);
        previewCanvas.setCalibrationPreview(true);
        previewCanvas.setBgAlphaPercent(AppPrefs.getBgAlphaPercent(this));
        previewCanvas.setLineColor(AppPrefs.getLineColor(this));

        canvasLp = new FrameLayout.LayoutParams(dpToPx(300), dpToPx(300));
        canvasLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        root.addView(previewCanvas, canvasLp);

        // 3. Полупрозрачная панель управления внизу экрана
        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        int pad = dpToPx(14);
        controlPanel.setPadding(pad, pad, pad, pad);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#DD10141D"));
        panelBg.setCornerRadius(dpToPx(14));
        panelBg.setStroke(dpToPx(1), Color.parseColor("#4FC3F7"));
        controlPanel.setBackground(panelBg);

        TextView panelTitle = new TextView(this);
        panelTitle.setText("Совместите 11 зелёных колец со скриншотом Glyph Hack");
        panelTitle.setTextColor(Color.parseColor("#4FC3F7"));
        panelTitle.setTextSize(13f);
        panelTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        controlPanel.addView(panelTitle);

        // Ползунок масштаба
        int currScale = AppPrefs.getCanvasScalePercent(this);
        scaleValueText = new TextView(this);
        scaleValueText.setText("Размер квадрата поля: " + currScale + "%");
        scaleValueText.setTextColor(Color.WHITE);
        scaleValueText.setTextSize(13f);
        scaleValueText.setPadding(0, dpToPx(8), 0, dpToPx(2));
        controlPanel.addView(scaleValueText);

        SeekBar scaleSeek = new SeekBar(this);
        scaleSeek.setMax(40); // 65% .. 105%
        scaleSeek.setProgress(currScale - 65);
        scaleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = 65 + progress;
                scaleValueText.setText("Размер квадрата поля: " + val + "%");
                AppPrefs.setCanvasScalePercent(CalibrationActivity.this, val);
                updatePreviewGeometry();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controlPanel.addView(scaleSeek);

        // Ползунок вертикального сдвига
        int currOffset = AppPrefs.getCanvasOffsetYDp(this);
        offsetValueText = new TextView(this);
        offsetValueText.setText("Сдвиг поля по вертикали: " + currOffset + " dp");
        offsetValueText.setTextColor(Color.WHITE);
        offsetValueText.setTextSize(13f);
        offsetValueText.setPadding(0, dpToPx(6), 0, dpToPx(2));
        controlPanel.addView(offsetValueText);

        SeekBar offsetSeek = new SeekBar(this);
        offsetSeek.setMax(300); // -150 .. +150 dp
        offsetSeek.setProgress(currOffset + 150);
        offsetSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress - 150;
                offsetValueText.setText("Сдвиг поля по вертикали: " + val + " dp");
                AppPrefs.setCanvasOffsetYDp(CalibrationActivity.this, val);
                updatePreviewGeometry();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        controlPanel.addView(offsetSeek);

        // Ряд кнопок: [Выбрать скриншот] и [Готово]
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dpToPx(10), 0, 0);

        Button pickBtn = new Button(this);
        pickBtn.setText("Выбрать скриншот");
        pickBtn.setTextSize(13f);
        pickBtn.setTextColor(Color.WHITE);
        pickBtn.setBackgroundColor(Color.parseColor("#37474F"));
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                launchScreenshotPicker();
            }
        });
        LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(
                0, dpToPx(42), 1f
        );
        pickLp.rightMargin = dpToPx(8);
        btnRow.addView(pickBtn, pickLp);

        Button doneBtn = new Button(this);
        doneBtn.setText("Готово");
        doneBtn.setTextSize(13f);
        doneBtn.setTextColor(Color.WHITE);
        doneBtn.setBackgroundColor(Color.parseColor("#2E7D32"));
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(
                0, dpToPx(42), 1f
        );
        btnRow.addView(doneBtn, doneLp);

        controlPanel.addView(btnRow);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        panelLp.gravity = Gravity.BOTTOM;
        int margin = dpToPx(12);
        panelLp.setMargins(margin, margin, margin, margin);
        root.addView(controlPanel, panelLp);

        setContentView(root);
        updatePreviewGeometry();

        // Сразу открываем выбор скриншота при входе
        launchScreenshotPicker();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (OverlayService.isRunning()) {
            Intent intent = new Intent(this, OverlayService.class);
            intent.setAction(OverlayService.ACTION_CALIBRATION_ENTER);
            startService(intent);
        }
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

    private void launchScreenshotPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Выберите скриншот Glyph Hack"), REQ_PICK_SCREENSHOT);
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

    private void updatePreviewGeometry() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int scalePercent = AppPrefs.getCanvasScalePercent(this);
        int canvasSize = (int) (Math.min(screenW, screenH) * (scalePercent / 100f));
        int offsetY = dpToPx(AppPrefs.getCanvasOffsetYDp(this));
        int canvasTopY = (screenH - canvasSize) / 2 + offsetY;

        canvasLp.width = canvasSize;
        canvasLp.height = canvasSize;
        canvasLp.topMargin = canvasTopY;
        previewCanvas.setLayoutParams(canvasLp);
        previewCanvas.invalidate();
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}
