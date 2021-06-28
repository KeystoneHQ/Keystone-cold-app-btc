package com.keystone.cold.ui.fragment.multisigs.casa;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;

import com.keystone.cold.R;
import com.keystone.cold.databinding.MultisigCasaMainBinding;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;

public class CasaMainFragment extends MultiSigEntryBaseFragment<MultisigCasaMainBinding> implements Toolbar.OnMenuItemClickListener {
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
        mBinding.export.setOnClickListener(v -> navigate(R.id.action_to_casaExportXPubFragment));
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_scan) {
            Bundle data = new Bundle();
            data.putString("purpose", "importMultiSigWallet");
            navigate(R.id.action_to_scan, data);
        }
        return true;
    }
}
