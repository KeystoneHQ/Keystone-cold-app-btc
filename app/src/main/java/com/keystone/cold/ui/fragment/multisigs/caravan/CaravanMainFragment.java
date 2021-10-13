package com.keystone.cold.ui.fragment.multisigs.caravan;

import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS;
import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS_NAME;
import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS_PATH;
import static com.keystone.cold.ui.fragment.Constants.KEY_COIN_CODE;

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
import com.keystone.cold.databinding.CaravanBottomSheetBinding;
import com.keystone.cold.databinding.CaravanMultisgMainBinding;
import com.keystone.cold.databinding.MultisigAddressItemBinding;
import com.keystone.cold.db.entity.MultiSigAddressEntity;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.ui.fragment.main.NumberPickerCallback;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.fragment.main.scan.scanner.scanstate.LegacyScannerState;
import com.keystone.cold.ui.fragment.multisigs.common.MultiSigEntryBaseFragment;
import com.keystone.cold.ui.fragment.multisigs.legacy.AddressClickCallback;
import com.keystone.cold.ui.modal.ProgressModalDialog;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class CaravanMainFragment extends MultiSigEntryBaseFragment<CaravanMultisgMainBinding>
        implements NumberPickerCallback {
    private Menu mMenu;
    private boolean isEmpty;
    private int position;
    private String[] title;
    private RecyclerView receiveAddressRecycerView;
    private RecyclerView changeAddressRecycerView;
    private AddressAdapter receiveAdapter;
    private AddressAdapter changeAdapter;
    private ViewPagerAdapter viewPagerAdapter;
    protected CaravanMultiSigWalletEntity caravanWallet;

    @Override
    protected int setView() {
        return R.layout.caravan_multisg_main;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        Utilities.setMultiSigMode(mActivity, MultiSigMode.CARAVAN.getModeId());
        mActivity.setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setNavigationOnClickListener(((MainActivity) mActivity)::toggleDrawer);
        mBinding.toolbarModeSelection.setOnClickListener(l -> {
            showMultisigSelection();
        });
        if (getArguments() != null && getArguments().containsKey("walletFingerPrint")) {
            mBinding.hint.setVisibility(View.GONE);
        }
        mBinding.empty.exportXpub.setOnClickListener(v -> navigate(R.id.export_caravan_expub));
        mBinding.empty.importMultisigWallet.setOnClickListener(v -> navigate(R.id.import_multisig_file_list));
        mBinding.empty.createMultisig.setOnClickListener(v -> navigate(R.id.create_multisig_wallet));
        mBinding.walletInfo.fab.setOnClickListener(v -> addAddress());
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        super.initData(savedInstanceState);
        caravanMultiSigViewModel.getCurrentWallet().observe(this, walletEntity -> {
            if (walletEntity != null) {
                isEmpty = false;
                if (caravanWallet != null && !TextUtils.equals(caravanWallet.getWalletFingerPrint(), walletEntity.getWalletFingerPrint())) {
                    position = 0;
                }
                caravanWallet = walletEntity.toCaravanWallet();
            } else {
                isEmpty = true;
            }
            refreshUI();
        });
    }

    private void refreshUI() {
        if (isEmpty) {
            mBinding.hint.setVisibility(View.VISIBLE);
            mBinding.wallet.setVisibility(View.GONE);
            if (mMenu != null) {
                MenuItem sdcard = mMenu.findItem(R.id.action_sdcard);
                if (sdcard != null) sdcard.setVisible(false);
                MenuItem scan = mMenu.findItem(R.id.action_scan);
                if (scan != null) scan.setVisible(false);
                MenuItem more = mMenu.findItem(R.id.action_more);
                if (more != null) more.setVisible(false);
            }
            position = 0;
        } else {
            mBinding.hint.setVisibility(View.GONE);
            mBinding.wallet.setVisibility(View.VISIBLE);
            mBinding.walletInfo.walletLabel.setText(caravanWallet.getWalletName());
            mBinding.walletInfo.walletLabelContainer.setOnClickListener(v -> navigateToManageWallet());
            title = new String[]{getString(R.string.tab_my_address), getString(R.string.tab_my_change_address)};
            if (mMenu != null) {
                MenuItem sdcard = mMenu.findItem(R.id.action_sdcard);
                if (sdcard != null) sdcard.setVisible(true);
                MenuItem scan = mMenu.findItem(R.id.action_scan);
                if (scan != null) scan.setVisible(true);
                MenuItem more = mMenu.findItem(R.id.action_more);
                if (more != null) more.setVisible(true);
            }
            refreshViewPager();
        }
    }

    private void refreshViewPager() {
        legacyMultiSigViewModel.getMultiSigAddress(mActivity, caravanWallet.getWalletFingerPrint()).observe(mActivity, multiSigAddressEntities -> {
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
            mBinding.walletInfo.viewpager.setAdapter(viewPagerAdapter);
            mBinding.walletInfo.tab.setupWithViewPager(mBinding.walletInfo.viewpager);
            TabLayout.Tab tabAt = mBinding.walletInfo.tab.getTabAt(position);
            if (tabAt != null) {
                tabAt.select();
            }
        });
    }

    private void navigateToManageWallet() {
        Bundle data = new Bundle();
        data.putString("wallet_fingerprint", caravanWallet.getWalletFingerPrint());
        navigate(R.id.action_to_multisig_wallet, data);
    }

    private void addAddress() {
        position = mBinding.walletInfo.tab.getSelectedTabPosition();
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
            legacyMultiSigViewModel.addAddress(caravanWallet.getWalletFingerPrint(), value, position);
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
        menu.findItem(R.id.action_more).setVisible(false);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        mMenu = menu;
        inflater.inflate(R.menu.asset_hasmore, menu);
        if (isEmpty) {
            menu.findItem(R.id.action_sdcard).setVisible(false);
            menu.findItem(R.id.action_scan).setVisible(false);
            menu.findItem(R.id.action_more).setVisible(false);
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
                data.putString("multisig_mode", MultiSigMode.CARAVAN.name());
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
        CaravanBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.caravan_bottom_sheet, null, false);
        binding.switchWallet.setOnClickListener(v -> {
            dialog.dismiss();
            navigate(R.id.manage_multisig_wallet);
        });
        binding.addCaravan.setOnClickListener(v -> {
            navigate(R.id.action_to_add_multisig_wallet);
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
        data.putString(KEY_ADDRESS_PATH, addr.getPath());
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
