package com.sdk.esc;

/**
 * Activity đăng ký với {@link SocketService} để nhận lệnh từ Control Page.
 */
public interface ControlPageCommandHost {
    /** {@code main} hoặc {@code manual}. */
    String getControlPageWindowState();

    /** Manual: đang hiện In/Hủy sau chụp. */
    boolean isMonoPostCapturePending();

    void onControlPageSetIso(String isoValue);

    void onControlPageSetExposure(String exposureNs);

    void onControlPageSetPrintMode(int mode);

    void onControlPageSetPrinterTest(boolean enabled);

    void onControlPageSetQrPrint(boolean enabled);

    /** Ẩn/hiện nút chụp giữa màn hình chính (click_button_hidden). */
    void onControlPageSetClickButtonHidden(boolean hidden);

    /** Sync khung nếu thiếu rồi chọn theo asset id (Mongo _id). */
    void onControlPageSelectFrame(String frameId);

    /** Sync ảnh phụ nếu thiếu rồi chọn theo asset id. */
    void onControlPageSelectSubPhoto(String subPhotoId);

    /** Trang chính: countdown rồi chụp. Manual: chụp ngay. */
    void onControlPageCapture();

    void onControlPagePrint();

    void onControlPageCancelPostCapture();

    /** Manual → Main (Control Page nút Back). */
    void onControlPageNavigateBackToMain();

    /** Control Page → đánh thức màn hình / mở lại camera sau sleep. */
    void onControlPageWakeDevice();
}
