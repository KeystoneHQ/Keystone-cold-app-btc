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

import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS;
import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS_NAME;
import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS_PATH;
import static com.keystone.cold.ui.fragment.Constants.KEY_COIN_CODE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.AddAddressBottomSheetBinding;
import com.keystone.cold.databinding.MultisigAddressItemBinding;
import com.keystone.cold.databinding.MultisigBottomSheetBinding;
import com.keystone.cold.databinding.MultisigMainBinding;
import com.keystone.cold.db.entity.MultiSigAddressEntity;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.ui.fragment.main.NumberPickerCallback;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.fragment.main.scan.scanner.scanstate.LegacyScannerState;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;
import com.keystone.cold.ui.modal.ProgressModalDialog;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class MultisigMainFragment extends MultiSigEntryBaseFragment<MultisigMainBinding>
        implements NumberPickerCallback {
    public static final String TAG = "MultisigEntry";
    protected MultiSigWalletEntity wallet;
    private String[] title;
    private boolean isEmpty;
    private Menu mMenu;
    private RecyclerView receiveAddressRecycerView;
    private RecyclerView changeAddressRecycerView;
    private AddressAdapter receiveAdapter;
    private AddressAdapter changeAdapter;
    private ViewPagerAdapter viewPagerAdapter;
    private int position = 0;

    @Override
    protected int setView() {
        return R.layout.multisig_main;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        Utilities.setMultiSigMode(mActivity, MultiSigMode.LEGACY.getModeId());
        mActivity.setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setNavigationOnClickListener(((MainActivity) mActivity)::toggleDrawer);
        mBinding.toolbarModeSelection.setOnClickListener(l -> {
            showMultisigSelection();
        });
        if (getArguments() != null && getArguments().containsKey("walletFingerPrint")) {
            mBinding.empty.setVisibility(View.GONE);
            mBinding.viewpager.setVisibility(View.VISIBLE);
            mBinding.fab.show();
            mBinding.walletLabelContainer.setVisibility(View.VISIBLE);
        }
        legacyMultiSigViewModel.getCurrentWallet().observe(this, w -> {
            if (w != null) {
                isEmpty = false;
                if (wallet != null && !TextUtils.equals(wallet.getWalletFingerPrint(), w.getWalletFingerPrint())) {
                    position = 0;
                }
                wallet = w;
            } else {
                isEmpty = true;
            }
            refreshUI();
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        position = mBinding.tab.getSelectedTabPosition() == -1 ? 0 : mBinding.tab.getSelectedTabPosition();
    }

    private void refreshUI() {
        if (isEmpty) {
            mBinding.empty.setVisibility(View.VISIBLE);
            mBinding.viewpager.setVisibility(View.GONE);
            mBinding.fab.hide();
            mBinding.createMultisig.setOnClickListener(v -> navigate(R.id.create_multisig_wallet));
            mBinding.importMultisig.setOnClickListener(v -> navigate(R.id.import_multisig_file_list));
            if (mMenu != null) {
                MenuItem sdcard = mMenu.findItem(R.id.action_sdcard);
                if (sdcard != null) sdcard.setVisible(false);
                MenuItem scan = mMenu.findItem(R.id.action_scan);
                if (scan != null) scan.setVisible(false);
            }
            mBinding.walletLabelContainer.setVisibility(View.GONE);
            position = 0;
        } else {
            mBinding.empty.setVisibility(View.GONE);
            mBinding.viewpager.setVisibility(View.VISIBLE);
            mBinding.fab.show();
            mBinding.fab.setOnClickListener(v -> addAddress());
            mBinding.walletLabelContainer.setVisibility(View.VISIBLE);
            mBinding.walletLabel.setText(wallet.getWalletName());
            mBinding.walletLabelContainer.setOnClickListener(v -> navigateToManageWallet());
            title = new String[]{getString(R.string.tab_my_address), getString(R.string.tab_my_change_address)};
            if (mMenu != null) {
                MenuItem sdcard = mMenu.findItem(R.id.action_sdcard);
                if (sdcard != null) sdcard.setVisible(true);
                MenuItem scan = mMenu.findItem(R.id.action_scan);
                if (scan != null) scan.setVisible(true);
            }
            refreshViewPager();
        }
    }

    @SuppressLint("CutPasteId")
    private void refreshViewPager() {
        legacyMultiSigViewModel.getMultiSigAddress(wallet.getWalletFingerPrint()).observe(mActivity, multiSigAddressEntities -> {
            if (receiveAdapter == null) {
                receiveAddressRecycerView = new RecyclerView(mActivity);
                changeAddressRecycerView = new RecyclerView(mActivity);
                receiveAddressRecycerView.setLayoutManager(new LinearLayoutManager(mActivity, RecyclerView.VERTICAL, false));
                changeAddressRecycerView.setLayoutManager(new LinearLayoutManager(mActivity, RecyclerView.VERTICAL, false));
                receiveAdapter = new AddressAdapter(mActivity);
                changeAdapter = new AddressAdapter(mActivity);
                viewPagerAdapter = new ViewPagerAdapter();
                receiveAddressRecycerView.setAdapter(receiveAdapter);
                changeAddressRecycerView.setAdapter(changeAdapter);
            }
            receiveAdapter.setItems(legacyMultiSigViewModel.filterReceiveAddress(multiSigAddressEntities));
            changeAdapter.setItems(legacyMultiSigViewModel.filterChangeAddress(multiSigAddressEntities));
            viewPagerAdapter.setItems(Arrays.asList(receiveAddressRecycerView, changeAddressRecycerView));
            mBinding.viewpager.setAdapter(viewPagerAdapter);
            mBinding.tab.setupWithViewPager(mBinding.viewpager);
            TabLayout.Tab tabAt = mBinding.tab.getTabAt(position);
            if (tabAt != null) {
                tabAt.select();
            }
        });
    }

    private void navigateToManageWallet() {
        Bundle data = new Bundle();
        data.putString("wallet_fingerprint", wallet.getWalletFingerPrint());
        data.putString("creator", wallet.getCreator());
        navigate(R.id.action_to_multisig_wallet, data);
    }

    private void addAddress() {
        position = mBinding.tab.getSelectedTabPosition();
        BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
        AddAddressBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.add_address_bottom_sheet, null, false);
        String[] displayValue = IntStream.range(0, 9)
                .mapToObj(i -> String.valueOf(i + 1))
                .toArray(String[]::new);
        binding.setValue(1);
        binding.title.setText(getString(R.string.select_address_num, title[position]));
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
            legacyMultiSigViewModel.addAddress(wallet.getWalletFingerPrint(), value, position);
            handler.post(() -> legacyMultiSigViewModel.getObservableAddState().observe(this, complete -> {
                if (complete) {
                    handler.postDelayed(dialog::dismiss, 500);
                }
            }));
        });
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.action_sdcard).setVisible(false);
        menu.findItem(R.id.action_scan).setVisible(false);
        super.onPrepareOptionsMenu(menu);
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
        ViewModelProviders.of(mActivity).get(ScannerViewModel.class).setState(
                new LegacyScannerState(Arrays.asList(ScanResultTypes.UR_BYTES, ScanResultTypes.UR_CRYPTO_PSBT)));
        navigate(R.id.action_to_scanner);
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

    class ViewPagerAdapter extends PagerAdapter {
        private List<View> list = new ArrayList<>();

        public void setItems(List<View> list) {
            this.list.clear();
            this.list.addAll(list);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return title.length;
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object o) {
            return view == o;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return title[position];
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(list.get(position));
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ViewGroup parent = (ViewGroup) list.get(position).getParent();
            if (parent != null) {
                parent.removeView(list.get(position));
            }
            container.addView(list.get(position));
            return list.get(position);
        }
    }

    private final AddressClickCallback mAddrCallback = addr -> {
        Bundle data = new Bundle();
        data.putString(KEY_COIN_CODE, Utilities.isMainNet(mActivity) ? Coins.BTC.coinId() : Coins.XTN.coinId());
        data.putString(KEY_ADDRESS, addr.getAddress());
        data.putString(KEY_ADDRESS_NAME, addr.getName());
        if (isNeedReplace(wallet)) {
            data.putString(KEY_ADDRESS_PATH, addr.getPath().replace(wallet.getExPubPath(), "*"));
        } else {
            data.putString(KEY_ADDRESS_PATH, addr.getPath());
        }
        navigate(R.id.action_to_receiveCoinFragment, data);
    };

    class AddressAdapter extends BaseBindingAdapter<MultiSigAddressEntity, MultisigAddressItemBinding> {

        AddressAdapter(Context context) {
            super(context);
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.multisig_address_item;
        }

        @Override
        protected void onBindItem(MultisigAddressItemBinding binding, MultiSigAddressEntity item) {
            binding.setAddress(item);
            binding.setCallback(mAddrCallback);
        }
    }
}
