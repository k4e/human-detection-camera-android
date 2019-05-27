package com.github.k4e.android.humandetectioncamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.photo.Photo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HumanDetectionCameraPreview extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final String TAG = MainActivity.TAG;
    private static final int TARGET_FACE = 0;
    private static final int TARGET_BODY = 1;
    private final int mPreviewWidth;
    private final int mPreviewHeight;
    private final int mDisplayOrientation;
    private final String mFaceCascadeFilename;
    private final String mBodyCascadeFilename;
    private final CascadeClassifier mFaceDetector;
    private final CascadeClassifier mBodyDetector;
    private final List<Pair<Integer, RectF>> mTargets;
    private Integer mCameraInfo;
    private Boolean mFaceDetectionEnable;
    private Boolean mBodyDetectionEnable;
    private Boolean mSightOn;
    private Boolean mInpaintingOn;
    private Boolean mPreviewWorking;
    private Camera mCamera;
    private Bitmap mOriginalBitmap;
    private Bitmap mProcessedBitmap;
    private Mat mImageMat;
    private Mat mMaskMat;

    public HumanDetectionCameraPreview(
            Context context,
            int previewWidth,
            int previewHeight,
            int displayOrientation,
            String faceCascadeFilename,
            String bodyCascadeFilename,
            int cameraInfo,
            boolean faceDetectionEnable,
            boolean bodyDetectionEnable,
            boolean sightOn,
            boolean inpaintingOn
    ) {
        super(context);
        setWillNotDraw(false);
        getHolder().addCallback(this);
        mPreviewWidth = previewWidth;
        mPreviewHeight = previewHeight;
        mDisplayOrientation = displayOrientation;
        mFaceCascadeFilename = faceCascadeFilename;
        mBodyCascadeFilename = bodyCascadeFilename;
        mFaceDetector = new CascadeClassifier();
        mBodyDetector = new CascadeClassifier();
        mTargets = new ArrayList<>();
        mCameraInfo = cameraInfo;
        mFaceDetectionEnable = faceDetectionEnable;
        mBodyDetectionEnable = bodyDetectionEnable;
        mSightOn = sightOn;
        mInpaintingOn = inpaintingOn;
        mPreviewWorking = false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mFaceDetector.load(mFaceCascadeFilename);
            mBodyDetector.load(mBodyCascadeFilename);
            if (mCameraInfo <= Camera.getNumberOfCameras()) {
                openCamera();
                setZOrderOnTop(true);
                getHolder().setFormat(PixelFormat.TRANSLUCENT);
            } else {
                Log.d(TAG, "Cannot bind camera");
                Toast.makeText(getContext(), "カメラをバインドできません", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        cvCleanUp();
        startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        cvCleanUp();
        closeCamera();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        int pvWidth = camera.getParameters().getPreviewSize().width;
        int pvHeight = camera.getParameters().getPreviewSize().height;
        mOriginalBitmap = yuvToBitmap(data, pvWidth, pvHeight, mDisplayOrientation);
        processImage(pvWidth, pvHeight);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Paint bitmapPaint = new Paint();
        int width = getWidth();
        int height = getHeight();
        Bitmap bitmap = mProcessedBitmap != null ? mProcessedBitmap : mOriginalBitmap;
        if (bitmap != null) {
            canvas.drawBitmap(mProcessedBitmap, null,
                    new android.graphics.Rect(0, 0, canvas.getWidth(), canvas.getHeight()), bitmapPaint);
        }
        if (isSightOn()) {
            Paint greenPaint = new Paint();
            greenPaint.setColor(Color.GREEN);
            greenPaint.setStyle(Paint.Style.STROKE);
            greenPaint.setStrokeWidth(4f);
            Paint redPaint = new Paint();
            redPaint.setColor(Color.RED);
            redPaint.setStyle(Paint.Style.STROKE);
            redPaint.setStrokeWidth(4f);
            for (Pair<Integer, RectF> target : mTargets) {
                Paint p = target.first == TARGET_FACE ? greenPaint : redPaint;
                RectF tr = target.second;
                RectF sr = new RectF(width * tr.left, height * tr.top, width * tr.right, height * tr.bottom);
                canvas.drawRect(sr, p);
            }
        }
    }

    public int getCameraInfo() {
        return mCameraInfo;
    }

    public boolean isFaceDetectionEnable() {
        return mFaceDetectionEnable;
    }

    public boolean isBodyDetectionEnable() {
        return mBodyDetectionEnable;
    }

    public boolean isSightOn() {
        return mSightOn;
    }

    public boolean isInpaintingOn() {
        return mInpaintingOn;
    }

    public boolean isSomeProcessingEnable() {
        return mFaceDetectionEnable || mBodyDetectionEnable || mSightOn || mInpaintingOn;
    }

    public boolean isPreviewWorking() {
        return mPreviewWorking;
    }

    public void setFaceDetectionEnable(boolean b) {
        mFaceDetectionEnable = b;
    }

    public void setBodyDetectionEnable(boolean b) {
        mBodyDetectionEnable = b;
    }

    public void setSightOn(boolean b) {
        mSightOn = b;
    }

    public void setInpaintingOn(boolean b) {
        mInpaintingOn = b;
    }

    public void unsetAll() {
        mFaceDetectionEnable = mBodyDetectionEnable = mSightOn = mInpaintingOn = false;
    }

    public void startPreview() {
        if (mCamera != null) {
            mCamera.startPreview();
            mPreviewWorking = true;
        }
    }

    public void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mPreviewWorking = false;
        }
    }

    public void reprocess() {
        int pvWidth = mCamera.getParameters().getPreviewSize().width;
        int pvHeight = mCamera.getParameters().getPreviewSize().height;
        processImage(pvWidth, pvHeight);
        invalidate();
    }

    public void closeCamera() {
        if (mCamera != null) {
            stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    private void openCamera() throws IOException {
        mCamera = Camera.open(mCameraInfo);
        Camera.Parameters params = mCamera.getParameters();
        if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            Log.d(TAG, "Set continuous focus mode");
        } else {
            Log.d(TAG, "Continuous focus is not supported");
        }
        params.setPreviewSize(mPreviewWidth, mPreviewHeight);
        mCamera.setParameters(params);
        mCamera.setDisplayOrientation(mDisplayOrientation);
        mCamera.setPreviewDisplay(getHolder());
        mCamera.setPreviewCallback(this);
    }

    private Bitmap yuvToBitmap(byte[] data, int width, int height, int rotation) {
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 80, bout);
        byte[] jdata = bout.toByteArray();
        BitmapFactory.Options bfOpts = new BitmapFactory.Options();
        bfOpts.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bfOpts);
        return bitmap;
    }

    private void processImage(int pvWidth, int pvHeight) {
        if (mOriginalBitmap == null) {
            return;
        }
        if (mImageMat == null) {
            mImageMat = new Mat();
        }
        Utils.bitmapToMat(mOriginalBitmap, mImageMat);
        if ((mDisplayOrientation - 90) % 180 == 0) {
            int oldWith = pvWidth;
            pvWidth = pvHeight;
            pvHeight = oldWith;
            Core.flip(mImageMat.t(), mImageMat, 0);
        }
        List<Rect> faceRects;
        if (isFaceDetectionEnable()) {
            MatOfRect faceMor = new MatOfRect();
            mFaceDetector.detectMultiScale(mImageMat, faceMor);
            faceRects = Collections.unmodifiableList(faceMor.toList());
        } else {
            faceRects = Collections.emptyList();
        }
        int faceCount = faceRects.size();
        List<Rect> bodyRects;
        if (isBodyDetectionEnable()) {
            MatOfRect bodyMor = new MatOfRect();
            mBodyDetector.detectMultiScale(mImageMat, bodyMor);
            bodyRects = Collections.unmodifiableList(bodyMor.toList());
        } else {
            bodyRects = Collections.emptyList();
        }
        int bodyCount = bodyRects.size();
        boolean somethingDetected = faceCount > 0 || bodyCount > 0;
        if (somethingDetected) {
            Log.d(TAG, "Detect " + faceCount + " face(s), " + bodyCount + " body(s)");
        }
        mTargets.clear();
        if (somethingDetected) {
            for (Rect rect : faceRects) {
                addTarget(pvWidth, pvHeight, rect, TARGET_FACE);
            }
            for (Rect rect : bodyRects) {
                addTarget(pvWidth, pvHeight, rect, TARGET_BODY);
            }
        }
        mMaskMat = Mat.zeros(pvHeight, pvWidth, CvType.CV_8UC1);
        if (mProcessedBitmap == null) {
            mProcessedBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Bitmap.Config.ARGB_8888);
        }
        if (isInpaintingOn() && somethingDetected) {
            for (Rect rect : faceRects) {
                addMask(rect);
            }
            for (Rect rect : bodyRects) {
                addMask(rect);
            }
            Mat mInpaintInMat = new Mat(mImageMat.width(), mImageMat.height(), CvType.CV_8UC3);
            Mat mInpaintOutMat = new Mat(mImageMat.width(), mImageMat.height(), CvType.CV_8UC3);
            Imgproc.cvtColor(mImageMat, mInpaintInMat, Imgproc.COLOR_BGRA2BGR);
            Photo.inpaint(mInpaintInMat, mMaskMat, mInpaintOutMat, 1, Photo.INPAINT_TELEA);
            Utils.matToBitmap(mInpaintOutMat, mProcessedBitmap);
        } else {
            Utils.matToBitmap(mImageMat, mProcessedBitmap);
        }
    }

    private void addTarget(int pvWidth, int pvHeight, Rect rect, int targetType) {
        int x = rect.x;
        int y = rect.y;
        int w = rect.width;
        int h = rect.height;
        float left = Integer.valueOf(x).floatValue() / pvWidth;
        float top = Integer.valueOf(y).floatValue() / pvHeight;
        float right = left + (Integer.valueOf(w).floatValue() / pvWidth);
        float bottom = top + (Integer.valueOf(h).floatValue() / pvHeight);
        mTargets.add(Pair.create(targetType, new RectF(left, top, right, bottom)));
    }

    private void addMask(Rect rect) {
        int x = rect.x;
        int y = rect.y;
        int w = rect.width;
        int h = rect.height;
        int cx = x + w / 2;
        int cy = y + h / 2;
        Imgproc.ellipse(mMaskMat, new Point(cx, cy), new Size(w / 2, h / 2), 0, 0, 360, Scalar.all(255.), -1);
    }

    private void cvCleanUp() {
        if (mImageMat != null) {
            mImageMat.release();
            mImageMat = null;
        }
        if (mMaskMat != null) {
            mMaskMat.release();
            mMaskMat = null;
        }
        if (mProcessedBitmap != null) {
            if (!mProcessedBitmap.isRecycled()) {
                mProcessedBitmap.recycle();
            }
            mProcessedBitmap = null;
        }
        mTargets.clear();
    }
}
