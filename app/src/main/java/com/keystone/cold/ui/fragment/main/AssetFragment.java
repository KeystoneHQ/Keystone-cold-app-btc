/*
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
 */

package com.keystone.cold.ui.fragment.main;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProviders;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.keystone.coinlib.exception.CoinNotFindException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.utils.Base43;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.AddAddressBottomSheetBinding;
import com.keystone.cold.databinding.AssetFragmentBinding;
import com.keystone.cold.databinding.DialogBottomSheetBinding;
import com.keystone.cold.db.PresetData;
import com.keystone.cold.db.entity.CoinEntity;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.modal.ProgressModalDialog;
import com.keystone.cold.viewmodel.AddAddressViewModel;
import com.keystone.cold.viewmodel.KeystoneTxViewModel;
import com.keystone.cold.viewmodel.SetupVaultViewModel;
import com.keystone.cold.viewmodel.WatchWallet;
import com.keystone.cold.viewmodel.exceptions.UnknowQrCodeException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Permission;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.IntStream;

import static androidx.fragment.app.FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT;
import static com.keystone.cold.Utilities.IS_SETUP_VAULT;
import static com.keystone.cold.ui.fragment.Constants.KEY_COIN_ID;
import static com.keystone.cold.ui.fragment.setup.WebAuthResultFragment.WEB_AUTH_DATA;
import static com.keystone.cold.viewmodel.GlobalViewModel.getAddressType;
import static com.keystone.cold.viewmodel.WatchWallet.getWatchWallet;

public class AssetFragment extends BaseFragment<AssetFragmentBinding>
        implements NumberPickerCallback {

    public static final String TAG = "AssetFragment";
    private Fragment[] fragments;
    private String coinId;
    private String[] title;
    private WatchWallet watchWallet;

    @Override
    protected int setView() {
        return R.layout.asset_fragment;
    }

    @Override
    protected void init(View view) {
        coinId = Utilities.isMainNet(mActivity) ? Coins.BTC.coinId() : Coins.XTN.coinId();
        watchWallet = getWatchWallet(mActivity);
        mActivity.setSupportActionBar(mBinding.toolbar);
        mBinding.toolbar.setNavigationOnClickListener(((MainActivity) mActivity)::toggleDrawer);
        String walletName = watchWallet.getWalletName(mActivity);
        mBinding.toolbar.setTitle(walletName);
        mBinding.fab.setOnClickListener(v -> addAddress());
        title = new String[]{getString(R.string.tab_my_address), getString(R.string.tab_my_change_address)};
        initViewPager();
    }

    private void initScanResult() {
        ViewModelProviders.of(mActivity).get(ScannerViewModel.class)
                .setState(new ScannerState(Arrays.asList(ScanResultTypes.PLAIN_TEXT,
                        ScanResultTypes.UR_CRYPTO_PSBT, ScanResultTypes.UR_BYTES)) {
                    @Override
                    public void handleScanResult(ScanResult result) throws Exception {
                        if (result.getType().equals(ScanResultTypes.PLAIN_TEXT)) {
                            if (handleSignElectrumPSBT(result)) return;
                            throw new UnknowQrCodeException("not a electrum psbt transaction!");
                        } else if (result.getType().equals(ScanResultTypes.UR_BYTES)) {
                            if (handleWebAuth(result)) return;
                            if (handleKeystoneTx(result)) return;
                            if (handleSignUrBytesPSBT(result)) return;
                            throw new UnknowQrCodeException("unknown qrcode");
                        } else if (result.getType().equals(ScanResultTypes.UR_CRYPTO_PSBT)) {
                            if (handleSignCryptoPSBT(result)) return;
                            throw new UnknowQrCodeException("current watch wallet not support bc32 or psbt");
                        }
                    }

                    @Override
                    public boolean handleException(Exception e) {
                        e.printStackTrace();
                        if (e instanceof InvalidTransactionException) {
                            mFragment.alert(getString(R.string.incorrect_tx_data));
                            return true;
                        } else if (e instanceof CoinNotFindException) {
                            mFragment.alert(null, getString(R.string.only_support_btc), null);
                            return true;
                        } else if (e instanceof JSONException) {
                            mFragment.alert(getString(R.string.incorrect_qrcode));
                            return true;
                        } else if (e instanceof XfpNotMatchException) {
                            mFragment.alert(getString(R.string.uuid_not_match));
                            return true;
                        } else if (e instanceof UnknowQrCodeException) {
                            mFragment.alert(getString(R.string.error_hint),
                                    getString(R.string.unknown_qrcode,
                                            WatchWallet.getWatchWallet(mActivity).getWalletName(mActivity)));
                            return true;
                        } else if (e instanceof WatchWalletNotMatchException) {
                            mFragment.alert(getString(R.string.wallet_not_match_tips),
                                    getString(R.string.wallet_not_match));
                            return true;
                        }
                        return super.handleException(e);
                    }

                    private boolean handleWebAuth(ScanResult result) {
                        try {
                            JSONObject object = new JSONObject(new String((byte[]) result.resolve(), StandardCharsets.UTF_8));
                            JSONObject webAuth = object.optJSONObject("data");
                            if (webAuth != null && webAuth.optString("type").equals("webAuth")) {
                                String data = webAuth.getString("data");
                                Bundle bundle = new Bundle();
                                bundle.putString(WEB_AUTH_DATA, data);
                                bundle.putBoolean(IS_SETUP_VAULT, false);
                                mFragment.navigate(R.id.action_QRCodeScan_to_result, bundle);
                                return true;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return false;
                    }

                    private boolean handleSignElectrumPSBT(ScanResult result) {
                        byte[] data = Base43.decode(result.getData());
                        if (new String(data).startsWith("psbt")) {
                            String hex = result.getData();
                            navigatePsbt(Base43.decode(hex));
                            return true;
                        }
                        return false;
                    }

                    private boolean handleSignUrBytesPSBT(ScanResult result) {
                        byte[] bytes = (byte[]) result.resolve();
                        String hex = Hex.toHexString(bytes);
                        if (hex.startsWith(Hex.toHexString("psbt".getBytes()))) {
                            navigatePsbt(Hex.decode(hex));
                            return true;
                        }
                        return false;
                    }

                    private boolean handleKeystoneTx(ScanResult result) throws InvalidTransactionException,
                            XfpNotMatchException, JSONException, UnknowQrCodeException, CoinNotFindException,
                            WatchWalletNotMatchException {
                        KeystoneTxViewModel viewModel = ViewModelProviders.of(mActivity)
                                .get(KeystoneTxViewModel.class);
                        byte[] bytes = (byte[]) result.resolve();
                        String hex = Hex.toHexString(bytes);
                        JSONObject object = viewModel.decodeAsProtobuf(hex);
                        if (object != null) {
                            Log.i(TAG, "decodeAsProtobuf result: " + object);
                            Bundle bundle = viewModel.decodeAsBundle(object);
                            mFragment.navigate(R.id.action_to_txConfirmFragment, bundle);
                            return true;
                        }
                        return false;
                    }

                    private boolean handleSignCryptoPSBT(ScanResult result) throws WatchWalletNotMatchException {
                        WatchWallet watchWallet = WatchWallet.getWatchWallet(mActivity);
                        if (watchWallet.supportBc32QrCode() && watchWallet.supportPsbt()) {
                            CryptoPSBT cryptoPSBT = (CryptoPSBT) result.resolve();
                            byte[] bytes = cryptoPSBT.getPsbt();
                            navigatePsbt(bytes);
                            return true;
                        } else {
                            throw new WatchWalletNotMatchException("not support bc32 or psbt qrcode in current wallet mode");
                        }
                    }

                    private void navigatePsbt(byte[] decode) {
                        String base64 = Base64.toBase64String(decode);
                        Bundle bundle = new Bundle();
                        bundle.putString("psbt_base64", base64);
                        mFragment.navigate(R.id.action_to_psbtSigleTxConfirmFragment, bundle);
                    }
                });
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
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.asset_hasmore, menu);
        if (!watchWallet.supportSdcard()) {
            menu.findItem(R.id.action_sdcard).setVisible(false);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    private void initViewPager() {
        if (fragments == null) {
            fragments = new Fragment[title.length];
            fragments[0] = AddressFragment.newInstance(coinId, false);
            fragments[1] = AddressFragment.newInstance(coinId, true);
        }
        mBinding.viewpager.setAdapter(new FragmentStatePagerAdapter(getChildFragmentManager(),
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
        });
        mBinding.tab.setupWithViewPager(mBinding.viewpager);
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        checkAndAddNewCoins();
    }

    private void checkAndAddNewCoins() {
        SetupVaultViewModel viewModel = ViewModelProviders.of(mActivity)
                .get(SetupVaultViewModel.class);
        AppExecutors.getInstance().diskIO().execute(()
                -> viewModel.presetData(PresetData.generateCoins(mActivity), null)
        );

    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_more:
                showBottomSheetMenu();
                break;
            case R.id.action_scan:
                scanQrCode();
                break;
            case R.id.action_sdcard:
                showFileList();
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFileList() {
        switch (watchWallet) {
            case ELECTRUM:
            case WASABI:
            case BTCPAY:
            case GENERIC:
            case SPARROW:
                navigate(R.id.action_to_psbtListFragment);
                break;
        }
    }

    private void scanQrCode() {
        AndPermission.with(this)
                .permission(Permission.CAMERA, Permission.READ_EXTERNAL_STORAGE)
                .onGranted(permissions -> {
                    initScanResult();
                    navigate(R.id.action_to_scanner);
                })
                .onDenied(permissions -> {
                    Uri packageURI = Uri.parse("package:" + mActivity.getPackageName());
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Toast.makeText(mActivity, getString(R.string.scan_permission_denied), Toast.LENGTH_LONG).show();
                }).start();
    }

    private void showBottomSheetMenu() {
        BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
        DialogBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.dialog_bottom_sheet, null, false);
        binding.exportXpub.setOnClickListener(v -> {
            switch (watchWallet) {
                case ELECTRUM:
                    navigate(R.id.export_electrum_ypub);
                    break;
                case KEYSTONE:
                    navigate(R.id.export_xpub_keystone);
                    break;
                case WASABI:
                    navigate(R.id.action_to_export_xpub_guide);
                    break;
                case BTCPAY:
                case GENERIC:
                case SPARROW:
                    navigate(R.id.action_to_export_xpub_generic);
                    break;
                case BLUE:
                    navigate(R.id.action_to_export_xpub_blue);
                    break;
            }
            dialog.dismiss();

        });

        binding.signHistory.setOnClickListener(v -> {
            Bundle data = new Bundle();
            data.putString(KEY_COIN_ID, coinId);
            navigate(R.id.action_to_txList, data);
            dialog.dismiss();

        });

        binding.walletInfo.setOnClickListener(v -> {
            navigate(R.id.action_to_walletInfoFragment);
            dialog.dismiss();

        });

        dialog.setContentView(binding.getRoot());
        dialog.show();
    }

    @Override
    public void onValueSet(int value) {
        AddAddressViewModel viewModel = ViewModelProviders.of(this)
                .get(AddAddressViewModel.class);

        ProgressModalDialog dialog = ProgressModalDialog.newInstance();
        dialog.show(Objects.requireNonNull(mActivity.getSupportFragmentManager()), "");
        Handler handler = new Handler();
        AppExecutors.getInstance().diskIO().execute(() -> {
            CoinEntity coinEntity = viewModel.getCoin(coinId);
            if (coinEntity != null) {

                int tabPosition = mBinding.tab.getSelectedTabPosition();
                int changeIndex;
                if (tabPosition == 0) {
                    changeIndex = 0;
                } else {
                    changeIndex = 1;
                }

                viewModel.addAddress(value, getAddressType(mActivity), changeIndex);
                handler.post(() -> viewModel.getObservableAddState().observe(this, complete -> {
                    if (complete) {
                        handler.postDelayed(dialog::dismiss, 500);
                    }
                }));
            }
        });
    }
}
