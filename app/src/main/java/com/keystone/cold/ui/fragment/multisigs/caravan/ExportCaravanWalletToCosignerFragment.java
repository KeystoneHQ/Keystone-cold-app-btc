/*
 *
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
 *
 */

package com.keystone.cold.ui.fragment.multisigs.caravan;

import static com.keystone.cold.viewmodel.GlobalViewModel.hasSdcard;
import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.keystone.cold.R;
import com.keystone.cold.databinding.ExportWalletToCosignerBinding;
import com.keystone.cold.databinding.ModalWithTwoButtonBinding;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.ui.fragment.multisigs.legacy.MultiSigBaseFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.Storage;

import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ExportCaravanWalletToCosignerFragment extends MultiSigBaseFragment<ExportWalletToCosignerBinding> {
    private MultiSigWalletEntity walletEntity;
    private String walletFileContent;

    @Override
    protected int setView() {
        return R.layout.export_wallet_to_cosigner;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        Bundle data = getArguments();
        Objects.requireNonNull(data);
        String walletFingerprint = data.getString("wallet_fingerprint");
        boolean isSetup = data.getBoolean("setup", false);
        mBinding.skip.setVisibility(View.GONE);
        if (isSetup) {
            mBinding.exportToElectrum.setText(R.string.next);
            mBinding.exportToElectrum.setOnClickListener(v -> navigate(R.id.action_export_caravan_watch_only_guide,getArguments() ));
        } else {
            mBinding.exportToElectrum.setText(R.string.confirm);
            mBinding.exportToElectrum.setOnClickListener(v -> navigateUp());
        }
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mBinding.qrcodeLayout.hint.setVisibility(View.GONE);
        multiSigViewModel.exportCaravanWalletToCosigner(walletFingerprint).observe(this, s -> {
            walletFileContent = s;
            mBinding.qrcodeLayout.qrcode.setData(Hex.toHexString(s.getBytes(StandardCharsets.UTF_8)));
        });

        multiSigViewModel.getWalletEntity(walletFingerprint).observe(this,
                w -> {
                    walletEntity = w;
                    mBinding.verifyCode.setText(getString(R.string.wallet_verification_code, w.getVerifyCode()));
                });
        mBinding.exportToSdcard.setOnClickListener(v -> handleExportWallet());
        mBinding.info.setOnClickListener(v -> ModalDialog.showCommonModal(mActivity,
                getString(R.string.wallet_verify_code),
                getString(R.string.check_verify_code_hint),
                getString(R.string.know),
                null));

    }

    private void handleExportWallet() {
        if (hasSdcard()) {
            String fileName = String.format("export_%s.txt", walletEntity.getWalletName());
            ModalDialog dialog = new ModalDialog();
            ModalWithTwoButtonBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                    R.layout.modal_with_two_button,
                    null, false);
            binding.title.setText(R.string.export_multisig_to_cosigner);
            binding.subTitle.setText(R.string.file_name_label);
            binding.actionHint.setText(fileName);
            binding.actionHint.setTypeface(Typeface.DEFAULT_BOLD);
            binding.left.setText(R.string.cancel);
            binding.left.setOnClickListener(left -> dialog.dismiss());
            binding.right.setText(R.string.export);
            binding.right.setOnClickListener(right -> {
                dialog.dismiss();
                boolean result = writeToSdcard(Storage.createByEnvironment(), walletFileContent, fileName);
                showExportResult(mActivity, null, result);
            });
            dialog.setBinding(binding);
            dialog.show(mActivity.getSupportFragmentManager(), "");
        } else {
            ModalDialog.showCommonModal(mActivity, getString(R.string.no_sdcard),
                    getString(R.string.no_sdcard_hint),getString(R.string.know),null);
        }
    }
}
