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

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.keystone.coinlib.utils.Account;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.TxListBinding;
import com.keystone.cold.databinding.TxListItemBinding;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.ui.common.FilterableBaseBindingAdapter;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.viewmodel.CoinListViewModel;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;
import com.keystone.cold.viewmodel.WatchWallet;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.keystone.cold.ui.fragment.Constants.KEY_COIN_ID;
import static com.keystone.cold.ui.fragment.main.TxFragment.KEY_TX_ID;
import static com.keystone.cold.viewmodel.GlobalViewModel.getAccount;
import static com.keystone.cold.viewmodel.WatchWallet.ELECTRUM;
import static com.keystone.cold.viewmodel.WatchWallet.ELECTRUM_SIGN_ID;
import static com.keystone.cold.viewmodel.WatchWallet.PSBT_MULTISIG_SIGN_ID;
import static com.keystone.cold.viewmodel.WatchWallet.getWatchWallet;

public class TxListFragment extends BaseFragment<TxListBinding> {

    private TxAdapter adapter;
    private TxCallback txCallback;
    private String query;
    private boolean multisig;
    private String walletFingerprint;

    @Override
    protected int setView() {
        return R.layout.tx_list;
    }

    @Override
    protected void init(View view) {
        Bundle data = Objects.requireNonNull(getArguments());
        multisig = data.getBoolean("multisig");
        if (multisig) {
            walletFingerprint = data.getString("wallet_fingerprint");
        }
        CoinListViewModel viewModel = ViewModelProviders.of(mActivity)
                .get(CoinListViewModel.class);
        adapter = new TxAdapter(mActivity);
        mBinding.list.setAdapter(adapter);
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        txCallback = tx -> {
            Bundle bundle = new Bundle();
            bundle.putString(KEY_TX_ID, tx.getTxId());
            if (tx.getSignId().startsWith(ELECTRUM_SIGN_ID)) {
                navigate(R.id.action_to_electrumTxFragment, bundle);
            } else if (getWatchWallet(mActivity).supportPsbt() || PSBT_MULTISIG_SIGN_ID.equals(tx.getSignId())) {
                navigate(R.id.action_to_psbtSignedTxFragment, bundle);
            } else {
                navigate(R.id.action_to_txFragment, bundle);
            }
        };

        LiveData<List<TxEntity>> txs;
        if (multisig) {
            LegacyMultiSigViewModel vm = ViewModelProviders.of(this).get(LegacyMultiSigViewModel.class);
            txs = vm.loadTxs(walletFingerprint);
        } else {
            txs = viewModel.loadTxs(data.getString(KEY_COIN_ID));
        }
        txs.observe(this, txEntities -> {
            if (!multisig) {
                txEntities = txEntities.stream()
                        .filter(this::filterSingleSig)
                        .filter(this::shouldShow)
                        .filter(this::filterByMode)
                        .sorted((o1, o2) -> (int) (o2.getTimeStamp() - o1.getTimeStamp()))
                        .collect(Collectors.toList());
            } else {
                Collections.reverse(txEntities);
            }

            if (txEntities.isEmpty()) {
                showEmpty(true);
            } else {
                showEmpty(false);
                adapter.setItems(txEntities);
            }
        });
        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                if (!TextUtils.isEmpty(query) && adapter.getItemCount() == 0) {
                    mBinding.empty.setVisibility(View.VISIBLE);
                    mBinding.list.setVisibility(View.GONE);
                } else {
                    mBinding.empty.setVisibility(View.GONE);
                    mBinding.list.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void showEmpty(boolean empty) {
        if (empty) {
            mBinding.list.setVisibility(View.GONE);
            mBinding.txid.setVisibility(View.GONE);
            mBinding.empty.setVisibility(View.VISIBLE);
        } else {
            mBinding.list.setVisibility(View.VISIBLE);
            mBinding.txid.setVisibility(View.VISIBLE);
            mBinding.empty.setVisibility(View.GONE);
        }
    }

    private boolean filterByMode(TxEntity txEntity) {
        WatchWallet watchWallet = getWatchWallet(mActivity);
        if (watchWallet == WatchWallet.KEYSTONE) {
            return !txEntity.getSignId().contains("_sign_id")
                    && !txEntity.getSignId().contains(PSBT_MULTISIG_SIGN_ID);
        } else {
            if (watchWallet == ELECTRUM) {
                Account account = getAccount(mActivity);
                if (account == Account.P2WPKH || account == Account.P2WPKH_TESTNET) {
                    return (watchWallet.getSignId()+"_NATIVE_SEGWIT").equals(txEntity.getSignId());
                } else {
                    return watchWallet.getSignId().equals(txEntity.getSignId());
                }
            } else {
                return watchWallet.getSignId().equals(txEntity.getSignId());
            }
        }
    }

    private boolean shouldShow(TxEntity tx) {
        return Utilities.getCurrentBelongTo(mActivity).equals(tx.getBelongTo());
    }

    private boolean filterSingleSig(TxEntity txEntity) {
        return TextUtils.isEmpty(txEntity.getSignStatus());
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
    }

    public void setQuery(String s) {
        if (adapter != null) {
            query = s;
            adapter.getFilter().filter(s);
        }
    }

    class TxAdapter extends FilterableBaseBindingAdapter<TxEntity, TxListItemBinding> {

        TxAdapter(Context context) {
            super(context);
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.tx_list_item;
        }

        @Override
        protected void onBindItem(TxListItemBinding binding, TxEntity item) {
            binding.setTx(item);
            binding.setTxCallback(txCallback);

        }
    }
}


