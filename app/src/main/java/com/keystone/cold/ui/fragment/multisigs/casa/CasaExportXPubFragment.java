package com.keystone.cold.ui.fragment.multisigs.casa;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.databinding.DataBindingUtil;

import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.R;
import com.keystone.cold.databinding.CommonModalBinding;
import com.keystone.cold.databinding.MultisigCasaExportXpubBinding;
import com.keystone.cold.ui.modal.ModalDialog;
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
        mBinding.scanHint.setOnClickListener(v -> showExportGuide());
        mBinding.path.setText("Path: " + MultiSig.CASA.getPath());
        mBinding.xpub.setText(casaMultiSigViewModel.getXPub(MultiSig.CASA));
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
