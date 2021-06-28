package com.keystone.cold.ui.fragment.multisigs.casa;

import android.view.View;

import com.keystone.cold.R;
import com.keystone.cold.databinding.MultisigCasaMainBinding;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;

public class CasaMainFragment extends MultiSigEntryBaseFragment<MultisigCasaMainBinding> {
    public static final String TAG = "MultisigEntry";
    @Override
    protected int setView() {
        return R.layout.multisig_casa_main;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        mActivity.setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setNavigationOnClickListener(((MainActivity) mActivity)::toggleDrawer);
        mBinding.toolbarModeSelection.setOnClickListener(l -> {
            showMultisigSelection();
        });
    }
}
