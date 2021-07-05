package com.keystone.cold.ui.fragment.multisigs.casa;

import android.os.Bundle;
import android.view.View;

import com.keystone.coinlib.accounts.ExtendedPublicKey;
import com.keystone.coinlib.utils.B58;
import com.keystone.cold.R;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.databinding.MultisigCasaSignMessageBinding;
import com.keystone.cold.ui.fragment.BaseFragment;

public class CasaSignMessageFragment extends BaseFragment<MultisigCasaSignMessageBinding> {
    @Override
    protected int setView() {
        return R.layout.multisig_casa_sign_message;
    }

    @Override
    protected void init(View view) {
        Bundle data = requireArguments();
        String message = data.getString("message");
        String path = data.getString("path");
        String xPub = new GetExtendedPublicKeyCallable(path).call();
        ExtendedPublicKey key = new ExtendedPublicKey(xPub);
        String address = new B58().encodeToStringChecked(key.getKey(), 0);
        mBinding.message.setText(message);
        mBinding.path.setText(path);
        mBinding.address.setText(address);

    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }
}
