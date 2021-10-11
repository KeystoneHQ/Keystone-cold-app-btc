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

package com.keystone.cold.ui.fragment.multisigs.common;

import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.MultisigModeBottomSheetBinding;
import com.keystone.cold.databinding.SelectWalletModeBinding;
import com.keystone.cold.db.entity.CaravanMultiSigWalletEntity;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.multisigs.MultiSigPreferenceFragment;
import com.keystone.cold.viewmodel.multisigs.CaravanMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.CasaMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;

import java.util.ArrayList;
import java.util.List;

public abstract class MultiSigEntryBaseFragment<T extends ViewDataBinding>
        extends BaseFragment<T> implements MultiSigPreferenceFragment.MultiSigModeCallback {
    protected LegacyMultiSigViewModel legacyMultiSigViewModel;
    protected CaravanMultiSigViewModel caravanMultiSigViewModel;
    protected CasaMultiSigViewModel casaMultiSigViewModel;
    private BottomSheetDialog dialog;
    private Adapter adapter;

    @Override
    protected void init(View view) {
        legacyMultiSigViewModel = ViewModelProviders.of(mActivity).get(LegacyMultiSigViewModel.class);
        caravanMultiSigViewModel = ViewModelProviders.of(mActivity).get(CaravanMultiSigViewModel.class);
        casaMultiSigViewModel = ViewModelProviders.of(mActivity).get(CasaMultiSigViewModel.class);
        dialog = new BottomSheetDialog(mActivity);
        adapter = new Adapter(mActivity);
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        String modeId = Utilities.getMultiSigMode(mActivity);
        String[] entries = getResources().getStringArray(getEntries());
        String[] values = getResources().getStringArray(getValues());
        List<Pair<String, String>> displayItems = new ArrayList<>();

        for (int i = 0; i < values.length; i++) {
            String _modeId = values[i];
            if (modeId.equals(_modeId)) {
                continue;
            }
            String _entry = entries[i];
            displayItems.add(new Pair<>(_modeId, _entry));
        }
        adapter.setItems(displayItems);
        MultisigModeBottomSheetBinding binding = DataBindingUtil.inflate(LayoutInflater.from(mActivity),
                R.layout.multisig_mode_bottom_sheet, null, false);
        binding.modeList.setAdapter(adapter);
        dialog.setContentView(binding.getRoot());
    }

    private int getEntries() {
        return R.array.multisig_mode_list;
    }

    private int getValues() {
        return R.array.multisig_mode_list_values;
    }

    protected void showMultisigSelection() {
        dialog.show();
    }

    @Override
    public void onSelect(String modeId) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            if (modeId.equals(MultiSigMode.CASA.getModeId())) {
                dialog.dismiss();
                if (Utilities.getCasaSetUpVisitedTime(mActivity) < 1) {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("isGuide", true);
                    navigate(R.id.action_to_casaGuidePageOneFragment, bundle);
                } else {
                    navigate(R.id.action_to_casaMultisigFragment);
                }
            } else if (modeId.equals(MultiSigMode.CARAVAN.getModeId())) {
                dialog.dismiss();
                Bundle bundle = new Bundle();
                MultiSigWalletEntity caravanCurrentWallet = caravanMultiSigViewModel.getCurrentWalletSync();
                if (caravanCurrentWallet != null) {
                    bundle.putString("walletFingerPrint", caravanCurrentWallet.toCaravanWallet().getWalletFingerPrint());
                }
                navigate(R.id.action_to_caravanMultisigFragment, bundle);
            } else if (modeId.equals(MultiSigMode.LEGACY.getModeId())) {
                dialog.dismiss();
                Bundle bundle = new Bundle();
                MultiSigWalletEntity legacyCurrentWallet = legacyMultiSigViewModel.getCurrentWalletSync();
                if (legacyCurrentWallet != null) {
                    bundle.putString("walletFingerPrint", legacyCurrentWallet.getWalletFingerPrint());
                }
                navigate(R.id.action_to_legacyMultisigFragment, bundle);
            }
        });
    }

    protected class Adapter extends BaseBindingAdapter<Pair<String, String>, SelectWalletModeBinding> {

        public Adapter(Context context) {
            super(context);
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.select_wallet_mode;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SelectWalletModeBinding binding = DataBindingUtil.getBinding(holder.itemView);
            binding.title.setText(items.get(position).second);
            binding.checkbox.setVisibility(View.GONE);
            binding.subTitle.setVisibility(View.GONE);
            binding.arrowRight.setVisibility(View.VISIBLE);
            binding.setModeId(items.get(position).first);
            binding.divider.setVisibility(View.GONE);
            binding.setCallback(MultiSigEntryBaseFragment.this);
        }

        @Override
        protected void onBindItem(SelectWalletModeBinding binding, Pair<String, String> item) {

        }
    }
}
