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

package com.keystone.cold.ui.fragment.setting;

import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.keystone.coinlib.utils.Account;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.SettingItemSelectableBinding;
import com.keystone.cold.ui.MainActivity;
import com.keystone.cold.ui.SetupVaultActivity;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.viewmodel.WatchWallet;

import java.util.ArrayList;
import java.util.List;

import static com.keystone.cold.ui.fragment.Constants.KEY_TITLE;
import static com.keystone.cold.ui.fragment.setting.MainPreferenceFragment.SETTING_ADDRESS_FORMAT;
import static com.keystone.cold.ui.fragment.setting.MainPreferenceFragment.SETTING_CHOOSE_WATCH_WALLET;
import static com.keystone.cold.viewmodel.WatchWallet.getWatchWallet;

public class ChooseWatchWalletFragment extends ListPreferenceFragment {

    private Adapter adapter;
    private List<Pair<String,String>> displayItems;
    @Override
    protected void init(View view) {
        Bundle data = getArguments();
        boolean isMainnet = Utilities.isMainNet(mActivity);
        if (data != null) {
            mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
            mBinding.toolbarTitle.setText(data.getInt(KEY_TITLE));
        } else {
            mBinding.toolbar.setVisibility(View.GONE);
        }
        prefs = Utilities.getPrefs(mActivity);
        entries = getResources().getStringArray(getEntries());
        values = getResources().getStringArray(getValues());
        value = prefs.getString(getKey(), defaultValue());
        adapter = new Adapter(mActivity);

        displayItems = new ArrayList<>();
        for (int i =0; i < entries.length; i++ ) {
            if (isMainnet || WatchWallet.getWatchWalletById(values[i].toString()).supportTestnet()) {
                displayItems.add(Pair.create(values[i].toString(), entries[i].toString()));
            }
        }

        if (mActivity instanceof SetupVaultActivity) {
            adapter.setItems(displayItems.subList(0, displayItems.size() - 1));
        } else {
            adapter.setItems(displayItems);
        }
        mBinding.list.setAdapter(adapter);
        mBinding.confirm.setText(R.string.confirm);
        mBinding.confirm.setOnClickListener(v -> popBackStack(R.id.assetFragment, false));
        if (mActivity instanceof MainActivity) {
            mBinding.confirm.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected int getEntries() {
        return R.array.watch_wallet_list;
    }

    @Override
    protected int getValues() {
        return R.array.watch_wallet_list_values;
    }

    @Override
    protected String getKey() {
        return SETTING_CHOOSE_WATCH_WALLET;
    }

    @Override
    protected String defaultValue() {
        return WatchWallet.KEYSTONE.getWalletId();
    }

    @Override
    public void onSelect(int walletId) {
        String old = value;
        value = String.valueOf(walletId);
        if (!old.equals(value)) {
            setWatchWallet();
            adapter.notifyDataSetChanged();
        }
    }

    private void setWatchWallet() {
        if (prefs.edit().putString(SETTING_CHOOSE_WATCH_WALLET, value).commit()) {
            WatchWallet wallet = getWatchWallet(mActivity);
            if (wallet.supportNativeSegwit()) {
                prefs.edit().putString(SETTING_ADDRESS_FORMAT, Account.P2WPKH.getType()).apply();
            } else if (wallet.supportNestedSegwit()) {
                prefs.edit().putString(SETTING_ADDRESS_FORMAT, Account.P2SH_P2WPKH.getType()).apply();
            }
        }
    }

    protected class Adapter extends BaseBindingAdapter<Pair<String,String>, SettingItemSelectableBinding> {

        public Adapter(Context context) {
            super(context);
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.setting_item_selectable;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SettingItemSelectableBinding binding = DataBindingUtil.getBinding(holder.itemView);
            binding.title.setText(displayItems.get(position).second);
            if (subTitles == null) {
                binding.subTitle.setVisibility(View.GONE);
            } else {
                binding.subTitle.setVisibility(View.VISIBLE);
                binding.subTitle.setText(subTitles[position]);
            }
            binding.setIndex(Integer.parseInt(displayItems.get(position).first));
            binding.setCallback(ChooseWatchWalletFragment.this);
            binding.setChecked(displayItems.get(position).first.equals(value));
        }

        @Override
        protected void onBindItem(SettingItemSelectableBinding binding, Pair<String,String> item) {
        }
    }
}

