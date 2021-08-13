package com.keystone.cold.ui.fragment.multisigs.casa;

import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.databinding.DataBindingUtil;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.R;
import com.keystone.cold.databinding.CommonModalBinding;
import com.keystone.cold.databinding.ExportSdcardModalBinding;
import com.keystone.cold.databinding.MultisigCasaExportXpubBinding;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.Storage;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;

public class CasaExportXPubFragment extends CasaBaseFragment<MultisigCasaExportXpubBinding> {
    @Override
    protected int setView() {
        return R.layout.multisig_casa_export_xpub;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        CryptoHDKey cryptoHDKey = casaMultiSigViewModel.exportCryptoHDKey();
        UR ur = cryptoHDKey.toUR();
        mBinding.qrcodeLayout.hint.setVisibility(View.GONE);
        mBinding.qrcodeLayout.qrcode.setData(ur.toString());
        mBinding.qrcodeLayout.frame.setLayoutParams(new LinearLayout.LayoutParams(320, 320));
        mBinding.done.setOnClickListener(v -> navigateUp());
        mBinding.info.setOnClickListener(v -> showExportGuide());
        mBinding.path.setText("Path: " + MultiSig.CASA.getPath());
        mBinding.xpub.setText(casaMultiSigViewModel.getXPub(MultiSig.CASA));
        mBinding.exportToSdcard.setOnClickListener(v -> exportXpub());
    }

    private void exportXpub() {
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
                boolean result = writeToSdcard(storage, getKeystoneXPubFileContent(casaMultiSigViewModel.getXPub(MultiSig.CASA), casaMultiSigViewModel.getXfp()), getFileName());
                showExportResult(mActivity, null, result);
            });
            modalDialog.setBinding(binding);
            modalDialog.show(mActivity.getSupportFragmentManager(), "");
        }
    }

    private String getFileName() {
        return "keystone.txt";
    }

    private String getKeystoneXPubFileContent(String xpub, String mfp) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("# Keystone Extended Public Key File\n");
        stringBuffer.append("## For wallet with master key fingerprint: ").append(mfp).append("\n");
        stringBuffer.append("## ## IMPORTANT WARNING\n\n");
        stringBuffer.append("Do **not** deposit to any address in this file unless you have a working\n");
        stringBuffer.append("wallet system that is ready to handle the funds at that address!\n");
        stringBuffer.append("## Top-level, 'master' extended public key ('m/'):\n").append(xpub);
        return stringBuffer.toString();
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
    }

    private void showExportGuide() {
        ModalDialog modalDialog = ModalDialog.newInstance();
        CommonModalBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(mActivity), R.layout.common_modal,
                null, false);
        binding.title.setText(R.string.scan_qrcode_with_casa_hint_title);
        binding.subTitle.setText(R.string.scan_qrcode_with_casa_hint);
        binding.subTitle.setGravity(Gravity.START);
        binding.close.setVisibility(View.GONE);
        binding.confirm.setText(R.string.know);
        binding.confirm.setOnClickListener(vv -> modalDialog.dismiss());
        modalDialog.setBinding(binding);
        modalDialog.show(mActivity.getSupportFragmentManager(), "");
    }
}
