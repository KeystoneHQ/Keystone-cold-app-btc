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

package com.keystone.cold.ui.fragment.multisigs.casa;

import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;

import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;

import com.keystone.cold.R;
import com.keystone.cold.databinding.SignedTxBinding;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.TransactionItem;
import com.keystone.cold.ui.fragment.main.TransactionItemAdapter;
import com.keystone.cold.viewmodel.CoinListViewModel;
import com.keystone.cold.viewmodel.GlobalViewModel;
import com.keystone.cold.viewmodel.WatchWallet;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;

import java.util.ArrayList;
import java.util.List;

import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.KEY_DUPLICATE_TX;
import static com.keystone.cold.ui.fragment.main.PsbtTxConfirmFragment.showExportPsbtDialog;

public class CasaSignedPsbtFragment extends BaseFragment<SignedTxBinding> {

    public static final String KEY_ID = "id";
    protected CasaSignature casaSignature;
    protected WatchWallet watchWallet;
    private List<String> changeAddress = new ArrayList<>();

    @Override
    protected int setView() {
        return R.layout.signed_tx;
    }

    @Override
    protected void init(View view) {
        Bundle data = requireArguments();
        mBinding.toolbar.setNavigationOnClickListener(v -> {
            if (data.getBoolean(KEY_DUPLICATE_TX)) {
                NavHostFragment.findNavController(this)
                        .popBackStack(R.id.assetFragment, false);
            } else {
                navigateUp();
            }
        });
        watchWallet = WatchWallet.getWatchWallet(mActivity);
        String walletName = watchWallet.getWalletName(mActivity);
        mBinding.txDetail.watchWallet.setText(walletName);
        ViewModelProviders.of(mActivity)
                .get(GlobalViewModel.class)
                .getChangeAddress()
                .observe(this, address -> this.changeAddress = address);
        CoinListViewModel viewModel = ViewModelProviders.of(mActivity).get(CoinListViewModel.class);
        viewModel.loadCasaSignature(String.valueOf(data.getLong(KEY_ID))).observe(this, casaSignature -> {
            mBinding.setTx(casaSignature);
            this.casaSignature = casaSignature;
            displaySignResult(casaSignature);
            refreshAmount();
            refreshFromList();
            refreshReceiveList();
            refreshSignStatus();
            mBinding.txDetail.exportToSdcard.setOnClickListener(v -> showExportDialog());
        });

    }

    private void refreshSignStatus() {
        if (!TextUtils.isEmpty(casaSignature.getSignStatus())) {
            mBinding.txDetail.txSignStatus.setVisibility(View.VISIBLE);
            String signStatus = casaSignature.getSignStatus();

            String[] splits = signStatus.split("-");
            int sigNumber = Integer.parseInt(splits[0]);
            int reqSigNumber = Integer.parseInt(splits[1]);

            String text;
            if (sigNumber == 0) {
                text = getString(R.string.unsigned);
            } else if (sigNumber < reqSigNumber) {
                text = getString(R.string.partial_signed);
                mBinding.txDetail.broadcastGuide.setVisibility(View.GONE);
            } else {
                text = getString(R.string.signed);
            }

            mBinding.txDetail.signStatus.setText(text);
        } else {
            mBinding.txDetail.txSource.setVisibility(View.VISIBLE);
        }
    }

    protected void showExportDialog() {
        showExportPsbtDialog(mActivity, casaSignature, null);
    }

    private void refreshFromList() {
        String from = casaSignature.getFrom();
        List<TransactionItem> items = new ArrayList<>();
        try {
            JSONArray outputs = new JSONArray(from);
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject out = outputs.getJSONObject(i);
                items.add(new TransactionItem(i,
                        out.getLong("value"), out.getString("address"),
                        casaSignature.getCoinCode()));
            }
        } catch (JSONException e) {
            return;
        }
        TransactionItemAdapter adapter
                = new TransactionItemAdapter(mActivity,
                TransactionItem.ItemType.INPUT, changeAddress);
        adapter.setItems(items);
        mBinding.txDetail.fromList.setAdapter(adapter);
    }

    private void refreshAmount() {
        SpannableStringBuilder style = new SpannableStringBuilder(casaSignature.getAmount());
        style.setSpan(new ForegroundColorSpan(mActivity.getColor(R.color.colorAccent)),
                0, casaSignature.getAmount().indexOf(" "), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mBinding.txDetail.amount.setText(style);
    }

    private void refreshReceiveList() {
        String to = casaSignature.getTo();
        List<TransactionItem> items = new ArrayList<>();
        try {
            JSONArray outputs = new JSONArray(to);
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject output = outputs.getJSONObject(i);
                boolean isChange = output.optBoolean("isChange");
                String changePath = null;
                if (isChange) {
                    changePath = output.getString("changeAddressPath");
                }

                items.add(new TransactionItem(i,
                        output.getLong("value"),
                        output.getString("address"),
                        casaSignature.getCoinCode(), changePath));
            }
        } catch (JSONException e) {
            return;
        }
        TransactionItemAdapter adapter =
                new TransactionItemAdapter(mActivity,
                        TransactionItem.ItemType.OUTPUT,
                        changeAddress);
        adapter.setItems(items);
        mBinding.txDetail.toList.setAdapter(adapter);
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }

    protected void displaySignResult(CasaSignature casaSignature) {
        mBinding.txDetail.txIdInfo.setVisibility(View.GONE);
        mBinding.txDetail.arrowDown.setVisibility(View.GONE);
        mBinding.txDetail.scanInfo.setVisibility(View.GONE);
        mBinding.txDetail.export.setVisibility(View.GONE);

        mBinding.txDetail.dynamicQrcodeLayout.qrcode.setVisibility(View.VISIBLE);
        mBinding.txDetail.exportToSdcardHint.setVisibility(View.VISIBLE);
        byte[] psbtBytes = Base64.decode(casaSignature.getSignedHex());
        mBinding.txDetail.dynamicQrcodeLayout.qrcode.setData(new CryptoPSBT(psbtBytes).toUR().toString());
        mBinding.txDetail.dynamicQrcodeLayout.hint.setVisibility(View.GONE);
        mBinding.txDetail.qrcodeLayout.qrcode.setVisibility(View.GONE);
        mBinding.txDetail.broadcastGuide.setVisibility(View.GONE);
        mBinding.txDetail.export.setVisibility(View.GONE);
        mBinding.txDetail.exportToSdcardHint.setVisibility(View.INVISIBLE);
    }

}
