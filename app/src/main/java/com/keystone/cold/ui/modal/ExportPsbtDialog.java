package com.keystone.cold.ui.modal;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;

import com.keystone.cold.R;
import com.keystone.cold.databinding.ExportSdcardModalBinding;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.update.utils.Storage;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;

import org.spongycastle.util.encoders.Base64;

import static com.keystone.cold.viewmodel.GlobalViewModel.hasSdcard;
import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;

public class ExportPsbtDialog {
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
}
