/*
 * Copyright (c) 2021 Keystone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * in the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.keystone.cold.ui.fragment.main.scan.scanner;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.NonNull;

import com.keystone.cold.R;
import com.keystone.cold.databinding.ScannerFragmentBinding;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.scan.scanner.bean.ZxingConfig;
import com.keystone.cold.ui.fragment.main.scan.scanner.bean.ZxingConfigBuilder;
import com.keystone.cold.ui.fragment.main.scan.scanner.camera.CameraManager;
import com.sparrowwallet.hummingbird.UR;

import org.json.JSONException;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;

public class ScannerFragment extends BaseFragment<ScannerFragmentBinding>
        implements SurfaceHolder.Callback, Host {
    private CameraManager mCameraManager;
    private CaptureHandler mHandler;
    private boolean hasSurface;
    private ZxingConfig mConfig;
    private SurfaceHolder mSurfaceHolder;
    private ArrayList<ScanResultTypes> desiredTypes;

    private ObjectAnimator scanLineAnimator;

    @Override
    protected int setView() {
        return R.layout.scanner_fragment;
    }

    @Override
    protected void init(View view) {

        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mConfig = new ZxingConfigBuilder()
                .setIsFullScreenScan(true)
                .setFrameColor(R.color.colorAccent)
                .createZxingConfig();
        mCameraManager = new CameraManager(mActivity, mConfig);
        mBinding.frameView.setCameraManager(mCameraManager);
        mBinding.frameView.setZxingConfig(mConfig);
        scanLineAnimator = ObjectAnimator.ofFloat(mBinding.scanLine, "translationY", 0, 600);
        scanLineAnimator.setDuration(2000L);
        scanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);

        ArrayList<String> desiredResults = requireArguments().getStringArrayList("desired_results");
        if (desiredResults == null) {
            throw new InvalidParameterException("no desired type passed to scanner");
        } else {
            ArrayList<ScanResultTypes> types = new ArrayList<>();
            desiredResults.forEach(dr -> types.add(ScanResultTypes.valueOf(dr)));
            this.desiredTypes = types;
        }

        mBinding.scanHint.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSurfaceHolder = mBinding.preview.getHolder();
        if (hasSurface) {
            initCamera(mSurfaceHolder);
        } else {
            mSurfaceHolder.addCallback(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mHandler != null) {
            mHandler.quitSynchronously();
            mHandler = null;
        }
        mCameraManager.closeDriver();

        if (!hasSurface) {
            mSurfaceHolder.removeCallback(this);
        }
        scanLineAnimator.cancel();
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        scanLineAnimator.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(surfaceHolder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        hasSurface = false;
    }

    private void initCamera(@NonNull SurfaceHolder surfaceHolder) {
        if (mCameraManager.isOpen()) {
            return;
        }
        try {
            mCameraManager.openDriver(surfaceHolder);
            if (mHandler == null) {
                mHandler = new CaptureHandler(this, mCameraManager);
            }
        } catch (IOException ioe) {
            Log.w(TAG, ioe);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unexpected error initializing camera", e);
        }
    }

    @Override
    public ZxingConfig getConfig() {
        return mConfig;
    }

    @Override
    public void handleDecode(String text) {
        try {
            if (this.desiredTypes.stream().anyMatch(dt -> dt.isType(text))) {
                setScanResult(ScanResult.createJSON(ScanResultTypes.PLAIN_TEXT, text));
                navigateUp();
            } else {
                alert(getString(R.string.scan_failed), getString(R.string.unsupported_qrcode));
                mHandler.restartPreviewAndDecode();
            }
        } catch (JSONException e) {
            alert(getString(R.string.scan_failed), getString(R.string.unsupported_qrcode));
            mHandler.restartPreviewAndDecode();
        }
    }

    @Override
    public void handleDecode(UR ur) {
        try {
            ScanResultTypes srt = this.desiredTypes.stream().filter(dt -> dt.isType(ur)).findFirst().orElse(null);
            if (srt != null) {
                setScanResult(ScanResult.createJSON(srt, Hex.toHexString(ur.getCborBytes())));
                navigateUp();
            } else {
                alert(getString(R.string.scan_failed), getString(R.string.unsupported_qrcode));
                mHandler.restartPreviewAndDecode();
            }
        } catch (JSONException e) {
            alert(getString(R.string.scan_failed), getString(R.string.unsupported_qrcode));
            mHandler.restartPreviewAndDecode();
        }

    }

    @Override
    public void handleProgressPercent(double percent) {
        mActivity.runOnUiThread(() -> mBinding.scanProgress.setText(getString(R.string.scan_progress, (int) Math.floor((percent * 100)) + "%")));
    }

    @Override
    public CameraManager getCameraManager() {
        return mCameraManager;
    }

    @Override
    public Handler getHandler() {
        return mHandler;
    }

}


