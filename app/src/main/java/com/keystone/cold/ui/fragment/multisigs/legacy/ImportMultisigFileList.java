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

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.FileListBinding;
import com.keystone.cold.databinding.FileListItemBinding;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.ui.fragment.main.electrum.Callback;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerFragment;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.viewmodel.exceptions.InvalidMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.keystone.coinlib.Util.getExpubFingerprint;
import static com.keystone.cold.viewmodel.GlobalViewModel.hasSdcard;
import static com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel.decodeCaravanWalletFile;
import static com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel.decodeColdCardWalletFile;


public class ImportMultisigFileList extends MultiSigBaseFragment<FileListBinding>
        implements Callback, Toolbar.OnMenuItemClickListener {
    private Adapter adapter;
    private AtomicBoolean showEmpty;

    @Override
    protected int setView() {
        return R.layout.file_list;
    }

    private Map<String, JSONObject> walletFiles = new HashMap<>();

    @Override
    protected void init(View view) {
        super.init(view);
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mBinding.toolbar.inflateMenu(R.menu.main);
        mBinding.toolbar.setOnMenuItemClickListener(this);
        mBinding.toolbarTitle.setText(R.string.import_multisig_wallet);
        adapter = new Adapter(mActivity, this);
        initViews();
    }

    private void initViews() {
        showEmpty = new AtomicBoolean(false);
        if (!hasSdcard()) {
            showEmpty.set(true);
            mBinding.emptyTitle.setText(R.string.no_sdcard);
            mBinding.emptyMessage.setText(R.string.no_sdcard_hint);
        } else {
            mBinding.list.setAdapter(adapter);
            legacyMultiSigViewModel.loadWalletFile().observe(this, files -> {
                walletFiles = files;
                if (files.size() > 0) {
                    List<String> fileNames = new ArrayList<>(files.keySet());
                    fileNames.sort(String::compareTo);
                    adapter.setItems(fileNames);
                } else {
                    showEmpty.set(true);
                    mBinding.emptyTitle.setText(R.string.no_multisig_wallet_file);
                    mBinding.emptyMessage.setText(R.string.no_multisig_wallet_file_hint);
                }
                updateUi();
            });
        }
        updateUi();
    }

    private void updateUi() {
        if (showEmpty.get()) {
            mBinding.emptyView.setVisibility(View.VISIBLE);
            mBinding.list.setVisibility(View.GONE);
        } else {
            mBinding.emptyView.setVisibility(View.GONE);
            mBinding.list.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(String file) {
        JSONObject walletFile = walletFiles.get(file);
        boolean isWalletFileTest = walletFile.optBoolean("isTest", false);
        boolean isTestnet = !Utilities.isMainNet(mActivity);
        if (isWalletFileTest != isTestnet) {
            String currentNet = isTestnet ? getString(R.string.testnet) : getString(R.string.mainnet);
            String walletFileNet = isWalletFileTest ? getString(R.string.testnet) : getString(R.string.mainnet);
            ModalDialog.showCommonModal(mActivity, getString(R.string.import_failed),
                    getString(R.string.import_failed_network_not_match, currentNet, walletFileNet, walletFileNet),
                    getString(R.string.know), null);
            return;
        }

        Bundle data = new Bundle();
        data.putString("wallet_info", walletFile.toString());
        navigate(R.id.import_multisig_wallet, data);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_scan) {
            Bundle data = new Bundle();
            data.putString("purpose", "importMultiSigWallet");
            ViewModelProviders.of(mActivity).get(ScannerViewModel.class)
                    .setState(new ScannerState(Collections.singletonList(ScanResultTypes.UR_BYTES)) {
                        @Override
                        public void handleScanResult(ScanResult result) throws Exception {
                            if (result.getType().equals(ScanResultTypes.UR_BYTES)) {
                                byte[] bytes = (byte[]) result.resolve();
                                String hex = Hex.toHexString(bytes);
                                JSONObject object = LegacyMultiSigViewModel.decodeColdCardWalletFile(new String(Hex.decode(hex), StandardCharsets.UTF_8));
                                if (object == null) {
                                    object = LegacyMultiSigViewModel.decodeCaravanWalletFile(new String(Hex.decode(hex), StandardCharsets.UTF_8));
                                }
                                if (object != null) {
                                    handleImportMultisigWallet(hex, mFragment);
                                } else {
                                    throw new InvalidMultisigWalletException("not multisig wallet");
                                }
                            }
                        }

                        @Override
                        public boolean handleException(Exception e) {
                            e.printStackTrace();
                            if (e instanceof InvalidMultisigWalletException) {
                                mFragment.alert(getString(R.string.invalid_multisig_wallet), getString(R.string.invalid_multisig_wallet_hint));
                                return true;
                            }
                            return super.handleException(e);
                        }
                    });
            navigate(R.id.action_to_scanner);
        }
        return true;
    }

    public void handleImportMultisigWallet(String hex, ScannerFragment scannerFragment) {
        try {
            LegacyMultiSigViewModel viewModel = ViewModelProviders.of(mActivity).get(LegacyMultiSigViewModel.class);
            String xfp = viewModel.getXfp();
            JSONObject obj;
            //try decode cc format
            obj = decodeColdCardWalletFile(new String(Hex.decode(hex), StandardCharsets.UTF_8));
            //try decode caravan format
            if (obj == null) {
                obj = decodeCaravanWalletFile(new String(Hex.decode(hex), StandardCharsets.UTF_8));
            }
            if (obj == null) {
                scannerFragment.alert(getString(R.string.invalid_multisig_wallet), getString(R.string.invalid_multisig_wallet_hint));
                return;
            }

            boolean isWalletFileTest = obj.optBoolean("isTest", false);
            Account account = MultiSig.ofPath(obj.getString("Derivation"), !isWalletFileTest).get(0);
            boolean isTestnet = !Utilities.isMainNet(mActivity);
            if (isWalletFileTest != isTestnet) {
                String currentNet = isTestnet ? getString(R.string.testnet) : getString(R.string.mainnet);
                String walletFileNet = isWalletFileTest ? getString(R.string.testnet) : getString(R.string.mainnet);
                scannerFragment.alert(getString(R.string.import_failed),
                        getString(R.string.import_failed_network_not_match, currentNet, walletFileNet, walletFileNet));
                return;
            }

            Bundle bundle = new Bundle();
            bundle.putString("wallet_info", obj.toString());
            JSONArray array = obj.getJSONArray("Xpubs");
            boolean matchXfp = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject xpubInfo = array.getJSONObject(i);
                String thisXfp = xpubInfo.getString("xfp");
                if (thisXfp.equalsIgnoreCase(xfp)
                        || thisXfp.equalsIgnoreCase(getExpubFingerprint(viewModel.getXPub(account)))) {
                    matchXfp = true;
                    break;
                }
            }
            if (!matchXfp) {
                throw new XfpNotMatchException("xfp not match");
            } else {
                scannerFragment.navigate(R.id.import_multisig_wallet, bundle);
            }
        } catch (XfpNotMatchException e) {
            e.printStackTrace();
            scannerFragment.alert(getString(R.string.import_multisig_wallet_fail), getString(R.string.import_multisig_wallet_fail_hint));
        } catch (JSONException e) {
            e.printStackTrace();
            scannerFragment.alert(getString(R.string.incorrect_qrcode));
        }

    }

    static class Adapter extends BaseBindingAdapter<String, FileListItemBinding> {
        private final Callback callback;

        Adapter(Context context, Callback callback) {
            super(context);
            this.callback = callback;
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.file_list_item;
        }

        @Override
        protected void onBindItem(FileListItemBinding binding, String item) {
            binding.setFile(item);
            binding.setCallback(callback);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
        }
    }
}
