package com.keystone.cold.ui.fragment.multisigs.casa;

import static com.keystone.cold.ui.fragment.multisigs.casa.CasaSignedPsbtFragment.KEY_ID;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.MultisigCasaListItemBinding;
import com.keystone.cold.databinding.MultisigCasaMainBinding;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.common.FilterableBaseBindingAdapter;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.fragment.main.scan.scanner.scanstate.CasaScannerState;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CasaMainFragment extends MultiSigEntryBaseFragment<MultisigCasaMainBinding> {
    public static final String TAG = "MultisigEntry";
    private SignatureAdapter signatureAdapter;
    private CasaCallback casaCallback;
    private int position;

    private LiveData<List<CasaSignature>> casaSignatureLiveData;

    @Override
    protected int setView() {
        return R.layout.multisig_casa_main;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        Utilities.setMultiSigMode(mActivity, MultiSigMode.CASA.getModeId());
        casaSignatureLiveData = casaMultiSigViewModel.loadCasaTxs(Utilities.getCurrentBelongTo(mActivity));
        mActivity.setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setNavigationOnClickListener(((MainActivity) mActivity)::toggleDrawer);
        mBinding.toolbarModeSelection.setOnClickListener(l -> {
            showMultisigSelection();
        });
        mBinding.export.setOnClickListener(v -> navigate(R.id.action_to_casaExportXPubFragment));
        casaCallback = cx -> {
            Bundle bundle = new Bundle();
            bundle.putLong(KEY_ID, cx.getId());
            navigate(R.id.action_to_psbtSignedCasaFragment, bundle);
        };
        signatureAdapter = new SignatureAdapter(mActivity);
        mBinding.tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                position = tab.getPosition();
                initView();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
        mBinding.healthCheck.setOnClickListener(v -> navigate(R.id.action_to_casaPerformHealthCheckFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        restoreView();
    }

    private void restoreView(){
        Objects.requireNonNull(mBinding.tab.getTabAt(position)).select();
        initView();
    }

    private void initView() {
        if (position == 0) {
            mBinding.signaturesList.setVisibility(View.GONE);
            mBinding.signaturesEmpty.setVisibility(View.GONE);
            mBinding.operations.setVisibility(View.VISIBLE);
        } else if (position == 1) {
            mBinding.operations.setVisibility(View.GONE);
            casaSignatureLiveData.observe(CasaMainFragment.this, casaSignatures -> {
                Collections.reverse(casaSignatures);
                if (casaSignatures.isEmpty()) {
                    mBinding.operations.setVisibility(View.GONE);
                    mBinding.signaturesList.setVisibility(View.GONE);
                    mBinding.signaturesEmpty.setVisibility(View.VISIBLE);
                } else {
                    signatureAdapter.setItems(casaSignatures);
                    mBinding.signaturesList.setAdapter(signatureAdapter);
                    mBinding.operations.setVisibility(View.GONE);
                    mBinding.signaturesEmpty.setVisibility(View.GONE);
                    mBinding.signaturesList.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.casa, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_scan) {
            ViewModelProviders.of(mActivity).get(ScannerViewModel.class)
                    .setState(new CasaScannerState(Collections.singletonList(ScanResultTypes.UR_CRYPTO_PSBT)));
            navigate(R.id.action_to_scanner);
            return true;
        } else if (id == R.id.action_sdcard) {
            Bundle data = new Bundle();
            data.putBoolean("multisig", true);
            data.putString("multisig_mode", MultiSigMode.CASA.name());
            navigate(R.id.action_to_psbtListFragment, data);
            return true;
        }
        return false;
    }

    class SignatureAdapter extends FilterableBaseBindingAdapter<CasaSignature, MultisigCasaListItemBinding> {

        SignatureAdapter(Context context) {
            super(context);
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.multisig_casa_list_item;
        }

        @Override
        protected void onBindItem(MultisigCasaListItemBinding binding, CasaSignature item) {
            String txid = item.getTxId();
            binding.setCs(item);
            binding.txid.setText(txid);
            binding.setCasaCallback(casaCallback);
        }
    }
}
