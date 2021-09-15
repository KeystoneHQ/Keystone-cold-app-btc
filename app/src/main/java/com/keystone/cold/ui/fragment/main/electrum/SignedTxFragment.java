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

package com.keystone.cold.ui.fragment.main.electrum;

import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.KEY_DUPLICATE_TX;
import static com.keystone.cold.ui.fragment.main.electrum.ElectrumBroadcastTxFragment.showElectrumInfo;
import static com.keystone.cold.ui.modal.ExportPsbtDialog.showExportPsbtDialog;
import static com.keystone.cold.viewmodel.WatchWallet.PSBT_MULTISIG_SIGN_ID;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;

import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.fragment.NavHostFragment;

import com.keystone.coinlib.utils.Base43;
import com.keystone.cold.R;
import com.keystone.cold.databinding.SignedTxBinding;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.TransactionItem;
import com.keystone.cold.ui.fragment.main.TransactionItemAdapter;
import com.keystone.cold.viewmodel.CoinListViewModel;
import com.keystone.cold.viewmodel.GlobalViewModel;
import com.keystone.cold.viewmodel.WatchWallet;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;

import java.util.ArrayList;
import java.util.List;


public class SignedTxFragment extends BaseFragment<SignedTxBinding> {

    private static final String KEY_TX_ID = "txid";
    protected TxEntity txEntity;
    protected WatchWallet watchWallet;
    private boolean isMultiSig;

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

        CoinListViewModel viewModel = ViewModelProviders.of(mActivity).get(CoinListViewModel.class);
        viewModel.loadTx(data.getString(KEY_TX_ID)).observe(this, txEntity -> {
            mBinding.setTx(txEntity);
            this.txEntity = txEntity;
            isMultiSig = txEntity.getSignId().equals(PSBT_MULTISIG_SIGN_ID);
            displaySignResult(txEntity);
            refreshFromList();
            ViewModelProviders.of(mActivity)
                    .get(GlobalViewModel.class)
                    .getChangeAddress()
                    .observe(this, address -> {
                        if (address != null) {
                            refreshReceiveList(address);
                        }
                    });
            refreshSignStatus();
            mBinding.txDetail.exportToSdcard.setOnClickListener(v -> showExportDialog());
        });
    }

    private void refreshSignStatus() {
        if (!TextUtils.isEmpty(txEntity.getSignStatus())) {
            mBinding.txDetail.txSignStatus.setVisibility(View.VISIBLE);
            String signStatus = txEntity.getSignStatus();

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
        showExportPsbtDialog(mActivity, txEntity, null);
    }

    private void refreshFromList() {
        String from = txEntity.getFrom();
        List<TransactionItem> items = new ArrayList<>();
        try {
            JSONArray outputs = new JSONArray(from);
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject out = outputs.getJSONObject(i);
                items.add(new TransactionItem(i,
                        out.getLong("value"), out.getString("address"),
                        txEntity.getCoinCode()));
            }
        } catch (JSONException e) {
            return;
        }
        TransactionItemAdapter adapter
                = new TransactionItemAdapter(mActivity,
                TransactionItem.ItemType.INPUT);
        adapter.setItems(items);
        mBinding.txDetail.fromList.setAdapter(adapter);
    }

    private void refreshReceiveList(List<String> changeAddress) {
        String to = txEntity.getTo();
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
                        txEntity.getCoinCode(), changePath));
            }
        } catch (JSONException e) {
            return;
        }
        TransactionItemAdapter adapter;
        if (isMultiSig) {
            adapter = new TransactionItemAdapter(mActivity,
                            TransactionItem.ItemType.OUTPUT);
        } else {
            adapter = new TransactionItemAdapter(mActivity,
                    TransactionItem.ItemType.OUTPUT, changeAddress);
        }
        adapter.setItems(items);
        mBinding.txDetail.toList.setAdapter(adapter);
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }

    protected void displaySignResult(TxEntity txEntity) {
        if (txEntity.getSignedHex().length() <= 800) {
            byte[] data = Base64.decode(txEntity.getSignedHex());
            String base43 = Base43.encode(data);
            new Handler().postDelayed(() -> mBinding.txDetail.qrcodeLayout.qrcode.setData(base43), 500);
            mBinding.txDetail.export.setVisibility(View.GONE);
            mBinding.txDetail.exportToSdcardHint.setOnClickListener(v -> showExportDialog());
            mBinding.txDetail.info.setOnClickListener(v -> showElectrumInfo(mActivity));
        } else {
            mBinding.txDetail.qr.setVisibility(View.GONE);
        }
    }

}
