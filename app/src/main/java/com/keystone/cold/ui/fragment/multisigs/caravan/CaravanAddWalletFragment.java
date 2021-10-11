package com.keystone.cold.ui.fragment.multisigs.caravan;

import android.view.View;

import com.keystone.cold.R;
import com.keystone.cold.databinding.CaravanAddWalletBinding;
import com.keystone.cold.ui.fragment.multisigs.legacy.MultiSigBaseFragment;

public class CaravanAddWalletFragment extends MultiSigBaseFragment<CaravanAddWalletBinding> {
    @Override
    protected int setView() {
        return R.layout.caravan_add_wallet;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mBinding.empty.exportXpub.setOnClickListener(v -> navigate(R.id.export_caravan_expub));
        mBinding.empty.importMultisigWallet.setOnClickListener(v -> navigate(R.id.import_multisig_file_list));
        mBinding.empty.createMultisig.setOnClickListener(v -> navigate(R.id.create_multisig_wallet));
    }
}
