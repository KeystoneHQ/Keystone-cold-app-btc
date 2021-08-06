package com.keystone.cold.ui.fragment.multisigs.casa;

import static com.keystone.cold.viewmodel.GlobalViewModel.showExportResult;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;
import static com.keystone.cold.viewmodel.GlobalViewModel.writeToSdcard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.databinding.DataBindingUtil;

import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.R;
import com.keystone.cold.databinding.ExportSdcardModalBinding;
import com.keystone.cold.databinding.MultisigCasaSignMessageResultBinding;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.Storage;
import com.sparrowwallet.hummingbird.UR;

import java.nio.charset.StandardCharsets;

public class CasaSignMessageResultFragment extends BaseFragment<MultisigCasaSignMessageResultBinding> {
    private String fileName;
    private String signResult;

    @Override
    protected int setView() {
        return R.layout.multisig_casa_sign_message_result;
    }

    @Override
    protected void init(View view) {
        mBinding.toolbar.setNavigationOnClickListener(v -> {
            navigateUp();
        });
        mBinding.complete.setOnClickListener(v -> {
            popBackStack(R.id.casaMultisigFragment, false);
        });
        try {
            Bundle data = requireArguments();
            signResult = data.getString("sign_result");
            fileName = data.getString("file_name").replace(".txt", "-signed.txt");
            byte[] bytes = signResult.getBytes(StandardCharsets.UTF_8);
            String ur = UR.fromBytes(bytes).toString();
            mBinding.qrcodeLayout.qrcode.setData(ur);
            mBinding.qrcodeLayout.hint.setVisibility(View.GONE);
            mBinding.exportToSdcard.setOnClickListener(v -> exportSignedMessage());
        } catch (Exception e) {
            e.printStackTrace();
            navigateUp();
        }
    }

    private void exportSignedMessage() {
        Storage storage = Storage.createByEnvironment();
        if (storage == null || storage.getExternalDir() == null) {
            showNoSdcardModal(mActivity);
        } else {
            ModalDialog modalDialog = ModalDialog.newInstance();
            ExportSdcardModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                    R.layout.export_sdcard_modal, null, false);
            binding.title.setText(R.string.export_xpub_text_file);
            binding.fileName.setText(fileName);
            binding.cancel.setOnClickListener(vv -> modalDialog.dismiss());
            binding.confirm.setOnClickListener(vv -> {
                modalDialog.dismiss();
                boolean result = writeToSdcard(storage, signResult, fileName);
                showExportResult(mActivity, null, result);
            });
            modalDialog.setBinding(binding);
            modalDialog.show(mActivity.getSupportFragmentManager(), "");
        }
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }
}
