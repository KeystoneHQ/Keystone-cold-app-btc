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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.keystone.coinlib.ExtendPubkeyFormat;
import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.ExtendedPublicKeyVersion;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.CollectExpubBinding;
import com.keystone.cold.databinding.CommonModalBinding;
import com.keystone.cold.databinding.XpubFileItemBinding;
import com.keystone.cold.databinding.XpubInputBinding;
import com.keystone.cold.databinding.XpubListBinding;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.fragment.main.scan.scanner.exceptions.UnExpectedQRException;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.FileUtils;
import com.keystone.cold.viewmodel.CollectXpubViewModel;
import com.keystone.cold.viewmodel.SharedDataViewModel;
import com.keystone.cold.viewmodel.exceptions.CollectExPubWrongDataException;
import com.keystone.cold.viewmodel.exceptions.CollectExPubWrongTypeException;
import com.keystone.cold.viewmodel.exceptions.UnknowQrCodeException;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;
import com.sparrowwallet.hummingbird.registry.CryptoAccount;
import com.sparrowwallet.hummingbird.registry.CryptoOutput;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.keystone.cold.viewmodel.GlobalViewModel.hasSdcard;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;

public class CollectExpubFragment extends MultiSigBaseFragment<CollectExpubBinding>
        implements CollectXpubClickHandler {

    private Adapter adapter;
    private List<CollectXpubViewModel.XpubInfo> data;
    private int total;
    private int threshold;
    private Account account;
    private String path;
    private CollectXpubViewModel collectXpubViewModel;

    @Override
    protected int setView() {
        return R.layout.collect_expub;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        extractArguments();
        initializeData();
        mBinding.walletType.setText(getString(R.string.wallet_type, threshold + "-" + total));
        mBinding.addressType.setText(getString(R.string.address_type, getAddressTypeString(account)));
        mBinding.hint.setOnClickListener(v -> showCommonModal(mActivity, getString(R.string.export_multisig_xpub),
                getString(R.string.invalid_xpub_file_hint), getString(R.string.know), null));
        adapter = new Adapter();
        mBinding.list.setAdapter(adapter);
        mBinding.create.setOnClickListener(v -> createWallet());
        mBinding.create.setEnabled(data.stream()
                .allMatch(i -> !TextUtils.isEmpty(i.xpub) && !TextUtils.isEmpty(i.xfp)));
        if (!collectXpubViewModel.startCollect) {
            showHint();
            collectXpubViewModel.startCollect = true;
        }
    }

    private void showHint() {
        ModalDialog.showCommonModal(mActivity, getString(R.string.check_input_pub_key),
                getString(R.string.check_pub_key_hint),
                getString(R.string.know), null);
    }

    private void createWallet() {
        try {
            JSONArray array = new JSONArray();
            for (CollectXpubViewModel.XpubInfo info : data) {
                JSONObject xpub = new JSONObject();
                if (ExtendPubkeyFormat.isValidXpub(info.xpub)) {
                    xpub.put("xfp", info.xfp);
                    xpub.put("xpub", ExtendedPublicKeyVersion.convertXPubVersion(info.xpub, account.getXPubVersion()));
                    array.put(xpub);
                }
            }
            legacyMultiSigViewModel.createMultisigWallet(threshold, account, null, array, "Keystone")
                    .observe(this, walletEntity -> {
                        if (walletEntity != null) {
                            Bundle data = new Bundle();
                            data.putString("wallet_fingerprint", walletEntity.getWalletFingerPrint());
                            data.putBoolean("setup", true);
                            navigate(R.id.action_export_wallet_to_cosigner, data);
                        }
                    });
        } catch (JSONException | XfpNotMatchException e) {
            e.printStackTrace();
        }
    }

    private void extractArguments() {
        Bundle bundle = getArguments();
        Objects.requireNonNull(bundle);
        total = bundle.getInt("total");
        threshold = bundle.getInt("threshold");
        path = bundle.getString("path");
        account = com.keystone.coinlib.accounts.MultiSig.ofPath(path, Utilities.isMainNet(mActivity)).get(0);
    }

    private String getAddressTypeString(Account account) {
        int id = R.string.multi_sig_account_segwit;

        if (account == MultiSig.P2SH_P2WSH
                || account == MultiSig.P2SH_P2WSH_TEST) {
            id = R.string.multi_sig_account_p2sh;
        } else if (account == MultiSig.P2SH || account == MultiSig.P2SH_TEST) {
            id = R.string.multi_sig_account_legacy;
        }

        return String.format(getString(id) + "(%s)", account.isMainNet()
                ? getString(R.string.mainnet) : getString(R.string.testnet));
    }

    private void initializeData() {
        collectXpubViewModel = ViewModelProviders.of(mActivity).get(CollectXpubViewModel.class);
        data = collectXpubViewModel.getXpubInfo();
    }

    @Override
    public void onClickDelete(CollectXpubViewModel.XpubInfo info) {
        info.xpub = null;
        info.xfp = null;
        adapter.notifyItemChanged(info.index - 1);
        mBinding.create.setEnabled(data.stream()
                .allMatch(i -> !TextUtils.isEmpty(i.xpub) && !TextUtils.isEmpty(i.xfp)));
    }

    @Override
    public void onClickScan(CollectXpubViewModel.XpubInfo info) {
        SharedDataViewModel viewModel =
                ViewModelProviders.of(mActivity).get(SharedDataViewModel.class);
        viewModel.setTargetMultiSigAccount(account);
        MutableLiveData<String> scanResult = viewModel.getScanResult();
        scanResult.observe(mActivity, s -> {
            if (!TextUtils.isEmpty(s)) {
                try {
                    JSONObject object = new JSONObject(s);
                    String xfp = object.getString("xfp");
                    String xpub = object.getString("xpub");
                    updateXpubInfo(info, xfp.toUpperCase(), xpub);
                } catch (JSONException e) {
                    e.printStackTrace();
                    showCommonModal(mActivity, getString(R.string.invalid_xpub_file),
                            getString(R.string.invalid_xpub_file_hint),
                            getString(R.string.know), null);
                } finally {
                    scanResult.setValue("");
                    scanResult.removeObservers(mActivity);
                }
            }
        });
        ViewModelProviders.of(mActivity).get(ScannerViewModel.class)
                .setState(new ScannerState(Arrays.asList(ScanResultTypes.UR_CRYPTO_ACCOUNT, ScanResultTypes.PLAIN_TEXT)) {
                    String xpub;

                    @Override
                    public void handleScanResult(ScanResult result) throws Exception {
                        if (result.getType().equals(ScanResultTypes.UR_CRYPTO_ACCOUNT)) {
                            if (handleImportCryptoAccount(result)) return;
                            throw new UnknowQrCodeException("unknown crypto account");
                        } else if (result.getType().equals(ScanResultTypes.PLAIN_TEXT)) {
                            if (handleImportXPubJson(result)) return;
                            throw new UnknowQrCodeException("unknown qrcode");
                        }
                    }

                    @Override
                    public boolean handleException(Exception e) {
                        e.printStackTrace();
                        if (e instanceof UnExpectedQRException
                                || e instanceof CollectExPubWrongDataException || e instanceof JSONException) {
                            mFragment.alert(getString(R.string.invalid_xpub_file),
                                    getString(R.string.invalid_xpub_file_hint),
                                    getString(R.string.know),
                                    () -> mFragment.resetScan());
                            return true;
                        } else if (e instanceof CollectExPubWrongTypeException) {
                            mFragment.alert(getString(R.string.wrong_xpub_format),
                                    getString(R.string.wrong_xpub_format_hint, getAddressTypeString(account),
                                            getAddressTypeString(MultiSig.ofPrefix(xpub.substring(0, 4)).get(0))),
                                    getString(R.string.know), () -> mFragment.resetScan());
                            return true;
                        }
                        return super.handleException(e);
                    }

                    private boolean handleImportCryptoAccount(ScanResult result) throws CollectExPubWrongDataException, JSONException, CollectExPubWrongTypeException {
                        String data = result.getData();
                        CryptoAccount cryptoAccount = collectXpubViewModel.decodeCryptoAccount(data);
                        CryptoOutput cryptoOutput = collectXpubViewModel.collectMultiSigCryptoOutputFromCryptoAccount(cryptoAccount, account);
                        String jsonStr = collectXpubViewModel.handleCollectExPubWithCryptoOutput(cryptoOutput);
                        return resolveXPubJson(jsonStr);
                    }

                    private boolean handleImportXPubJson(ScanResult result) throws JSONException, CollectExPubWrongTypeException {
                        return resolveXPubJson(result.getData());
                    }

                    private boolean resolveXPubJson(String jsonStr) throws JSONException, CollectExPubWrongTypeException {
                        JSONObject jsonObject = new JSONObject(jsonStr);
                        xpub = jsonObject.getString("xpub");
                        String path = jsonObject.getString("path");
                        if (path.equals(account.getPath())
                                && xpub.startsWith(account.getXPubVersion().getName())) {
                            AppExecutors.getInstance().mainThread().execute(() -> viewModel.updateScanResult(jsonObject.toString()));
                            mFragment.navigateUp();
                            return true;
                        }
                        throw new CollectExPubWrongTypeException("wrong xpub type");

                    }
                });
        navigate(R.id.action_to_scanner);
    }

    private void updateXpubInfo(CollectXpubViewModel.XpubInfo info, String xfp, String xpub) {
        for (CollectXpubViewModel.XpubInfo xpubInfo : data) {
            if (xpub.equals(xpubInfo.xpub)) {
                ModalDialog.showCommonModal(mActivity, getString(R.string.duplicate_xpub_title),
                        getString(R.string.duplicate_xpub_hint),
                        getString(R.string.know), null);
                return;
            }
        }
        info.xpub = xpub;
        info.xfp = xfp;
        data.set(info.index - 1, info);
        adapter.notifyItemChanged(info.index - 1);
        mBinding.create.setEnabled(data.stream()
                .allMatch(i -> !TextUtils.isEmpty(i.xpub) && !TextUtils.isEmpty(i.xfp)));
    }

    @Override
    public void onClickSdcard(CollectXpubViewModel.XpubInfo info) {
        if (!hasSdcard()) {
            showXpubList(new ArrayList<>(), info);
        } else {
            collectXpubViewModel.loadXpubFile().observe(this, files -> showXpubList(files, info));
        }
    }

    private void showXpubList(List<File> files, CollectXpubViewModel.XpubInfo info) {
        BottomSheetDialog dialog = new BottomSheetDialog(mActivity);
        XpubListBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.xpub_list, null, false);
        binding.close.setOnClickListener(v -> dialog.dismiss());
        if (!files.isEmpty()) {
            FileListAdapter adapter = new FileListAdapter(mActivity, dialog, info);
            adapter.setItems(files);
            binding.list.setAdapter(adapter);
            binding.list.setVisibility(View.VISIBLE);
            binding.emptyView.setVisibility(View.GONE);
        } else {
            binding.list.setVisibility(View.GONE);
            binding.emptyView.setVisibility(View.VISIBLE);
            if (!hasSdcard()) {
                binding.emptyTitle.setText(R.string.no_sdcard);
                binding.emptyMessage.setText(R.string.no_sdcard_hint);
            } else {
                binding.emptyTitle.setText(R.string.no_pub_file_found);
                binding.emptyMessage.setText(R.string.no_pub_file_found);
            }
        }
        dialog.setContentView(binding.getRoot());
        dialog.show();
    }

    private void readAndDecodeXpubFile(File file, CollectXpubViewModel.XpubInfo info) {
        if (!hasSdcard()) {
            showNoSdcardModal(mActivity);
        } else {
            decodeXpubFile(file, info);
        }
    }

    private void decodeXpubFile(File file, CollectXpubViewModel.XpubInfo info) {
        try {
            String content = FileUtils.readString(file);
            content = content.replaceAll("p2wsh_p2sh", "p2sh_p2wsh");
            JSONObject obj = new JSONObject(content);

            String xpub;
            String path;
            if (obj.has("xpub")) {
                xpub = obj.getString("xpub");
                path = obj.getString("path");
            } else {
                String tag = account.getScript().toLowerCase().replace("-", "_");
                xpub = obj.getString(tag);
                path = obj.getString(tag + "_deriv");
            }
            if (TextUtils.isEmpty(xpub) || !ExtendPubkeyFormat.isValidXpub(xpub)) {
                showCommonModal(mActivity, getString(R.string.invalid_xpub_file),
                        getString(R.string.invalid_xpub_file_hint),
                        getString(R.string.know), null);
                return;
            }
            if (!xpub.startsWith(account.getXPubVersion().getName()) || !path.equals(account.getPath())) {
                ModalDialog.showCommonModal(mActivity, getString(R.string.wrong_xpub_format),
                        getString(R.string.wrong_xpub_format_hint, getAddressTypeString(account),
                                getAddressTypeString(MultiSig.ofPrefix(xpub.substring(0, 4)).get(0))),
                        getString(R.string.know), null);
                return;
            }
            updateXpubInfo(info, obj.getString("xfp"), xpub);
        } catch (Exception e) {
            e.printStackTrace();
            showCommonModal(mActivity, getString(R.string.invalid_xpub_file),
                    getString(R.string.invalid_xpub_file_hint),
                    getString(R.string.know), null);
        }
    }

    private String format(CollectXpubViewModel.XpubInfo info) {
        String index = info.index < 10 ? "0" + info.index : String.valueOf(info.index);
        if (TextUtils.isEmpty(info.xpub)) {
            return index + " ";
        } else {
            return index + " Fingerprint:" + info.xfp + "\n"
                    + mActivity.getString(R.string.extend_pubkey1) + ":" + info.xpub;
        }
    }

    class FileListAdapter extends BaseBindingAdapter<File, XpubFileItemBinding> {
        int selectIndex = -1;
        BottomSheetDialog dialog;
        CollectXpubViewModel.XpubInfo info;

        FileListAdapter(Context context, BottomSheetDialog dialog, CollectXpubViewModel.XpubInfo info) {
            super(context);
            this.dialog = dialog;
            this.info = info;
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.xpub_file_item;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            XpubFileItemBinding binding = DataBindingUtil.getBinding(holder.itemView);
            if (binding != null) {
                if (position == selectIndex) {
                    binding.icon.setVisibility(View.VISIBLE);
                } else {
                    binding.icon.setVisibility(View.GONE);
                }
                onBindItem(binding, this.items.get(position));

                binding.getRoot().setOnClickListener(v -> {
                    selectIndex = position;
                    notifyDataSetChanged();
                    dialog.dismiss();
                    if (selectIndex != -1) {
                        readAndDecodeXpubFile(getItems().get(selectIndex), info);
                    }
                });
            }
        }

        @Override
        protected void onBindItem(XpubFileItemBinding binding, File item) {
            binding.text.setText(item.getName());
        }

    }

    class Adapter extends RecyclerView.Adapter<VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                    R.layout.xpub_input, parent, false).getRoot());
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            XpubInputBinding binding = DataBindingUtil.getBinding(holder.itemView);
            Objects.requireNonNull(binding).setData(data.get(position));
            binding.setClickHandler(CollectExpubFragment.this);
            binding.text.setText(format(data.get(position)));
            binding.executePendingBindings();
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    class VH extends RecyclerView.ViewHolder {
        VH(@NonNull View itemView) {
            super(itemView);
        }
    }


    public static ModalDialog showCommonModal(AppCompatActivity activity,
                                              String title,
                                              String subTitle,
                                              String buttonText,
                                              Runnable confirmAction) {
        ModalDialog dialog = new ModalDialog();
        CommonModalBinding binding = DataBindingUtil.inflate(LayoutInflater.from(activity),
                R.layout.common_modal, null, false);
        binding.title.setText(title);
        binding.subTitle.setText(subTitle);
        binding.subTitle.setGravity(Gravity.START);
        binding.close.setVisibility(View.GONE);
        binding.confirm.setText(buttonText);
        binding.confirm.setOnClickListener(v -> {
            if (confirmAction != null) {
                confirmAction.run();
            }
            dialog.dismiss();
        });
        dialog.setBinding(binding);
        dialog.show(activity.getSupportFragmentManager(), "");
        return dialog;
    }

}


