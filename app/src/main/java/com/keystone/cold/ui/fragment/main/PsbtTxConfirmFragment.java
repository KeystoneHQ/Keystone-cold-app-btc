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

package com.keystone.cold.ui.fragment.main;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.coins.AbsTx;
import com.keystone.cold.R;
import com.keystone.cold.databinding.ExportSdcardModalBinding;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.ui.fragment.main.electrum.UnsignedTxFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.ui.views.AuthenticateModal;
import com.keystone.cold.update.utils.Storage;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;
import com.keystone.cold.viewmodel.WatchWallet;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;

import org.spongycastle.util.encoders.Base64;

import java.util.stream.Stream;

import static com.keystone.cold.ui.fragment.main.PsbtBroadcastTxFragment.KEY_TXID;
import static com.keystone.cold.ui.fragment.main.PsbtBroadcastTxFragment.KEY_MULTISIG_MODE;
import static com.keystone.cold.viewmodel.GlobalViewModel.hasSdcard;
import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;

public class PsbtTxConfirmFragment extends UnsignedTxFragment {

    private String psbtBase64;
    private boolean multisig;
    private MultiSigMode multiSigMode;

    @Override
    protected void init(View view) {
        super.init(view);
    }

    public static void showExportPsbtDialog(AppCompatActivity activity, TxEntity tx,
                                            Runnable onExportSuccess) {

        String signStatus = tx.getSignStatus();
        boolean signed = true;
        if (!TextUtils.isEmpty(signStatus)) {
            String[] splits = signStatus.split("-");
            int sigNumber = Integer.parseInt(splits[0]);
            int reqSigNumber = Integer.parseInt(splits[1]);
            signed = sigNumber >= reqSigNumber;
        }

        showExportPsbtDialog(activity, signed,
                tx.getTxId(), tx.getSignedHex(), onExportSuccess);
    }

    public static void showExportPsbtDialog(AppCompatActivity activity, CasaSignature tx,
                                            Runnable onExportSuccess) {

        String signStatus = tx.getSignStatus();
        boolean signed = true;
        if (!TextUtils.isEmpty(signStatus)) {
            String[] splits = signStatus.split("-");
            int sigNumber = Integer.parseInt(splits[0]);
            int reqSigNumber = Integer.parseInt(splits[1]);
            signed = sigNumber >= reqSigNumber;
        }

        showExportPsbtDialog(activity, signed,
                tx.getTxId(), tx.getSignedHex(), onExportSuccess);
    }

    public static void showExportPsbtDialog(AppCompatActivity activity, boolean signed, String txId, String psbt,
                                            Runnable onExportSuccess) {
        ModalDialog modalDialog = ModalDialog.newInstance();
        ExportSdcardModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(activity),
                R.layout.export_sdcard_modal, null, false);
        LegacyMultiSigViewModel vm = ViewModelProviders.of(activity).get(LegacyMultiSigViewModel.class);
        String fileName;
        if (signed) {
            fileName = "signed_" + txId.substring(0, 8) + ".psbt";
        } else {
            if (txId.startsWith("unknown_txid")) {
                fileName = "part_" + vm.getXfp() + ".psbt";
            } else {
                fileName = "part_" + txId.substring(0, 8) + "_" + vm.getXfp() + ".psbt";
            }
        }
        if (signed) {
            binding.title.setText(R.string.export_signed_txn);
        } else {
            binding.title.setText(R.string.export_file);
        }
        binding.fileName.setText(fileName);
        binding.actionHint.setVisibility(View.GONE);
        binding.cancel.setOnClickListener(vv -> modalDialog.dismiss());
        binding.confirm.setOnClickListener(vv -> {
            modalDialog.dismiss();
            if (hasSdcard()) {
                Storage storage = Storage.createByEnvironment();
                boolean result = writeToSdcard(storage, Base64.decode(psbt), fileName);
                showExportResult(activity, onExportSuccess, result);
            } else {
                showNoSdcardModal(activity);
            }
        });
        modalDialog.setBinding(binding);
        modalDialog.show(activity.getSupportFragmentManager(), "");
    }

    @Override
    protected AuthenticateModal.OnVerify signWithVerifyInfo() {
        return token -> {
            viewModel.setToken(token);
            viewModel.handleSignPsbt(psbtBase64);
            subscribeSignState();
        };
    }

    @Override
    protected void parseTx() {
        Bundle bundle = requireArguments();
        multisig = bundle.getBoolean("multisig");
        psbtBase64 = bundle.getString("psbt_base64");
        viewModel.setIsMultisig(multisig);
        if (multisig) {
            multiSigMode = MultiSigMode.valueOf(bundle.getString("multisig_mode"));
            viewModel.setMultisigMode(multiSigMode);
        }
        viewModel.parsePsbtBase64(psbtBase64, multisig);
        try {
            String[] paths = viewModel.getPath().split(AbsTx.SEPARATOR);
            String[] distinctPaths = Stream.of(paths).distinct().toArray(String[]::new);
            String first = distinctPaths[0];
            String path = first.replace("m/", "");
            String[] index = path.split("/");
            if (index[1].equals("1'")) {
                viewModel.isCasaMainnet = true;
            } else {
                viewModel.isCasaMainnet = false;
            }
        } catch (Exception e) {
            viewModel.isCasaMainnet = false;
        }
    }

    protected void onSignSuccess() {
        WatchWallet wallet = WatchWallet.getWatchWallet(mActivity);
        if (multisig) {
            if (multiSigMode.equals(MultiSigMode.LEGACY)) {
                Bundle data = new Bundle();
                data.putString(KEY_TXID, viewModel.getTxId());
                data.putString(KEY_MULTISIG_MODE, multiSigMode.name());
                navigate(R.id.action_to_psbt_broadcast, data);
            } else {
                Bundle data = new Bundle();
                data.putString(KEY_TXID, viewModel.getCasaSignatureId());
                data.putString(KEY_MULTISIG_MODE, multiSigMode.name());
                navigate(R.id.action_to_psbt_broadcast, data);
            }
        } else if (wallet == WatchWallet.BTCPAY || wallet == WatchWallet.BLUE || wallet == WatchWallet.GENERIC || wallet == WatchWallet.SPARROW) {
            Bundle data = new Bundle();
            data.putString(KEY_TXID, viewModel.getTxId());
            navigate(R.id.action_to_psbt_broadcast, data);
        } else if (wallet == WatchWallet.ELECTRUM) {
            if (viewModel.getTxHex().length() <= 800) {
                String txId = viewModel.getTxId();
                Bundle data = new Bundle();
                data.putString(BroadcastTxFragment.KEY_TXID, txId);
                navigate(R.id.action_to_broadcastElectrumTxFragment, data);
            } else {
                showExportPsbtDialog(mActivity, viewModel.getSignedTxEntity(),
                        () -> popBackStack(R.id.assetFragment, false));
            }
        } else {
            showExportPsbtDialog(mActivity, viewModel.getSignedTxEntity(),
                    () -> popBackStack(R.id.assetFragment, false));
        }
        viewModel.getSignState().removeObservers(this);
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }
}



