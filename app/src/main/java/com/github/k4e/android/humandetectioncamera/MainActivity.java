package com.github.k4e.android.humandetectioncamera;

import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Camera;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.opencv.android.OpenCVLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "HumanDetectionCamera";

    static {
        System.loadLibrary("opencv_java4");
    }

    private Context mContext;
    private HumanDetectionCameraPreview mPreview;
    private int mCameraInfo;
    private int mCameraWidth;
    private int mCameraHeight;
    private Spinner mResolutionSpinner;
    private List<Camera.Size> mSupportedCameraSizes;
    private AdapterView.OnItemSelectedListener mOnResolutionSpinnerSelected;
    private boolean mFlagContinuousUpdateConfirmPassed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;
        mCameraInfo = Camera.CameraInfo.CAMERA_FACING_BACK;
        mCameraWidth = 640;
        mCameraHeight = 480;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            copyAssets("haarcascades");
        } catch (IOException e) {
            e.printStackTrace();
        }
        mResolutionSpinner = findViewById(R.id.resolutionSpinner);
        final Button cameraSwitchButton = findViewById(R.id.cameraSwitchButton);
        final ToggleButton snapshotButton = findViewById(R.id.snapshotButton);
        final Button unsetButton = findViewById(R.id.unsetButton);
        final CheckBox faceCheck = findViewById(R.id.faceCheck);
        final CheckBox bodyCheck = findViewById(R.id.bodyCheck);
        final ToggleButton sightToggle = findViewById(R.id.sightToggle);
        final ToggleButton inpaintToggle = findViewById(R.id.inpaintToggle);
        final List<CompoundButton> flagCompoundButtons = Arrays.asList(
                faceCheck, bodyCheck, sightToggle, inpaintToggle);
        final LinearLayout pvLayer = findViewById(R.id.previewLayer);
        mPreview = createView(
                faceCheck.isChecked(), bodyCheck.isChecked(), sightToggle.isChecked(), inpaintToggle.isChecked());
        snapshotButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final ToggleButton tb = (ToggleButton) v;
                if (mPreview.isPreviewWorking()) {
                    mPreview.stopPreview();
                    tb.setChecked(true);
                } else {
                    onContinuousUpdate(new Runnable() {
                        @Override
                        public void run() {
                            mPreview.startPreview();
                            tb.setChecked(false);
                        }
                    }, new Runnable() {
                        @Override public void run() {
                            tb.setChecked(true);
                        }
                    }, !mPreview.isSomeProcessingEnable());
                }
            }
        });
        cameraSwitchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (CompoundButton cb : flagCompoundButtons) {
                    cb.setChecked(false);
                }
                int cameraInfo = mPreview.getCameraInfo();
                if (cameraInfo == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    mCameraInfo = Camera.CameraInfo.CAMERA_FACING_FRONT;
                } else {
                    mCameraInfo = Camera.CameraInfo.CAMERA_FACING_BACK;
                }
                mPreview.closeCamera();
                pvLayer.removeAllViews();
                mPreview = createView(
                        faceCheck.isChecked(), bodyCheck.isChecked(),
                        sightToggle.isChecked(), inpaintToggle.isChecked());
                pvLayer.addView(mPreview, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                snapshotButton.setChecked(false);
            }
        });
        mOnResolutionSpinnerSelected = new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (mSupportedCameraSizes != null && position < mSupportedCameraSizes.size()) {
                        for (CompoundButton cb : flagCompoundButtons) {
                            cb.setChecked(false);
                        }
                        Camera.Size newSize = mSupportedCameraSizes.get(position);
                        mCameraWidth = newSize.width;
                        mCameraHeight = newSize.height;
                        mPreview.closeCamera();
                        pvLayer.removeAllViews();
                        mPreview = createView(faceCheck.isChecked(), bodyCheck.isChecked(),
                                sightToggle.isChecked(), inpaintToggle.isChecked());
                        pvLayer.addView(mPreview, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        snapshotButton.setChecked(false);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
        };
        unsetButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                mPreview.unsetAll();
                for (CompoundButton cb : flagCompoundButtons) {
                    cb.setChecked(false);
                }
                if (!mPreview.isPreviewWorking()) {
                    mPreview.reprocess();
                }
            }
        });
        faceCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                onContinuousUpdate(new Runnable() {
                    @Override public void run() {
                        mPreview.setFaceDetectionEnable(isChecked);
                        if (!mPreview.isPreviewWorking()) {
                            mPreview.reprocess();
                        }
                    }
                }, new Runnable() {
                    @Override public void run() {
                        buttonView.setChecked(false);
                    }
                }, !isChecked || !mPreview.isPreviewWorking());
            }
        });
        bodyCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                onContinuousUpdate(new Runnable() {
                    @Override public void run() {
                        mPreview.setBodyDetectionEnable(isChecked);
                        if (!mPreview.isPreviewWorking()) {
                            mPreview.reprocess();
                        }
                    }
                }, new Runnable() {
                    @Override public void run() {
                        buttonView.setChecked(false);
                    }
                }, !isChecked || !mPreview.isPreviewWorking());
            }
        });
        sightToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                onContinuousUpdate(new Runnable() {
                    @Override public void run() {
                        mPreview.setSightOn(isChecked);
                        if (!mPreview.isPreviewWorking()) {
                            mPreview.reprocess();
                        }
                    }
                }, new Runnable() {
                    @Override public void run() {
                        buttonView.setChecked(false);
                    }
                }, !isChecked || !mPreview.isPreviewWorking());
            }
        });
        inpaintToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                onContinuousUpdate(new Runnable() {
                    @Override public void run() {
                        mPreview.setInpaintingOn(isChecked);
                        if (!mPreview.isPreviewWorking()) {
                            mPreview.reprocess();
                        }
                    }
                }, new Runnable() {
                    @Override public void run() {
                        buttonView.setChecked(false);
                    }
                }, !isChecked || !mPreview.isPreviewWorking());
            }
        });
        pvLayer.addView(mPreview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void copyAssets(String dirname) throws IOException {
        File dir = new File(getFilesDir(), dirname);
        if(!dir.exists()) {
            dir.mkdirs();
            dir.setReadable(true, false);
            dir.setWritable(true, false);
            dir.setExecutable(true, false);
        }
        byte[] buf = new byte[1024 * 4];
        for(String filename : getAssets().list(dirname)) {
            File file = new File(dir, filename);
            BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
            BufferedInputStream in = new BufferedInputStream(getAssets().open(dirname + "/" + filename));
            int len;
            while((len = in.read(buf)) >= 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            file.setReadable(true, false);
            file.setWritable(true, false);
            file.setExecutable(true, false);
        }
    }

    private HumanDetectionCameraPreview createView(
            boolean faceDetectionEnabled, boolean bodyDetectionEnabled, boolean sightOn, boolean inpaintingOn) {
        String pathToAssets = getFilesDir().getAbsolutePath();
        return new HumanDetectionCameraPreview(
                this,
                mCameraWidth,
                mCameraHeight,
                0,
                pathToAssets + "/haarcascades/haarcascade_frontalface_alt.xml",
                pathToAssets + "/haarcascades/haarcascade_fullbody.xml",
                mCameraInfo,
                faceDetectionEnabled,
                bodyDetectionEnabled,
                sightOn,
                inpaintingOn,
                new Runnable() {
                    @Override public void run() {
                        mSupportedCameraSizes = mPreview.getSupportedCameraSizes(true);
                        Camera.Size currentSize = mPreview.getCameraSize();
                        if (currentSize != null) {
                            Toast.makeText(mContext, String.format("解像度は %d x %d です", currentSize.width, currentSize.height), Toast.LENGTH_LONG)
                                    .show();
                        } else {
                            Toast.makeText(mContext, "解像度が取得できません", Toast.LENGTH_LONG).show();
                        }
                        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, R.layout.support_simple_spinner_dropdown_item);
                        int index = 0;
                        if (mSupportedCameraSizes != null) {
                            for (int i = 0; i < mSupportedCameraSizes.size(); ++i) {
                                Camera.Size size = mSupportedCameraSizes.get(i);
                                adapter.add(String.format("%d x %d", size.width, size.height));
                                if (currentSize != null && size.width == currentSize.width && size.height == currentSize.height) {
                                    index = i;
                                }
                            }
                        } else {
                            adapter.add("解像度変更不可");
                        }
                        mResolutionSpinner.setOnItemSelectedListener(null);
                        mResolutionSpinner.setAdapter(adapter);
                        if (index < adapter.getCount()) {
                            mResolutionSpinner.setSelection(index, false);
                        }
                        if (mOnResolutionSpinnerSelected != null) {
                            mResolutionSpinner.setOnItemSelectedListener(mOnResolutionSpinnerSelected);
                        }
                    }
                }
        );
    }

    private void onContinuousUpdate(
            final Runnable doIfOk, final Runnable doIfCancel, boolean alwaysOk) {
        if (alwaysOk || mFlagContinuousUpdateConfirmPassed) {
            doIfOk.run();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("警告")
                .setMessage("リアルタイムに画像処理を行いますか?\n動作が重くなるかもしれません．")
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        mFlagContinuousUpdateConfirmPassed = true;
                        doIfOk.run();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (doIfCancel != null) {
                            doIfCancel.run();
                        }
                    }
                })
                .show();
    }


}
