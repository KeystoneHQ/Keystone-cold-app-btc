package com.keystone.cold.ui.fragment.multisigs.casa;

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
import com.keystone.cold.databinding.MultisigCasaListItemBinding;
import com.keystone.cold.databinding.MultisigCasaMainBinding;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.common.FilterableBaseBindingAdapter;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.spongycastle.util.encoders.Base64;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.keystone.cold.ui.fragment.multisigs.casa.CasaSignedPsbtFragment.KEY_ID;

public class CasaMainFragment extends MultiSigEntryBaseFragment<MultisigCasaMainBinding> {
    public static final String TAG = "MultisigEntry";
    private SignatureAdapter signatureAdapter;
    private CasaCallback casaCallback;
    private boolean isShowSignature;
    private int position;

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
        casaCallback = cx -> {
            Bundle bundle = new Bundle();
            bundle.putLong(KEY_ID, cx.getId());
            navigate(R.id.action_to_psbtSignedCasaFragment, bundle);
        };
        signatureAdapter = new SignatureAdapter(mActivity);
        signatureAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                if (isShowSignature) {
                    if (position == 0) {
                        mBinding.signaturesEmpty.setVisibility(View.VISIBLE);
                        mBinding.signaturesList.setVisibility(View.GONE);
                    } else {
                        mBinding.signaturesEmpty.setVisibility(View.GONE);
                        mBinding.signaturesList.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
        mBinding.tab.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                position = tab.getPosition();
                if (position == 0) {
                    isShowSignature = false;
                    mBinding.operations.setVisibility(View.VISIBLE);
                } else {
                    isShowSignature = true;
                    casaSignatureLiveData.observe(CasaMainFragment.this, casaSignatures -> {
                        casaSignatures = new ArrayList<>(casaSignatures);
                        signatureAdapter.setItems(casaSignatures);
                        if (casaSignatures.isEmpty()) {
                            mBinding.signaturesEmpty.setVisibility(View.VISIBLE);
                        } else {
                            mBinding.signaturesList.setAdapter(signatureAdapter);
                            mBinding.signaturesList.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    mBinding.operations.setVisibility(View.GONE);
                } else {
                    if (casaSignatureLiveData.getValue() == null || casaSignatureLiveData.getValue().isEmpty()) {
                        mBinding.signaturesEmpty.setVisibility(View.GONE);
                    } else {
                        mBinding.signaturesList.setVisibility(View.GONE);
                    }
                }
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
        Objects.requireNonNull(mBinding.tab.getTabAt(position)).select();
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
            Bundle data = new Bundle();
            ArrayList<String> desiredResults = new ArrayList<>();
            desiredResults.add(ScanResultTypes.UR_CRYPTO_PSBT.name());
            data.putStringArrayList("desired_results", desiredResults);
            ViewModelProviders.of(mActivity).get(ScannerViewModel.class).setState(new ScannerState(Collections.singletonList(ScanResultTypes.UR_CRYPTO_PSBT)) {
                @Override
                public void handleScanResult(ScanResult result) {
                    if (result.getType().equals(ScanResultTypes.UR_CRYPTO_PSBT)) {
                        CryptoPSBT cryptoPSBT = (CryptoPSBT) result.resolve();
                        byte[] bytes = cryptoPSBT.getPsbt();
                        String psbtB64 = Base64.toBase64String(bytes);
                        Bundle bundle = new Bundle();
                        bundle.putString("psbt_base64", psbtB64);
                        bundle.putBoolean("multisig", true);
                        bundle.putString("multisig_mode", MultiSigMode.CASA.name());
                        mFragment.navigate(R.id.action_to_psbtCasaTxConfirmFragment, bundle);
                    }
                }
            });
            navigate(R.id.action_to_scanner, data);
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
            binding.setCs(item);
            binding.setCasaCallback(casaCallback);
        }
    }
}
