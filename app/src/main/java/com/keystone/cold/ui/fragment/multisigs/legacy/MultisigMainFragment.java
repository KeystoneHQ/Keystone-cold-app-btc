/*
 *
 * Copyright (c) 2021 Keystone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * in the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.keystone.cold.ui.fragment.multisigs.legacy;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.AddAddressBottomSheetBinding;
import com.keystone.cold.databinding.MultisigBottomSheetBinding;
import com.keystone.cold.databinding.MultisigMainBinding;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.fragment.main.NumberPickerCallback;
import com.keystone.cold.ui.fragment.main.QrScanPurpose;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;
import com.keystone.cold.ui.modal.ProgressModalDialog;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

import static androidx.fragment.app.FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT;

public class MultisigMainFragment extends MultiSigEntryBaseFragment<MultisigMainBinding>
        implements NumberPickerCallback {
    public static final String TAG = "MultisigEntry";

    private MultiSigAddressFragment[] fragments;
    private String[] title;
    private String coinId;
    private MultiSigWalletEntity wallet;
    private boolean isEmpty;
    private Menu mMenu;
    private FragmentManager fm;

    @Override
    protected int setView() {
        return R.layout.multisig_main;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        coinId = Utilities.isMainNet(mActivity) ? Coins.BTC.coinId() : Coins.XTN.coinId();
        mActivity.setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setNavigationOnClickListener(((MainActivity) mActivity)::toggleDrawer);
        legacyMultiSigViewModel.getCurrentWallet().observe(this, w -> {
            if (w != null) {
                isEmpty = false;
                wallet = w;
            } else {
                isEmpty = true;
            }
            refreshUI();
        });
        mBinding.toolbarModeSelection.setOnClickListener(l -> {
            showMultisigSelection();
        });
    }

    private void refreshUI() {
        if (isEmpty) {
            mBinding.empty.setVisibility(View.VISIBLE);
            mBinding.fab.hide();
            mBinding.createMultisig.setOnClickListener(v -> navigate(R.id.create_multisig_wallet));
            mBinding.importMultisig.setOnClickListener(v -> navigate(R.id.import_multisig_file_list));
            if (mMenu != null) {
                MenuItem sdcard = mMenu.findItem(R.id.action_sdcard);
                if (sdcard != null) sdcard.setVisible(false);
                MenuItem scan = mMenu.findItem(R.id.action_scan);
                if (scan != null) scan.setVisible(false);
            }
            if (fm != null) {
                if (fragments != null) {
                    fm.beginTransaction().remove(fragments[0]).remove(fragments[1]).commit();
                }
            }
            mBinding.walletLabelContainer.setVisibility(View.GONE);
        } else {
            mBinding.empty.setVisibility(View.GONE);
            mBinding.fab.show();
            mBinding.fab.setOnClickListener(v -> addAddress());
            mBinding.walletLabelContainer.setVisibility(View.VISIBLE);
            mBinding.walletLabel.setText(wallet.getWalletName() + " ");
            mBinding.walletLabelContainer.setOnClickListener(v -> navigateToManageWallet());
            title = new String[]{getString(R.string.tab_my_address), getString(R.string.tab_my_change_address)};
            fm = getChildFragmentManager();
            initViewPager();
            if (mMenu != null) {
                MenuItem sdcard = mMenu.findItem(R.id.action_sdcard);
                if (sdcard != null) sdcard.setVisible(true);
                MenuItem scan = mMenu.findItem(R.id.action_scan);
                if (scan != null) scan.setVisible(true);
            }
        }
    }

    private void navigateToManageWallet() {
        Bundle data = new Bundle();
        data.putString("wallet_fingerprint", wallet.getWalletFingerPrint());
        data.putString("creator", wallet.getCreator());
        navigate(R.id.action_to_multisig_wallet, data);
    }

    private void addAddress() {
        BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
        AddAddressBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.add_address_bottom_sheet, null, false);
        String[] displayValue = IntStream.range(0, 9)
                .mapToObj(i -> String.valueOf(i + 1))
                .toArray(String[]::new);
        binding.setValue(1);
        binding.title.setText(getString(R.string.select_address_num, title[mBinding.tab.getSelectedTabPosition()]));
        binding.close.setOnClickListener(v -> dialog.dismiss());
        binding.picker.setValue(0);
        binding.picker.setDisplayedValues(displayValue);
        binding.picker.setMinValue(0);
        binding.picker.setMaxValue(8);
        binding.picker.setOnValueChangedListener((picker, oldVal, newVal) -> binding.setValue(newVal + 1));
        binding.confirm.setOnClickListener(v -> {
            onValueSet(binding.picker.getValue() + 1);
            dialog.dismiss();

        });
        dialog.setContentView(binding.getRoot());
        dialog.show();
    }

    @Override
    public void onValueSet(int value) {
        ProgressModalDialog dialog = ProgressModalDialog.newInstance();
        dialog.show(Objects.requireNonNull(mActivity.getSupportFragmentManager()), "");
        Handler handler = new Handler();
        AppExecutors.getInstance().diskIO().execute(() -> {
            int tabPosition = mBinding.tab.getSelectedTabPosition();
            legacyMultiSigViewModel.addAddress(wallet.getWalletFingerPrint(), value, tabPosition);
            handler.post(() -> legacyMultiSigViewModel.getObservableAddState().observe(this, complete -> {
                if (complete) {
                    handler.postDelayed(dialog::dismiss, 500);
                }
            }));

        });
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        mMenu = menu;
        inflater.inflate(R.menu.asset_hasmore, menu);
        if (isEmpty) {
            menu.findItem(R.id.action_sdcard).setVisible(false);
            menu.findItem(R.id.action_scan).setVisible(false);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_scan:
                scanQrCode();
                break;
            case R.id.action_sdcard:
                Bundle data = new Bundle();
                data.putBoolean("multisig", true);
                data.putString("multisig_mode", MultiSigMode.LEGACY.name());
                navigate(R.id.action_to_psbtListFragment, data);
                break;
            case R.id.action_more:
                showBottomSheetMenu();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void scanQrCode() {
        ViewModelProviders.of(mActivity).get(ScannerViewModel.class).setState(new ScannerState(Arrays.asList(ScanResultTypes.UR_BYTES, ScanResultTypes.UR_CRYPTO_PSBT)) {
            @Override
            public void handleScanResult(ScanResult result) throws Exception {
                String psbt = null;
                if (result.getType().equals(ScanResultTypes.UR_BYTES)) {
                    byte[] bytes = (byte[]) result.resolve();
                    String hex = Hex.toHexString(bytes);
                    if (hex.startsWith(Hex.toHexString("psbt".getBytes()))) {
                        psbt = hex;
                    }
                } else if (result.getType().equals(ScanResultTypes.UR_CRYPTO_PSBT)) {
                    CryptoPSBT cryptoPSBT = (CryptoPSBT) result.resolve();
                    psbt = Hex.toHexString(cryptoPSBT.getPsbt());
                }
                if (psbt == null) {
                    throw new Exception("no psbt data found");
                }
                Bundle bundle = new Bundle();
                bundle.putString("psbt_base64", Base64.toBase64String(Hex.decode(psbt)));
                bundle.putBoolean("multisig", true);
                bundle.putString("multisig_mode", MultiSigMode.LEGACY.name());
                mFragment.navigate(R.id.action_scanner_to_psbtTxConfirmFragment, bundle);
            }
        });
        navigate(R.id.action_to_scanner);
    }


    private void initViewPager() {
        fragments = new MultiSigAddressFragment[title.length];
        fragments[0] = MultiSigAddressFragment.newInstance(coinId, false, wallet.getWalletFingerPrint());
        fragments[1] = MultiSigAddressFragment.newInstance(coinId, true, wallet.getWalletFingerPrint());

        FragmentStatePagerAdapter pagerAdapter = new FragmentStatePagerAdapter(fm,
                BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
            @NonNull
            @Override
            public Fragment getItem(int position) {
                return fragments[position];
            }

            @Override
            public int getCount() {
                return title.length;
            }

            @Override
            public CharSequence getPageTitle(int position) {
                return title[position];
            }

            @Override
            public int getItemPosition(@NonNull Object object) {
                return POSITION_NONE;
            }


        };
        mBinding.viewpager.setAdapter(pagerAdapter);
        mBinding.tab.setupWithViewPager(mBinding.viewpager);
    }

    private void showBottomSheetMenu() {
        BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
        MultisigBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.multisig_bottom_sheet, null, false);
        binding.exportXpub.setOnClickListener(v -> {
            dialog.dismiss();
            navigate(R.id.export_export_multisig_expub);
        });
        binding.createMultisig.setOnClickListener(v -> {
            navigate(R.id.create_multisig_wallet);
            dialog.dismiss();
        });

        binding.importMultisig.setOnClickListener(v -> {
            navigate(R.id.import_multisig_file_list);
            dialog.dismiss();
        });

        binding.manageMultisig.setOnClickListener(v -> {
            navigate(R.id.manage_multisig_wallet);
            dialog.dismiss();
        });

        dialog.setContentView(binding.getRoot());
        dialog.show();
    }
}
