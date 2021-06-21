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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.R;
import com.keystone.cold.databinding.CommonModalBinding;
import com.keystone.cold.databinding.ElectrumExportBinding;
import com.keystone.cold.databinding.ExportSdcardModalBinding;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.SetupVaultActivity;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.Storage;
import com.keystone.cold.viewmodel.GlobalViewModel;

import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;


public class ElectrumExportFragment extends BaseFragment<ElectrumExportBinding> {

    private String exPub;

    @Override
    protected int setView() {
        return R.layout.electrum_export;
    }

    @Override
    protected void init(View view) {
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        GlobalViewModel viewModel;
        if (mActivity instanceof SetupVaultActivity) {
            viewModel = ViewModelProviders.of(this).get(GlobalViewModel.class);
        } else {
            viewModel = ViewModelProviders.of(mActivity).get(GlobalViewModel.class);
        }
        viewModel.getExtendPublicKey().observe(this, s -> {
            if (!TextUtils.isEmpty(s)) {
                exPub = s;
                mBinding.qrcode.setData(s);
                mBinding.expub.setText(s);
            }
        });
        mBinding.info.setOnClickListener(v -> showElectrumInfo());
        mBinding.addressType.setText(getString(R.string.master_xpub,
                GlobalViewModel.getAddressFormat(mActivity)));
        mBinding.done.setOnClickListener(v -> {
            if (mActivity instanceof SetupVaultActivity) {
                navigate(R.id.action_to_setupCompleteFragment);
            } else {
                MainActivity activity = (MainActivity) mActivity;
                activity.getNavController().popBackStack(R.id.assetFragment, false);
            }
        });
        mBinding.exportToSdcard.setOnClickListener(v -> {
            Storage storage = Storage.createByEnvironment();
            if (storage == null || storage.getExternalDir() == null) {
                showNoSdcardModal(mActivity);
            } else {
                ModalDialog modalDialog = ModalDialog.newInstance();
                ExportSdcardModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                        R.layout.export_sdcard_modal, null, false);
                binding.title.setText(R.string.export_xpub_text_file);
                binding.fileName.setText(getFileName());
                binding.cancel.setOnClickListener(vv -> modalDialog.dismiss());
                binding.confirm.setOnClickListener(vv -> {
                    modalDialog.dismiss();
                    boolean result = writeToSdcard(storage, exPub, getFileName());
                    showExportResult(mActivity, null, result);
                });
                modalDialog.setBinding(binding);
                modalDialog.show(mActivity.getSupportFragmentManager(), "");
            }
        });
    }

    private void showElectrumInfo() {
        ModalDialog modalDialog = ModalDialog.newInstance();
        CommonModalBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(mActivity), R.layout.common_modal,
                null, false);
        binding.title.setText(R.string.electrum_import_xpub_guide_title);
        binding.subTitle.setText(R.string.export_xpub_guide_text2_electrum_info);
        binding.subTitle.setGravity(Gravity.START);
        binding.close.setVisibility(View.GONE);
        binding.confirm.setText(R.string.know);
        binding.confirm.setOnClickListener(vv -> modalDialog.dismiss());
        modalDialog.setBinding(binding);
        modalDialog.show(mActivity.getSupportFragmentManager(), "");
    }

    private String getFileName() {
        Coins.Account account = GlobalViewModel.getAccount(mActivity);
        switch (account) {
            case P2WPKH:
            case P2WPKH_TESTNET:
                return "p2wpkh-pubkey.txt";
            case P2SH_P2WPKH:
            case P2SH_P2WPKH_TESTNET:
                return "p2sh-p2wpkh-pubkey.txt";
            case P2PKH:
            case P2PKH_TESTNET:
                return "p2pkh-pubkey.txt";
        }
        return "p2sh-p2wpkh-pubkey.txt";
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }
}
