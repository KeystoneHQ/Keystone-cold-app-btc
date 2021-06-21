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

package com.keystone.cold.ui.fragment.main.electrum;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.keystone.cold.R;
import com.keystone.cold.databinding.ExportSdcardModalBinding;
import com.keystone.cold.databinding.ExportXpubGuideBinding;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.SetupVaultActivity;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.Storage;
import com.keystone.cold.viewmodel.GlobalViewModel;
import com.keystone.cold.viewmodel.WatchWallet;

import org.json.JSONObject;

import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;

public class ExportXpubGuideFragment extends BaseFragment<ExportXpubGuideBinding> {

    private WatchWallet watchWallet;
    private JSONObject xpubJson;
    private static final String WASABI_XPUB_FILENAME = "Keystone-Wasabi.json";
    private static final String BTCPAY_XPUB_FILENAME = "Keystone-BTCPay.json";

    @Override
    protected int setView() {
        return R.layout.export_xpub_guide;
    }

    @Override
    protected void init(View view) {
        watchWallet = WatchWallet.getWatchWallet(mActivity);
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mBinding.toolbarTitle.setText(getTitle());
        mBinding.export.setOnClickListener(v -> export());
        if (mActivity instanceof MainActivity) {
            mBinding.skip.setOnClickListener(v -> popBackStack(R.id.assetFragment, false));
        } else {
            mBinding.skip.setText(R.string.export_later);
            mBinding.skip.setOnClickListener(v -> navigate(R.id.action_to_setupCompleteFragment));
        }

        mBinding.text1.setText(getText1());
        mBinding.text2.setText(getText2());
        mBinding.export.setText(getButtonText());
        xpubJson = GlobalViewModel.getXpubInfo(mActivity);
    }

    private void export() {
        switch (watchWallet) {
            case ELECTRUM:
                navigate(R.id.export_electrum_ypub);
                break;
            case KEYSTONE:
                navigate(R.id.export_xpub_keystone);
                break;
            case SPARROW:
            case BTCPAY:
                navigate(R.id.action_to_export_xpub_generic);
                break;
            case WASABI:
                exportXpub(watchWallet);
                break;
            case BLUE:
                navigate(R.id.action_to_export_xpub_blue);
                break;
        }
    }

    private void exportXpub(WatchWallet watchWallet) {
        Storage storage = Storage.createByEnvironment();
        if (storage == null || storage.getExternalDir() == null) {
            showNoSdcardModal(mActivity);
        } else {
            ModalDialog modalDialog = ModalDialog.newInstance();
            ExportSdcardModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                    R.layout.export_sdcard_modal, null, false);

            String fileName = getExportFileName(watchWallet);
            binding.title.setText(R.string.export_xpub_text_file);
            binding.fileName.setText(fileName);
            binding.actionHint.setVisibility(View.GONE);
            binding.cancel.setOnClickListener(vv -> modalDialog.dismiss());
            binding.confirm.setOnClickListener(vv -> {
                modalDialog.dismiss();
                boolean result = writeToSdcard(storage, xpubJson.toString(), fileName);
                Runnable runnable = null;
                if (result) {
                    if (mActivity instanceof SetupVaultActivity) {
                        runnable = () -> navigate(R.id.action_to_setupCompleteFragment);
                    } else {
                        runnable = () -> popBackStack(R.id.assetFragment, false);
                    }
                }
                showExportResult(mActivity, runnable, result);
            });
            modalDialog.setBinding(binding);
            modalDialog.show(mActivity.getSupportFragmentManager(), "");
        }
    }

    private String getExportFileName(WatchWallet watchWallet) {
        if (watchWallet == WatchWallet.WASABI) {
            return WASABI_XPUB_FILENAME;
        } else if (watchWallet == WatchWallet.BTCPAY) {
            return BTCPAY_XPUB_FILENAME;
        }
        return "";
    }

    private int getButtonText() {
        switch (watchWallet) {
            case ELECTRUM:
                return R.string.show_master_public_key_qrcode;
            case WASABI:
                return R.string.export_wallet;
            case KEYSTONE:
            case BTCPAY:
            case SPARROW:
            case BLUE:
                return R.string.show_qrcode;
        }
        return 0;
    }

    private int getTitle() {
        switch (watchWallet) {
            case ELECTRUM:
                return R.string.export_xpub_guide_title_electrum;
            case WASABI:
                return R.string.export_xpub_guide_title_wasabi;
            case BTCPAY:
                return R.string.export_xpub_guide_title_btcpay;
            case KEYSTONE:
                return R.string.export_xpub_guide_title_keystone;
            case BLUE:
                return R.string.export_xpub_guide_title_blue;
            case SPARROW:
                return R.string.export_xpub_guide_title_sparrow;
        }
        return 0;
    }

    private int getText1() {
        switch (watchWallet) {
            case ELECTRUM:
                return R.string.export_xpub_guide_text1_electrum;
            case BTCPAY:
                return R.string.export_xpub_guide_text1_btcpay;
            case WASABI:
                return R.string.export_xpub_guide_text1_wasabi;
            case KEYSTONE:
                return R.string.export_xpub_guide_text1_keystone;
            case BLUE:
                return R.string.export_xpub_guide_text1_blue;
            case SPARROW:
                return R.string.export_xpub_guide_text1_sparrow;
        }
        return 0;
    }

    private int getText2() {
        switch (watchWallet) {
            case ELECTRUM:
                return R.string.export_xpub_guide_text2_electrum;
            case WASABI:
                return R.string.export_xpub_guide_text2_wasabi;
            case BTCPAY:
                return R.string.export_xpub_guide_text2_btcpay;
            case KEYSTONE:
                return R.string.export_xpub_guide_text2_keystone;
            case BLUE:
                return R.string.export_xpub_guide_text2_blue;
            case SPARROW:
                return R.string.export_xpub_guide_text2_sparrow;
        }
        return 0;
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }
}
