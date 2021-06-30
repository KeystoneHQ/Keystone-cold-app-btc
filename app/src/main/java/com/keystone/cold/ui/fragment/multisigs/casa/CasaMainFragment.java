package com.keystone.cold.ui.fragment.multisigs.casa;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;

import com.keystone.cold.R;
import com.keystone.cold.databinding.MultisigCasaMainBinding;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;
import com.keystone.cold.viewmodel.SharedDataViewModel;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import java.util.ArrayList;
import java.util.Arrays;

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
        mBinding.export.setOnClickListener(v -> navigate(R.id.action_to_casaExportXPubFragment));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_scan) {
            Bundle data = new Bundle();
            ArrayList<String> desiredResults = new ArrayList<>();
            desiredResults.add(ScanResultTypes.CRYPTO_PSBT.name());
            data.putStringArrayList("desired_results", desiredResults);
            navigate(R.id.action_to_scanner, data);
            getNavigationResult("scan_result").observe(this, v -> {
                CryptoPSBT cryptoPSBT = (CryptoPSBT) ScanResultTypes.CRYPTO_PSBT.resolveURHex((String) v);
            });
            return true;
        }
        return false;
    }
}
