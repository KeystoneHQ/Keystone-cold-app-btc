package com.keystone.cold.ui.fragment.multisigs.casa;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.google.android.material.tabs.TabLayout;
import com.keystone.cold.R;
import com.keystone.cold.databinding.MultisigCasaMainBinding;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.spongycastle.util.encoders.Base64;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CasaMainFragment extends MultiSigEntryBaseFragment<MultisigCasaMainBinding> {
    public static final String TAG = "MultisigEntry";

    private LiveData<List<CasaSignature>> casaSignatureLiveData;

    @Override
    protected int setView() {
        return R.layout.multisig_casa_main;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        casaSignatureLiveData = casaMultiSigViewModel.allCasaSignatures();
        mActivity.setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setNavigationOnClickListener(((MainActivity) mActivity)::toggleDrawer);
        mBinding.toolbarModeSelection.setOnClickListener(l -> {
            showMultisigSelection();
        });
        mBinding.export.setOnClickListener(v -> navigate(R.id.action_to_casaExportXPubFragment));
        mBinding.tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    mBinding.operations.setVisibility(View.VISIBLE);
                } else {
                    mBinding.signatures.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    mBinding.operations.setVisibility(View.GONE);
                } else {
                    mBinding.signatures.setVisibility(View.GONE);
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        mBinding.healthCheck.setOnClickListener(v -> {
            Bundle data = new Bundle();
            ArrayList<String> desiredResults = new ArrayList<>();
            desiredResults.add(ScanResultTypes.PLAIN_TEXT.name());
            data.putStringArrayList("desired_results", desiredResults);
            navigate(R.id.action_to_scanner, data);
            getNavigationResult("scan_result").observe(this, x -> {
                try (BufferedReader reader = new BufferedReader(new StringReader((String) x))) {
                    String message = reader.readLine();
                    String path = reader.readLine();
                    Bundle bundle = new Bundle();
                    bundle.putString("message", message);
                    bundle.putString("path", path);
                    navigate(R.id.action_to_psbtTxConfirmFragment, bundle);
                    getNavigationResult("scan_result").removeObservers(this);
                } catch (Exception e) {

                }
            });
        });
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
                byte[] bytes = cryptoPSBT.getPsbt();
                String psbtB64 = Base64.toBase64String(bytes);
                Bundle bundle = new Bundle();
                bundle.putString("psbt_base64", psbtB64);
                bundle.putBoolean("multisig", true);
                bundle.putString("multisig_mode", MultiSigMode.CASA.name());
                navigate(R.id.action_to_psbtTxConfirmFragment, bundle);
                getNavigationResult("scan_result").removeObservers(this);
            });
            return true;
        }
        return false;
    }
}
