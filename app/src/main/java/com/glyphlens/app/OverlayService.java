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

public class OverlayService extends Service {

    public static final String ACTION_START = "com.glyphlens.app.ACTION_START";
    public static final String ACTION_REFRESH_PREFS = "com.glyphlens.app.ACTION_REFRESH_PREFS";
    public static final String ACTION_TOGGLE_HIDE = "com.glyphlens.app.ACTION_TOGGLE_HIDE";
    public static final String ACTION_CALIBRATION_ENTER = "com.glyphlens.app.ACTION_CALIBRATION_ENTER";
    public static final String ACTION_CALIBRATION_EXIT = "com.glyphlens.app.ACTION_CALIBRATION_EXIT";
    public static final String ACTION_STOP = "com.glyphlens.app.ACTION_STOP";

    private static final String CHANNEL_ID = "glyphlens_overlay_channel";
    private static final int NOTIFICATION_ID = 1001;

    private enum State { COLLAPSED, PICKER, EXPANDED_RECORD, EXPANDED_REPLAY, CALIBRATION }

    private static boolean running = false;
    public static boolean isRunning() { return running; }

    private WindowManager windowManager;
    private State currentState = State.COLLAPSED;
    private boolean isHiddenByNotification = false;

    private LinearLayout bubbleContainer;
    private SlotButtonView mainBubbleBtn;
    private LinearLayout quickPickerRow;
    private WindowManager.LayoutParams bubbleParams;

    private LinearLayout topBarLayout;
    private final SlotButtonView[] slotButtons = new SlotButtonView[5];
    private WindowManager.LayoutParams topBarParams;

    private GlyphCanvasView glyphCanvasView;
    private WindowManager.LayoutParams canvasParams;

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
            if (ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
            else if (ACTION_TOGGLE_HIDE.equals(action)) { isHiddenByNotification = !isHiddenByNotification; updateVisibility(); updateNotification(); }
            else if (ACTION_CALIBRATION_ENTER.equals(action)) { switchToState(State.CALIBRATION); applyPreferences(); }
            else if (ACTION_CALIBRATION_EXIT.equals(action)) { switchToState(State.COLLAPSED); applyPreferences(); }
            else if (ACTION_REFRESH_PREFS.equals(action)) { applyPreferences(); }
        }
        return START_STICKY;
    }

    private void initOverlayViews() {
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int btnSize = dpToPx(42);
        int btnMargin = dpToPx(4);

        bubbleContainer = new LinearLayout(this);
        bubbleContainer.setOrientation(LinearLayout.HORIZONTAL);
        bubbleContainer.setGravity(Gravity.CENTER_VERTICAL);
        mainBubbleBtn = new SlotButtonView(this);
        bubbleContainer.addView(mainBubbleBtn, new LinearLayout.LayoutParams(btnSize, btnSize));

        quickPickerRow = new LinearLayout(this);
        quickPickerRow.setOrientation(LinearLayout.HORIZONTAL);
        quickPickerRow.setVisibility(View.GONE);
        for (int i = 1; i <= 5; i++) {
            final int count = i;
            SlotButtonView numBtn = new SlotButtonView(this);
            numBtn.setLabelText(String.valueOf(count));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(38));
            lp.leftMargin = btnMargin;
            numBtn.setOnClickListener(v -> openCanvasWithGlyphCount(count));
            quickPickerRow.addView(numBtn, lp);
        }
        bubbleContainer.addView(quickPickerRow);
        setupBubbleDragAndClick(mainBubbleBtn);
        bubbleParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
        bubbleParams.x = AppPrefs.getBubbleX(this, dpToPx(12));
        bubbleParams.y = AppPrefs.getBubbleY(this, dpToPx(220));

        glyphCanvasView = new GlyphCanvasView(this);
        glyphCanvasView.setOnStrokeRecordedListener(this::handleStrokeRecorded);
        
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int canvasSize = screenW - dpToPx(16);
        
        // ВАЖНО: Привязка холста строго к Gravity.TOP, чтобы не зависеть от скрытой панели навигации!
        canvasParams = new WindowManager.LayoutParams(canvasSize, canvasSize, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        canvasParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        topBarLayout = new LinearLayout(this);
        topBarLayout.setOrientation(LinearLayout.HORIZONTAL);
        topBarLayout.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < 5; i++) {
            final int slotIdx = i;
            SlotButtonView slotBtn = new SlotButtonView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.rightMargin = dpToPx(5);
            slotBtn.setOnClickListener(v -> onSlotButtonClicked(slotIdx));
            slotButtons[i] = slotBtn;
            topBarLayout.addView(slotBtn, lp);
        }
        SlotButtonView clearButton = new SlotButtonView(this);
        clearButton.setLabelText("C");
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(38));
        clearLp.rightMargin = dpToPx(5);
        clearButton.setOnClickListener(v -> onClearOnlyClicked());
        topBarLayout.addView(clearButton, clearLp);

        SlotButtonView closeButton = new SlotButtonView(this);
        closeButton.setLabelText("✕");
        closeButton.setCloseButtonStyle(true);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(38));
        closeButton.setOnClickListener(v -> onCloseOverlayClicked());
        topBarLayout.addView(closeButton, closeLp);

        topBarParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, overlayType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        topBarParams.gravity = Gravity.TOP | Gravity.LEFT;

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
                        initialX = bubbleParams.x; initialY = bubbleParams.y;
                        touchX = event.getRawX(); touchY = event.getRawY();
                        isDragging = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - touchX); int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dx) > dpToPx(6) || Math.abs(dy) > dpToPx(6)) isDragging = true;
                        if (isDragging) { bubbleParams.x = initialX + dx; bubbleParams.y = initialY + dy; windowManager.updateViewLayout(bubbleContainer, bubbleParams); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isDragging) AppPrefs.saveBubblePos(OverlayService.this, bubbleParams.x, bubbleParams.y);
                        else onBubbleTapped();
                        return true;
                }
                return false;
            }
        });
    }

    private void onBubbleTapped() {
        if (AppPrefs.isAskBeforeHack(this)) {
            switchToState(currentState == State.COLLAPSED ? State.PICKER : State.COLLAPSED);
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

    private void onClearOnlyClicked() {
        for (int i = 0; i < 5; i++) { recordedStrokes[i] = null; slotButtons[i].setStroke(null); }
        currentSlotIndex = 0;
        updateSlotSelectionRing();
        switchToState(State.EXPANDED_RECORD);
    }

    private void onCloseOverlayClicked() {
        for (int i = 0; i < 5; i++) { recordedStrokes[i] = null; slotButtons[i].setStroke(null); }
        currentSlotIndex = 0;
        updateSlotSelectionRing();
        switchToState(State.COLLAPSED);
    }

    private void updateSlotSelectionRing() {
        for (int i = 0; i < 5; i++) slotButtons[i].setSlotSelected(i == currentSlotIndex);
    }

    private void switchToState(State newState) {
        this.currentState = newState;
        
        if (newState != State.CALIBRATION) {
            glyphCanvasView.setCalibrationPreview(false);
        }

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
                canvasParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(glyphCanvasView, canvasParams);
                break;
            case CALIBRATION:
                glyphCanvasView.setRecordMode(false);
                glyphCanvasView.setCalibrationPreview(true);
                canvasParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(glyphCanvasView, canvasParams);
                break;
        }
        updateVisibility();
    }

    private void updateVisibility() {
        if (isHiddenByNotification) {
            bubbleContainer.setVisibility(View.GONE); topBarLayout.setVisibility(View.GONE); glyphCanvasView.setVisibility(View.GONE);
            return;
        }
        
        if (currentState == State.CALIBRATION) {
            bubbleContainer.setVisibility(View.GONE); 
            topBarLayout.setVisibility(View.VISIBLE); // Оставили видимой, чтобы видеть, где она будет
            glyphCanvasView.setVisibility(View.VISIBLE);
        } else if (currentState == State.COLLAPSED || currentState == State.PICKER) {
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
        mainBubbleBtn.setLabelText(AppPrefs.isAskBeforeHack(this) ? "?" : String.valueOf(AppPrefs.getFixedMaxGlyphs(this)));
        int lineColor = AppPrefs.getLineColor(this);
        glyphCanvasView.setLineColor(lineColor);
        glyphCanvasView.setBgAlphaPercent(AppPrefs.getBgAlphaPercent(this));
        glyphCanvasView.setShowGuide(AppPrefs.isShowGuide(this));
        for (SlotButtonView slotBtn : slotButtons) slotBtn.setLineColor(lineColor);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        // --- ГЛАВНОЕ ИЗМЕНЕНИЕ ---
        // Если координата Y еще не была сохранена, вычисляем ее один раз по центру.
        int absoluteY = AppPrefs.getCanvasAbsoluteY(this);
        if (absoluteY == -1) {
            int canvasSize = screenW - dpToPx(16);
            absoluteY = (screenH - canvasSize) / 2;
            AppPrefs.setCanvasAbsoluteY(this, absoluteY);
        }
        
        // Теперь мы просто берем эту абсолютную базу и прибавляем к ней ползунок смещения
        int finalY = absoluteY + dpToPx(AppPrefs.getCanvasOffsetYDp(this));

        canvasParams.y = finalY;
        windowManager.updateViewLayout(glyphCanvasView, canvasParams);

        float scale = AppPrefs.getGridScalePercent(this) / 100f;
        float squash = AppPrefs.getGridSquashPercent(this) / 100f;
        glyphCanvasView.setGridGeometry(scale, squash);

        topBarParams.x = dpToPx(12);
        topBarParams.y = Math.max(dpToPx(30), finalY - dpToPx(55));
        windowManager.updateViewLayout(topBarLayout, topBarParams);
    }

    private void startForegroundWithNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "GlyphLens Overlay Service", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent openPi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), piFlags);
        Intent toggleIntent = new Intent(this, OverlayService.class);
        toggleIntent.setAction(ACTION_TOGGLE_HIDE);
        PendingIntent togglePi = PendingIntent.getService(this, 1, toggleIntent, piFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("GlyphLens активен")
                .setContentText(isHiddenByNotification ? "Поплавок скрыт" : "Нажмите для настроек")
                .setContentIntent(openPi).setOngoing(true)
                .addAction(android.R.drawable.ic_menu_view, isHiddenByNotification ? "Показать кнопку" : "Скрыть кнопку", togglePi);
        return builder.build();
    }

    @Override public void onDestroy() {
        super.onDestroy(); running = false;
        if (windowManager != null) {
            if (bubbleContainer != null) windowManager.removeView(bubbleContainer);
            if (topBarLayout != null) windowManager.removeView(topBarLayout);
            if (glyphCanvasView != null) windowManager.removeView(glyphCanvasView);
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private int dpToPx(int dp) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()); }
}
