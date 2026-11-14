package com.glyphlens.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

/**
 * Главный фоновый сервис плавающего окна GlyphLens.
 * Управляет состояниями:
 * 1) COLLAPSED — маленький плавающий кружок на краю экрана (с цифрой 1..5 или знаком "?").
 * 2) PICKER    — быстрый выбор количества глифов [1][2][3][4][5] (если включён режим "?").
 * 3) EXPANDED  — полноразмерный холст с верхней панелью слотов (в режиме ЗАПИСИ или СКВОЗНОЙ КАЛЬКИ).
 */
public class OverlayService extends Service {

    public static final String ACTION_START = "com.glyphlens.app.ACTION_START";
    public static final String ACTION_REFRESH_PREFS = "com.glyphlens.app.ACTION_REFRESH_PREFS";
    public static final String ACTION_TOGGLE_HIDE = "com.glyphlens.app.ACTION_TOGGLE_HIDE";
    public static final String ACTION_STOP = "com.glyphlens.app.ACTION_STOP";

    private static final String CHANNEL_ID = "glyphlens_overlay_channel";
    private static final int NOTIFICATION_ID = 1001;

    private enum State {
        COLLAPSED,
        PICKER,
        EXPANDED_RECORD,
        EXPANDED_REPLAY
    }

    private static boolean running = false;
    public static boolean isRunning() {
        return running;
    }

    private WindowManager windowManager;
    private State currentState = State.COLLAPSED;
    private boolean isHiddenByNotification = false;

    // 1. Плавающий кружок (в свёрнутом состоянии) и ряд быстрого выбора 1..5
    private LinearLayout bubbleContainer;
    private SlotButtonView mainBubbleBtn;
    private LinearLayout quickPickerRow;
    private WindowManager.LayoutParams bubbleParams;

    // 2. Верхняя панель управления со слотами (в развёрнутом виде) — всегда кликабельна!
    private LinearLayout topBarLayout;
    private final SlotButtonView[] slotButtons = new SlotButtonView[5];
    private SlotButtonView clearButton;
    private WindowManager.LayoutParams topBarParams;

    // 3. Большой квадратный холст глифов — в режиме кальки получает FLAG_NOT_TOUCHABLE!
    private GlyphCanvasView glyphCanvasView;
    private WindowManager.LayoutParams canvasParams;

    // Данные текущей сессии взлома
    private final GlyphStroke[] recordedStrokes = new GlyphStroke[5];
    private int activeMaxGlyphs = 5;
    private int currentSlotIndex = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundWithNotification();
        initOverlayViews();
        applyPreferences();
        switchToState(State.COLLAPSED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_TOGGLE_HIDE.equals(action)) {
                isHiddenByNotification = !isHiddenByNotification;
                updateVisibility();
                updateNotification();
            } else if (ACTION_REFRESH_PREFS.equals(action)) {
                applyPreferences();
            }
        }
        return START_STICKY;
    }

    private void initOverlayViews() {
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int btnSize = dpToPx(42);
        int btnMargin = dpToPx(4);

        // --- А. ПЛАВАЮЩИЙ ПУЗЫРЁК И МИНИ-МЕНЮ ВЫБОРА (1..5) ---
        bubbleContainer = new LinearLayout(this);
        bubbleContainer.setOrientation(LinearLayout.HORIZONTAL);
        bubbleContainer.setGravity(Gravity.CENTER_VERTICAL);

        mainBubbleBtn = new SlotButtonView(this);
        LinearLayout.LayoutParams mainBubbleLp = new LinearLayout.LayoutParams(btnSize, btnSize);
        bubbleContainer.addView(mainBubbleBtn, mainBubbleLp);

        quickPickerRow = new LinearLayout(this);
        quickPickerRow.setOrientation(LinearLayout.HORIZONTAL);
        quickPickerRow.setVisibility(View.GONE);
        for (int i = 1; i <= 5; i++) {
            final int count = i;
            SlotButtonView numBtn = new SlotButtonView(this);
            numBtn.setLabelText(String.valueOf(count));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(38));
            lp.leftMargin = btnMargin;
            numBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openCanvasWithGlyphCount(count);
                }
            });
            quickPickerRow.addView(numBtn, lp);
        }
        bubbleContainer.addView(quickPickerRow);

        setupBubbleDragAndClick(mainBubbleBtn);

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
        bubbleParams.x = AppPrefs.getBubbleX(this, dpToPx(12));
        bubbleParams.y = AppPrefs.getBubbleY(this, dpToPx(220));

        // --- Б. КВАДРАТНЫЙ ХОЛСТ ГЛИФОВ ---
        glyphCanvasView = new GlyphCanvasView(this);
        glyphCanvasView.setOnStrokeRecordedListener(new GlyphCanvasView.OnStrokeRecordedListener() {
            @Override
            public void onStrokeRecorded(GlyphStroke stroke) {
                handleStrokeRecorded(stroke);
            }
        });

        canvasParams = new WindowManager.LayoutParams(
                dpToPx(320),
                dpToPx(320),
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        canvasParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        // --- В. ВЕРХНЯЯ ПАНЕЛЬ СЛОТОВ (1..5 + КНОПКА C) ---
        topBarLayout = new LinearLayout(this);
        topBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        topBarLayout.setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < 5; i++) {
            final int slotIdx = i;
            SlotButtonView slotBtn = new SlotButtonView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.rightMargin = dpToPx(6);
            slotBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSlotButtonClicked(slotIdx);
                }
            });
            slotButtons[i] = slotBtn;
            topBarLayout.addView(slotBtn, lp);
        }

        clearButton = new SlotButtonView(this);
        clearButton.setLabelText("C");
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(38));
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onClearButtonClicked();
            }
        });
        topBarLayout.addView(clearButton, clearLp);

        topBarParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        topBarParams.gravity = Gravity.TOP | Gravity.LEFT;

        // Добавляем все три слоя в WindowManager
        windowManager.addView(glyphCanvasView, canvasParams);
        windowManager.addView(topBarLayout, topBarParams);
        windowManager.addView(bubbleContainer, bubbleParams);
    }

    private void setupBubbleDragAndClick(View bubbleView) {
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float touchX, touchY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - touchX);
                        int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dx) > dpToPx(6) || Math.abs(dy) > dpToPx(6)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            bubbleParams.x = initialX + dx;
                            bubbleParams.y = initialY + dy;
                            windowManager.updateViewLayout(bubbleContainer, bubbleParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            AppPrefs.saveBubblePos(OverlayService.this, bubbleParams.x, bubbleParams.y);
                        } else {
                            onBubbleTapped();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void onBubbleTapped() {
        if (AppPrefs.isAskBeforeHack(this)) {
            if (currentState == State.COLLAPSED) {
                switchToState(State.PICKER);
            } else {
                switchToState(State.COLLAPSED);
            }
        } else {
            openCanvasWithGlyphCount(AppPrefs.getFixedMaxGlyphs(this));
        }
    }

    private void openCanvasWithGlyphCount(int count) {
        this.activeMaxGlyphs = Math.max(1, Math.min(5, count));
        for (int i = 0; i < 5; i++) {
            recordedStrokes[i] = null;
            slotButtons[i].setStroke(null);
            slotButtons[i].setVisibility(i < activeMaxGlyphs ? View.VISIBLE : View.GONE);
        }
        currentSlotIndex = 0;
        updateSlotSelectionRing();
        switchToState(State.EXPANDED_RECORD);
    }

    private void handleStrokeRecorded(GlyphStroke stroke) {
        if (currentState != State.EXPANDED_RECORD) return;

        recordedStrokes[currentSlotIndex] = stroke;
        slotButtons[currentSlotIndex].setStroke(stroke);

        if (currentSlotIndex + 1 < activeMaxGlyphs) {
            currentSlotIndex++;
            updateSlotSelectionRing();
        } else {
            currentSlotIndex = 0;
            updateSlotSelectionRing();
            switchToState(State.EXPANDED_REPLAY);
        }
    }

    private void onSlotButtonClicked(int slotIdx) {
        if (slotIdx >= activeMaxGlyphs) return;

        if (currentState == State.EXPANDED_RECORD) {
            if (recordedStrokes[slotIdx] != null) {
                currentSlotIndex = slotIdx;
                updateSlotSelectionRing();
                switchToState(State.EXPANDED_REPLAY);
            }
        } else if (currentState == State.EXPANDED_REPLAY) {
            currentSlotIndex = slotIdx;
            updateSlotSelectionRing();
            glyphCanvasView.setDisplayedStroke(recordedStrokes[currentSlotIndex]);
        }
    }

    private void onClearButtonClicked() {
        boolean hasAnyGlyph = false;
        for (int i = 0; i < activeMaxGlyphs; i++) {
            if (recordedStrokes[i] != null) {
                hasAnyGlyph = true;
                break;
            }
        }

        if (hasAnyGlyph) {
            for (int i = 0; i < 5; i++) {
                recordedStrokes[i] = null;
                slotButtons[i].setStroke(null);
            }
            currentSlotIndex = 0;
            updateSlotSelectionRing();
            switchToState(State.EXPANDED_RECORD);
        } else {
            switchToState(State.COLLAPSED);
        }
    }

    private void updateSlotSelectionRing() {
        for (int i = 0; i < 5; i++) {
            slotButtons[i].setSlotSelected(i == currentSlotIndex);
        }
    }

    private void switchToState(State newState) {
        this.currentState = newState;

        switch (newState) {
            case COLLAPSED:
                quickPickerRow.setVisibility(View.GONE);
                mainBubbleBtn.setSlotSelected(false);
                break;

            case PICKER:
                quickPickerRow.setVisibility(View.VISIBLE);
                mainBubbleBtn.setSlotSelected(true);
                break;

            case EXPANDED_RECORD:
                glyphCanvasView.setRecordMode(true);
                canvasParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(glyphCanvasView, canvasParams);
                break;

            case EXPANDED_REPLAY:
                glyphCanvasView.setRecordMode(false);
                glyphCanvasView.setDisplayedStroke(recordedStrokes[currentSlotIndex]);
                canvasParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(glyphCanvasView, canvasParams);
                break;
        }
        updateVisibility();
    }

    private void updateVisibility() {
        if (isHiddenByNotification) {
            bubbleContainer.setVisibility(View.GONE);
            topBarLayout.setVisibility(View.GONE);
            glyphCanvasView.setVisibility(View.GONE);
            return;
        }

        if (currentState == State.COLLAPSED || currentState == State.PICKER) {
            bubbleContainer.setVisibility(View.VISIBLE);
            topBarLayout.setVisibility(View.GONE);
            glyphCanvasView.setVisibility(View.GONE);
        } else {
            bubbleContainer.setVisibility(View.GONE);
            topBarLayout.setVisibility(View.VISIBLE);
            glyphCanvasView.setVisibility(View.VISIBLE);
        }
    }

    public void applyPreferences() {
        if (AppPrefs.isAskBeforeHack(this)) {
            mainBubbleBtn.setLabelText("?");
        } else {
            mainBubbleBtn.setLabelText(String.valueOf(AppPrefs.getFixedMaxGlyphs(this)));
        }

        int lineColor = AppPrefs.getLineColor(this);
        glyphCanvasView.setLineColor(lineColor);
        glyphCanvasView.setBgAlphaPercent(AppPrefs.getBgAlphaPercent(this));
        glyphCanvasView.setShowGuide(AppPrefs.isShowGuide(this));
        for (SlotButtonView slotBtn : slotButtons) {
            slotBtn.setLineColor(lineColor);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        int scalePercent = AppPrefs.getCanvasScalePercent(this);
        int canvasSize = (int) (Math.min(screenW, screenH) * (scalePercent / 100f));
        int offsetY = dpToPx(AppPrefs.getCanvasOffsetYDp(this));

        int canvasTopY = (screenH - canvasSize) / 2 + offsetY;
        int canvasLeftX = (screenW - canvasSize) / 2;

        canvasParams.width = canvasSize;
        canvasParams.height = canvasSize;
        canvasParams.y = canvasTopY;
        windowManager.updateViewLayout(glyphCanvasView, canvasParams);

        topBarParams.x = canvasLeftX;
        topBarParams.y = Math.max(dpToPx(24), canvasTopY - dpToPx(50));
        windowManager.updateViewLayout(topBarLayout, topBarParams);
    }

    private void startForegroundWithNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GlyphLens Overlay Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(channel);
        }
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        Intent toggleIntent = new Intent(this, OverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE_HIDE);
        PendingIntent togglePi = PendingIntent.getService(this, 1, toggleIntent, piFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        String toggleLabel = isHiddenByNotification ? "Показать кнопку" : "Скрыть кнопку";

        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("GlyphLens активен")
                .setContentText(isHiddenByNotification ? "Поплавок временно скрыт" : "Нажмите для настроек")
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_view, toggleLabel, togglePi);

        return builder.build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        if (windowManager != null) {
            if (bubbleContainer != null) windowManager.removeView(bubbleContainer);
            if (topBarLayout != null) windowManager.removeView(topBarLayout);
            if (glyphCanvasView != null) windowManager.removeView(glyphCanvasView);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()
        );
    }
}
