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

import static com.keystone.coinlib.accounts.Account.MULTI_P2SH;
import static com.keystone.coinlib.accounts.Account.MULTI_P2SH_P2WSH;
import static com.keystone.coinlib.accounts.Account.MULTI_P2SH_P2WSH_TEST;
import static com.keystone.coinlib.accounts.Account.MULTI_P2SH_TEST;
import static com.keystone.coinlib.accounts.Account.MULTI_P2WSH;
import static com.keystone.coinlib.accounts.Account.MULTI_P2WSH_TEST;
import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;

import android.graphics.Typeface;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.CommonModalBinding;
import com.keystone.cold.databinding.ExportCaravanExpubBinding;
import com.keystone.cold.databinding.ModalWithTwoButtonBinding;
import com.keystone.cold.databinding.SwitchXpubBottomSheetBinding;
import com.keystone.cold.ui.fragment.multisigs.legacy.MultiSigBaseFragment;
import com.keystone.cold.ui.modal.ExportToSdcardDialog;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.Storage;

public class ExportCaravanExpubFragment extends MultiSigBaseFragment<ExportCaravanExpubBinding>
        implements Toolbar.OnMenuItemClickListener {
    public static final String TAG = "ExportCaravanExpubFragment";
    private Account account;

    @Override
    protected int setView() {
        return R.layout.export_caravan_expub;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mBinding.toolbar.inflateMenu(R.menu.export_all);
        mBinding.toolbar.setOnMenuItemClickListener(this);
        account = Utilities.isMainNet(mActivity) ? MULTI_P2WSH : MULTI_P2WSH_TEST;
        updateUI();
        mBinding.addressType.setOnClickListener(v -> showBottomSheetMenu());
        mBinding.exportToSdcard.setOnClickListener(v -> exportToSdcard());
    }

    private void exportToSdcard() {
        Storage storage = Storage.createByEnvironment();
        if (storage == null || storage.getExternalDir() == null) {
            showNoSdcardModal(mActivity);
        } else {
            String fileName = multiSigViewModel.getExportXpubFileName(account);
            ModalDialog dialog = new ModalDialog();
            ModalWithTwoButtonBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                    R.layout.modal_with_two_button, null, false);
            binding.title.setText(R.string.export_multisig_xpub);
            binding.subTitle.setText(R.string.file_name_label);
            binding.actionHint.setText(fileName);
            binding.actionHint.setTypeface(Typeface.DEFAULT_BOLD);
            binding.left.setText(R.string.cancel);
            binding.left.setOnClickListener(v -> dialog.dismiss());
            binding.right.setText(R.string.export);
            binding.right.setOnClickListener(v -> {
                dialog.dismiss();
                boolean result = writeToSdcard(storage, multiSigViewModel.getExportXpubInfo(account), fileName);
                showExportResult(mActivity, null, result);
            });
            dialog.setBinding(binding);
            dialog.show(mActivity.getSupportFragmentManager(), "");
        }
    }

    private void exportAllToSdcard() {
        Storage storage = Storage.createByEnvironment();
        if (storage == null || storage.getExternalDir() == null) {
            showNoSdcardModal(mActivity);
        } else {
            String fileName = multiSigViewModel.getExportAllXpubFileName();
            boolean success = writeToSdcard(storage, multiSigViewModel.getExportAllXpubInfo(), fileName);
            ExportToSdcardDialog dialog = ExportToSdcardDialog.newInstance(fileName, success);
            dialog.show(mActivity.getSupportFragmentManager(), "");
            new Handler().postDelayed(dialog::dismiss, 1000);
        }
    }


    private void updateUI() {
        String accountType = multiSigViewModel.getAddressTypeString(account);
        String xpub = multiSigViewModel.getXPub(account);
        mBinding.addressType.setText(String.format("%s ", accountType));
        mBinding.expub.setText(xpub);
        mBinding.path.setText(String.format("(%s)", account.getPath()));
        mBinding.qrcode.setData(multiSigViewModel.getCryptoAccount(account).toUR().toString().toUpperCase());
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_export_all) {
            ModalDialog dialog = new ModalDialog();
            CommonModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                    R.layout.common_modal, null, false);
            binding.title.setText(getString(R.string.extend_pubkey));
            binding.subTitle.setText(getAllExtendPubkeyInfo());
            binding.subTitle.setGravity(Gravity.START);
            binding.close.setOnClickListener(v -> dialog.dismiss());
            binding.confirm.setText(getString(R.string.export));
            binding.confirm.setOnClickListener(v -> {
                exportAllToSdcard();
                dialog.dismiss();
            });
            dialog.setBinding(binding);
            dialog.show(mActivity.getSupportFragmentManager(), "");

        }
        return true;
    }

    private String getAllExtendPubkeyInfo() {
        StringBuilder info = new StringBuilder("<br>");
        Account[] accounts =
                Utilities.isMainNet(mActivity) ?
                        new Account[]{MULTI_P2WSH, MULTI_P2SH_P2WSH, MULTI_P2SH}
                        : new Account[]{MULTI_P2WSH_TEST, MULTI_P2SH_P2WSH_TEST, MULTI_P2SH_TEST};
        for (Account a : accounts) {
            info.append(String.format("%s(%s)", multiSigViewModel.getAddressTypeString(a), a.getScript())).append("<br>")
                    .append(a.getPath()).append("<br>")
                    .append(multiSigViewModel.getXPub(a)).append("<br><br>");
        }

        return info.toString();
    }

    private void showBottomSheetMenu() {
        BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
        SwitchXpubBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.switch_xpub_bottom_sheet, null, false);
        refreshCheckedStatus(binding.getRoot());
        if (Utilities.isMainNet(mActivity)) {
            binding.nativeSegwit.setOnClickListener(v -> onXpubSwitch(dialog, MultiSig.P2WSH));
            binding.nestedSegeit.setOnClickListener(v -> onXpubSwitch(dialog, MultiSig.P2SH_P2WSH));
            binding.legacy.setOnClickListener(v -> onXpubSwitch(dialog, MultiSig.P2SH));
        } else {
            binding.nativeSegwit.setOnClickListener(v -> onXpubSwitch(dialog, MultiSig.P2WSH_TEST));
            binding.nestedSegeit.setOnClickListener(v -> onXpubSwitch(dialog, MultiSig.P2SH_P2WSH_TEST));
            binding.legacy.setOnClickListener(v -> onXpubSwitch(dialog, MultiSig.P2SH_TEST));
        }
        dialog.setContentView(binding.getRoot());
        dialog.show();
    }

    private void onXpubSwitch(BottomSheetDialog dialog,
                              Account account) {
        this.account = account;
        dialog.dismiss();
        updateUI();
    }

    private void refreshCheckedStatus(View view) {
        for (Account value : MultiSig.values()) {
            view.findViewWithTag(value.getScript()).setVisibility(View.GONE);
        }
        view.findViewWithTag(account.getScript()).setVisibility(View.VISIBLE);
    }
}
