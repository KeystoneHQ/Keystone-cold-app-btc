package com.keystone.cold.ui.fragment.multisigs.casa;

import android.os.Bundle;
import android.view.View;

import com.keystone.cold.R;
import com.keystone.cold.databinding.MultiCasaGuideOneBinding;
import com.keystone.cold.ui.fragment.BaseFragment;

public class CasaGuidePageOneFragment extends BaseFragment<MultiCasaGuideOneBinding> {
    @Override
    protected int setView() {
        return R.layout.multi_casa_guide_one;
    }

    @Override
    protected void init(View view) {
        mBinding.toolbar.setNavigationOnClickListener(v -> {
            if (getArguments() != null && getArguments().getBoolean("isFromLegacy")) {
                navigate(R.id.action_to_legacyMultisigFragment);
            } else {
                navigateUp();
            }
        });
        mBinding.btContinue.setOnClickListener(v -> navigate(R.id.action_to_casaGuidePageTwoFragment));
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }
}
