package com.keystone.cold.ui.fragment.multisigs.casa;

import android.os.Bundle;
import android.view.View;

import com.keystone.cold.R;
import com.keystone.cold.databinding.MultisigCasaSignMessageResultBinding;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.sparrowwallet.hummingbird.UR;

import java.nio.charset.StandardCharsets;

public class CasaSignMessageResultFragment extends BaseFragment<MultisigCasaSignMessageResultBinding> {
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
            String signResult = data.getString("sign_result");
            byte[] bytes = signResult.getBytes(StandardCharsets.UTF_8);
            String ur = UR.fromBytes(bytes).toString();
            mBinding.qrcodeLayout.qrcode.setData(ur);
        }catch (Exception e){
            e.printStackTrace();
            navigateUp();
        }
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }
}
