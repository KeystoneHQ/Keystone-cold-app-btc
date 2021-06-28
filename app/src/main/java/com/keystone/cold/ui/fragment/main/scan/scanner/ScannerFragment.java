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
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.coinlib.exception.CoinNotFindException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.utils.Base43;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.CommonModalBinding;
import com.keystone.cold.databinding.QrcodeScanFragmentBinding;
import com.keystone.cold.databinding.ScannerFragmentBinding;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.QrScanPurpose;
import com.keystone.cold.ui.fragment.main.scan.scanner.bean.ZxingConfig;
import com.keystone.cold.ui.fragment.main.scan.scanner.bean.ZxingConfigBuilder;
import com.keystone.cold.ui.fragment.main.scan.scanner.camera.CameraManager;
import com.keystone.cold.ui.fragment.multisigs.legacy.CollectExpubFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.viewmodel.QrScanViewModel;
import com.keystone.cold.viewmodel.SharedDataViewModel;
import com.keystone.cold.viewmodel.WatchWallet;
import com.keystone.cold.viewmodel.exceptions.CollectExPubException;
import com.keystone.cold.viewmodel.exceptions.InvalidMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.UnknowQrCodeException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;
import com.sparrowwallet.hummingbird.registry.CryptoAccount;
import com.sparrowwallet.hummingbird.registry.CryptoOutput;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.EncoderException;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.keystone.coinlib.Util.getExpubFingerprint;
import static com.keystone.cold.Utilities.IS_SETUP_VAULT;
import static com.keystone.cold.viewmodel.WatchWallet.getWatchWallet;
import static com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel.decodeCaravanWalletFile;
import static com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel.decodeColdCardWalletFile;

public class ScannerFragment extends BaseFragment<ScannerFragmentBinding>
        implements SurfaceHolder.Callback, Host {

    private CameraManager mCameraManager;
    private CaptureHandler mHandler;
    private boolean hasSurface;
    private ZxingConfig mConfig;
    private SurfaceHolder mSurfaceHolder;

    private ModalDialog dialog;
    private WatchWallet watchWallet;

    private ObjectAnimator scanLineAnimator;

    @Override
    protected int setView() {
        return R.layout.qrcode_scan_fragment;
    }

    @Override
    protected void init(View view) {
        watchWallet = getWatchWallet(mActivity);
        mBinding.scanHint.setText(getScanHint());
        boolean isSetupVault = getArguments() != null && getArguments().getBoolean(IS_SETUP_VAULT);
        String purpose = getArguments() != null ? getArguments().getString("purpose") : "";
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mConfig = new ZxingConfigBuilder()
                .setIsFullScreenScan(true)
                .setFrameColor(R.color.colorAccent)
                .createZxingConfig();
        mCameraManager = new CameraManager(mActivity, mConfig);
        mBinding.frameView.setCameraManager(mCameraManager);
        mBinding.frameView.setZxingConfig(mConfig);
        QrScanViewModel.Factory factory = new QrScanViewModel.Factory(mActivity.getApplication(), isSetupVault);
        if (!TextUtils.isEmpty(purpose)) {
            mBinding.scanHint.setVisibility(View.GONE);
        }
        
        scanLineAnimator = ObjectAnimator.ofFloat(mBinding.scanLine, "translationY", 0, 600);
        scanLineAnimator.setDuration(2000L);
        scanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);
    }

    private String getScanHint() {
        switch (watchWallet) {
            case ELECTRUM:
                return getString(R.string.scan_electrum_hint);
            case BLUE:
                return getString(R.string.scan_blue_hint);
            case WASABI:
                return getString(R.string.scan_wasabi_hint);
        }
        return "";
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
    public void handleDecode(Object res) {

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

    private void alert(String message) {
        alert(null, message);
    }

    private void alert(String title, String message) {
        alert(title, message, null);
    }

    private void alert(String title, String message, Runnable run) {
        dialog = ModalDialog.newInstance();
        CommonModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.common_modal, null, false);
        if (title != null) {
            binding.title.setText(title);
        } else {
            binding.title.setText(R.string.scan_failed);
        }
        binding.subTitle.setText(message);
        binding.close.setVisibility(View.GONE);
        binding.confirm.setText(R.string.know);
        binding.confirm.setOnClickListener(v -> {
            dialog.dismiss();
            if (run != null) {
                run.run();
            } else {
                mBinding.scanProgress.setText("");
                if (mHandler != null) {
                    mHandler.restartPreviewAndDecode();
                }
            }
        });
        dialog.setBinding(binding);
        dialog.show(mActivity.getSupportFragmentManager(), "scan fail");
    }
}


