package com.github.k4e.android.humandetectioncamera;

import android.content.DialogInterface;
import android.hardware.Camera;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Space;
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

    private HumanDetectionCameraPreview mPreview;
    private boolean mFlagContinuousUpdateConfirmPassed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            copyAssets("haarcascades");
        } catch (IOException e) {
            e.printStackTrace();
        }
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
                Camera.CameraInfo.CAMERA_FACING_BACK,
                faceCheck.isChecked(),
                bodyCheck.isChecked(),
                sightToggle.isChecked(),
                inpaintToggle.isChecked());
        snapshotButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final ToggleButton tb = (ToggleButton) v;
                if (mPreview.isPreviewWorking()) {
                    mPreview.stopPreview();
                    tb.setChecked(true);
                } else {
                    onContinuousUpdate(new Runnable() {
                        @Override public void run() {
                            mPreview.startPreview();
                            tb.setChecked(false);
                        }
                    }, null, !mPreview.isSomeProcessingEnable());
                }
            }
        });
        cameraSwitchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int cameraInfo = mPreview.getCameraInfo();
                int newCameraInfo;
                if (cameraInfo == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    newCameraInfo = Camera.CameraInfo.CAMERA_FACING_FRONT;
                } else {
                    newCameraInfo = Camera.CameraInfo.CAMERA_FACING_BACK;
                }
                mPreview.closeCamera();
                pvLayer.removeAllViews();
                mPreview = createView(
                        newCameraInfo,
                        faceCheck.isChecked(),
                        bodyCheck.isChecked(),
                        sightToggle.isChecked(),
                        inpaintToggle.isChecked());
                pvLayer.addView(mPreview, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                snapshotButton.setChecked(false);
            }
        });
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
                    @Override
                    public void run() {
                        mPreview.setInpaintingOn(isChecked);
                        if (!mPreview.isPreviewWorking()) {
                            mPreview.reprocess();
                        }
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
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
            int cameraInfo,
            boolean faceDetectionEnabled,
            boolean bodyDetectionEnabled,
            boolean sightOn,
            boolean inpaintingOn
    ) {
        String pathToAssets = getFilesDir().getAbsolutePath();
        return new HumanDetectionCameraPreview(
                this,
                640,
                480,
                0,
                pathToAssets + "/haarcascades/haarcascade_frontalface_alt.xml",
                pathToAssets + "/haarcascades/haarcascade_fullbody.xml",
                cameraInfo,
                faceDetectionEnabled,
                bodyDetectionEnabled,
                sightOn,
                inpaintingOn
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
