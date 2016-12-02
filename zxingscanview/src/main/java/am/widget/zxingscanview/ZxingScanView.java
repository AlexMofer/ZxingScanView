package am.widget.zxingscanview;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.camera.open.OpenCameraInterface;
import com.google.zxing.client.android.decode.BarcodeType;
import com.google.zxing.client.android.decode.ScanHandler;
import com.google.zxing.client.android.manager.AmbientLightManager;
import com.google.zxing.client.android.manager.ScanFeedbackManager;
import com.google.zxing.client.android.compat.Compat;

import java.util.ArrayList;
import java.util.Map;

/**
 * ZxingScanView
 * Created by Alex on 2016/11/24.
 */

public class ZxingScanView extends SurfaceView {

    public static final int ERROR_CODE_NULL = -1;//无错误
    public static final int ERROR_CODE_0 = 0;//开启摄像头失败
    public static final int ERROR_CODE_1 = 1;//无开启摄像头权限
    private CameraManager mCameraManager;
    private AmbientLightManager mAmbientLightManager;
    private ScanFeedbackManager mScanFeedbackManager;
    private int mScanWidth;
    private int mScanHeight;
    private int mCameraId;
    private int mErrorCode = ERROR_CODE_NULL;
    private ArrayList<OnScanListener> mListeners = new ArrayList<>();
    private ArrayList<OnStateListener> mStateListeners = new ArrayList<>();
    private ScanHandler mScanHandler;
    private OnResultListener resultListener = new OnResultListener();
    private ResultPointCallback resultPointCallback = new ResultPointCallback();
    private int mBarcodeType;
    private String mCharacterSet;
    private Map<DecodeHintType, ?> mBaseHints;


    public ZxingScanView(Context context) {
        super(context);
        initView(null);
    }

    public ZxingScanView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(attrs);
    }

    public ZxingScanView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(attrs);
    }

    @TargetApi(21)
    @SuppressWarnings("unused")
    public ZxingScanView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView(attrs);
    }

    private void initView(AttributeSet attrs) {
        int mode = AmbientLightManager.MODE_AUTO;
        int feedback = ScanFeedbackManager.MODE_AUTO;
        String fileName;
        int rawId = NO_ID;
        int scanWidth = ViewGroup.LayoutParams.MATCH_PARENT;
        int scanHeight = ViewGroup.LayoutParams.MATCH_PARENT;
        int cameraId = OpenCameraInterface.NO_REQUESTED_CAMERA;
        int milliseconds = ScanFeedbackManager.DEFAUT_MILLISECONDS;
        int barcodeType = BarcodeType.DEFAULT;
        String characterSet;
        TypedArray custom = getContext().obtainStyledAttributes(attrs, R.styleable.ZxingScanView);
        mode = custom.getInt(R.styleable.ZxingScanView_zsvAmbientLight, mode);
        feedback = custom.getInt(R.styleable.ZxingScanView_zsvFeedback, feedback);
        fileName = custom.getString(R.styleable.ZxingScanView_zsvAudioAssetsFileName);
        rawId = custom.getResourceId(R.styleable.ZxingScanView_zsvAudioRaw, rawId);
        milliseconds = custom.getInteger(R.styleable.ZxingScanView_zsvVibrateMilliseconds,
                milliseconds);
        scanWidth = custom.getLayoutDimension(R.styleable.ZxingScanView_zsvScanWidth, scanWidth);
        scanHeight = custom.getLayoutDimension(R.styleable.ZxingScanView_zsvScanHeight, scanHeight);
        cameraId = custom.getInteger(R.styleable.ZxingScanView_zsvCameraId, cameraId);
        barcodeType = custom.getInt(R.styleable.ZxingScanView_zsvBarcode, barcodeType);
        characterSet = custom.getString(R.styleable.ZxingScanView_zsvCharacterSet);
        custom.recycle();
        setScanWidth(scanWidth);
        setScanHeight(scanHeight);
        setCameraId(cameraId);
        setScanBarcodeType(barcodeType);
        setScanCharacterSet(characterSet);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
        if (isInEditMode())
            return;
        mAmbientLightManager = new AmbientLightManager(getContext(),
                new AmbientLightCallBack(), mode);
        mScanFeedbackManager = new ScanFeedbackManager(getContext());
        setFeedbackMode(feedback);
        setFeedbackAudioAssetsFileName(fileName);
        setFeedbackAudioRawId(rawId);
        setFeedbackVibrateMilliseconds(milliseconds);
        getHolder().addCallback(new CameraCallBack());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAmbientLightManager.release();
        mScanFeedbackManager.release();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_FOCUS:
            case KeyEvent.KEYCODE_CAMERA:
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                setAmbientLightMode(AmbientLightManager.MODE_CLOSE);
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                setAmbientLightMode(AmbientLightManager.MODE_OPEN);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private class CameraCallBack implements SurfaceHolder.Callback {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            openDriver(surfaceHolder);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (mCameraManager != null) {
                final int scanWidth = mScanWidth == ViewGroup.LayoutParams.MATCH_PARENT ? width :
                        (mScanWidth > width ? width : mScanWidth);
                final int scanHeight = mScanHeight == ViewGroup.LayoutParams.MATCH_PARENT ? height :
                        (mScanHeight > height ? height : mScanHeight);
                mCameraManager.setManualFramingRect(scanWidth, scanHeight);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            closeDriver();
        }
    }

    private void openDriver(SurfaceHolder surfaceHolder) {
        notifyListenerPrepareOpen();
        if (surfaceHolder == null)
            return;// 已经销毁
        if (isOpen())
            return;// 摄像头已经打开
        if (Compat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_DENIED) {
            mErrorCode = ERROR_CODE_1;
            notifyListenerError();
            return;
        }
        mCameraManager = new CameraManager(getContext());
        if (mCameraId != OpenCameraInterface.NO_REQUESTED_CAMERA)
            mCameraManager.setManualCameraId(mCameraId);
        final int width = mScanWidth == ViewGroup.LayoutParams.MATCH_PARENT ? getWidth() :
                (mScanWidth > getWidth() ? getWidth() : mScanWidth);
        final int height = mScanHeight == ViewGroup.LayoutParams.MATCH_PARENT ? getHeight() :
                (mScanHeight > getHeight() ? getHeight() : mScanHeight);
        mCameraManager.setManualFramingRect(width, height);
        try {
            mCameraManager.openDriver(surfaceHolder);
            mCameraManager.startPreview();
            mScanHandler = new ScanHandler(resultListener, mBarcodeType, mBaseHints,
                    mCharacterSet, mCameraManager,
                    resultPointCallback);
        } catch (Exception e) {
            mErrorCode = ERROR_CODE_0;
            notifyListenerError();
            return;
        }
        mAmbientLightManager.resume();
        notifyListenerOpened();
    }

    private void closeDriver() {
        notifyListenerPrepareClose();
        mErrorCode = ERROR_CODE_NULL;
        if (mScanHandler != null) {
            mScanHandler.quitSynchronously();
            mScanHandler = null;
        }
        if (mCameraManager != null)
            mCameraManager.stopPreview();
        mAmbientLightManager.pause();
        if (mCameraManager != null)
            mCameraManager.closeDriver();
        mCameraManager = null;
        notifyListenerClosed();
    }

    private class AmbientLightCallBack implements AmbientLightManager.AmbientLightCallBack {
        @Override
        public void onChange(boolean on) {
            if (mCameraManager != null)
                mCameraManager.setTorch(on);
        }
    }

    private void notifyListenerError() {
        for (OnScanListener listener : mListeners) {
            listener.onError(this);
        }
    }

    private void notifyListenerPrepareOpen() {
        for (OnStateListener listener : mStateListeners) {
            listener.onPrepareOpen(this);
        }
    }

    private void notifyListenerOpened() {
        for (OnStateListener listener : mStateListeners) {
            listener.onOpened(this);
        }
    }

    private void notifyListenerPrepareClose() {
        for (OnStateListener listener : mStateListeners) {
            listener.onPrepareClose(this);
        }
    }

    private void notifyListenerClosed() {
        for (OnStateListener listener : mStateListeners) {
            listener.onClosed(this);
        }
    }

    private class OnResultListener implements ScanHandler.OnResultListener {

        @Override
        public void onResult(Result result, Bitmap barcode, float scaleFactor) {
            mScanFeedbackManager.performScanFeedback();
            notifyListenerResult(result, barcode, scaleFactor);
        }
    }

    private void notifyListenerResult(Result result, Bitmap barcode, float scaleFactor) {
        for (OnScanListener listener : mListeners) {
            listener.onResult(this, result, barcode, scaleFactor);
        }
    }

    private class ResultPointCallback implements com.google.zxing.ResultPointCallback {

        @Override
        public void foundPossibleResultPoint(ResultPoint point) {
            notifyListenerFoundPossibleResultPoint(point);
        }
    }

    private void notifyListenerFoundPossibleResultPoint(ResultPoint point) {
        for (OnStateListener listener : mStateListeners) {
            listener.foundPossibleResultPoint(this, point);
        }
    }

    public void open() {
        openDriver(getHolder());
    }

    /**
     * 扫描是否已打开
     *
     * @return 是否打开
     */
    public boolean isOpen() {
        return mCameraManager != null && mCameraManager.isOpen();
    }

    /**
     * 添加扫描监听
     *
     * @param listener 监听器
     */
    public void addOnScanListener(OnScanListener listener) {
        if (listener != null)
            mListeners.add(listener);
    }

    /**
     * 移除扫描监听器
     *
     * @param listener 监听器
     * @return 是否移除成功
     */
    public boolean removeOnScanListener(OnScanListener listener) {
        return listener != null && mListeners.remove(listener);
    }

    /**
     * 添加状态监听
     *
     * @param listener 状态监听
     */
    public void addOnStateListener(OnStateListener listener) {
        if (listener != null)
            mStateListeners.add(listener);
    }

    /**
     * 移除状态监听
     *
     * @param listener 状态监听
     * @return 是否成功移除
     */
    public boolean removeOnStateListener(OnStateListener listener) {
        return listener != null && mStateListeners.remove(listener);
    }

    /**
     * 设置背光模式
     *
     * @param mode 背光模式，可用参数：{@link AmbientLightManager#MODE_AUTO}、
     *             {@link AmbientLightManager#MODE_OPEN}、{@link AmbientLightManager#MODE_CLOSE}
     */
    public void setAmbientLightMode(int mode) {
        mAmbientLightManager.setMode(mode);
    }

    /**
     * 设置扫描反馈模式
     *
     * @param mode 扫描反馈模式，可用参数：{@link ScanFeedbackManager#MODE_AUTO}、
     *             {@link ScanFeedbackManager#MODE_AUDIO_ONLY}、
     *             {@link ScanFeedbackManager#MODE_VIBRATOR_ONLY}、
     *             {@link ScanFeedbackManager#MODE_AUDIO_VIBRATOR}
     */
    public void setFeedbackMode(int mode) {
        mScanFeedbackManager.setMode(mode);
    }

    /**
     * 设置音频Assets文件名
     *
     * @param fileName 文件名
     */
    public void setFeedbackAudioAssetsFileName(String fileName) {
        mScanFeedbackManager.setAudioAssetsFileName(fileName);
    }

    /**
     * 设置音频资源ID
     *
     * @param id 资源ID
     */
    public void setFeedbackAudioRawId(int id) {
        mScanFeedbackManager.setAudioRawId(id);
    }

    /**
     * 设置震动时长
     *
     * @param milliseconds 时长
     */
    public void setFeedbackVibrateMilliseconds(long milliseconds) {
        mScanFeedbackManager.setVibrateMilliseconds(milliseconds);
    }

    /**
     * 设置扫描宽度
     * 下次创建CameraManager时生效
     *
     * @param width 扫描宽度
     */
    public void setScanWidth(int width) {
        mScanWidth = width;
    }

    /**
     * 设置扫描高度
     * 下次创建CameraManager时生效
     *
     * @param height 扫描高度
     */
    public void setScanHeight(int height) {
        mScanHeight = height;
    }

    /**
     * 设置摄像头ID
     * 下次创建CameraManager时生效
     *
     * @param id 摄像头ID
     */
    public void setCameraId(int id) {
        mCameraId = id;
    }

    /**
     * 获取错误代码
     *
     * @return 错误代码
     */
    public int getErrorCode() {
        return mErrorCode;
    }

    /**
     * 获取扫描宽度
     *
     * @return 扫描宽度
     */
    public int getScanWidth() {
        return mScanWidth;
    }

    /**
     * 获取扫描高度
     *
     * @return 扫描高度
     */
    public int getScanHeight() {
        return mScanHeight;
    }

    /**
     * 设置扫码类型
     *
     * @param type 类型
     */
    public void setScanBarcodeType(int type) {
        mBarcodeType = type;
    }


    public void setScanCharacterSet(String characterSet) {
        mCharacterSet = characterSet;
    }

    @SuppressWarnings("unused")
    public void setScanBaseHints(Map<DecodeHintType, ?> hints) {
        mBaseHints = hints;
    }

    /**
     * 重新开始扫描
     */
    @SuppressWarnings("unused")
    public void restartScan() {
        if (mScanHandler != null) {
            mScanHandler.restartScan();
        }
    }

    /**
     * 一段时间后重新你开始扫描
     *
     * @param delay 时间
     */
    @SuppressWarnings("unused")
    public void restartScanDelay(long delay) {
        if (mScanHandler != null) {
            mScanHandler.restartScanDelay(delay);
        }
    }

    /**
     * 扫描监听
     */
    public interface OnScanListener {
        /**
         * 出现错误
         *
         * @param scanView ZxingScanView
         */
        void onError(ZxingScanView scanView);

        /**
         * 扫描结果
         *
         * @param scanView    ZxingScanView
         * @param result      结果
         * @param barcode     图片
         * @param scaleFactor 缩放比
         */
        void onResult(ZxingScanView scanView, Result result, Bitmap barcode, float scaleFactor);
    }

    /**
     * 状态监听器
     */
    public interface OnStateListener {
        void onPrepareOpen(ZxingScanView scanView);

        void onOpened(ZxingScanView scanView);

        void foundPossibleResultPoint(ZxingScanView scanView, ResultPoint point);

        void onPrepareClose(ZxingScanView scanView);

        void onClosed(ZxingScanView scanView);
    }

    /**
     * 为结果图添加识别效果
     *
     * @param result      扫描结果
     * @param barcode     结果图片
     * @param scaleFactor 缩放比
     * @param color       颜色
     */
    @SuppressWarnings("unused")
    public static void addResultPoints(Result result, Bitmap barcode, float scaleFactor, int color) {
        ResultPoint[] points = result.getResultPoints();
        if (points != null && points.length > 0) {
            Canvas canvas = new Canvas(barcode);
            Paint paint = new Paint();
            paint.setColor(color);
            if (points.length == 2) {
                paint.setStrokeWidth(4.0f);
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
            } else if (points.length == 4 &&
                    (result.getBarcodeFormat() == BarcodeFormat.UPC_A ||
                            result.getBarcodeFormat() == BarcodeFormat.EAN_13)) {
                // Hacky special case -- draw two lines, for the barcode and metadata
                drawLine(canvas, paint, points[0], points[1], scaleFactor);
                drawLine(canvas, paint, points[2], points[3], scaleFactor);
            } else {
                paint.setStrokeWidth(10.0f);
                for (ResultPoint point : points) {
                    if (point != null) {
                        canvas.drawPoint(scaleFactor * point.getX(), scaleFactor * point.getY(), paint);
                    }
                }
            }
        }
    }

    private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b, float scaleFactor) {
        if (a != null && b != null) {
            canvas.drawLine(scaleFactor * a.getX(),
                    scaleFactor * a.getY(),
                    scaleFactor * b.getX(),
                    scaleFactor * b.getY(),
                    paint);
        }
    }
}