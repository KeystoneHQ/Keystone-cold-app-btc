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
import android.content.SharedPreferences;
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
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.databinding.MultisigModeBottomSheetBinding;
import com.keystone.cold.databinding.SettingItemWithArrowCallableBinding;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.setting.ListPreferenceCallback;
import com.keystone.cold.viewmodel.multisigs.CasaMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;

import java.util.ArrayList;
import java.util.List;

public abstract class MultiSigEntryBaseFragment<T extends ViewDataBinding>
        extends BaseFragment<T> implements ListPreferenceCallback {
    protected LegacyMultiSigViewModel legacyMultiSigViewModel;
    protected CasaMultiSigViewModel casaMultiSigViewModel;
    private BottomSheetDialog dialog;
    private Adapter adapter;

    @Override
    protected void init(View view) {
        legacyMultiSigViewModel = ViewModelProviders.of(mActivity).get(LegacyMultiSigViewModel.class);
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
    public void onSelect(int modeId) {
        navigateUp();
        String _modeId = String.valueOf(modeId);
        if (_modeId.equals(MultiSigMode.LEGACY.getModeId())) {
            Utilities.setMultiSigMode(mActivity, MultiSigMode.LEGACY.getModeId());
            dialog.dismiss();
            navigate(R.id.action_to_legacyMultisigFragment);
        } else {
            Utilities.setMultiSigMode(mActivity, MultiSigMode.CASA.getModeId());
            dialog.dismiss();
            navigate(R.id.action_to_casaMultisigFragment);
        }
    }

    protected class Adapter extends BaseBindingAdapter<Pair<String, String>, SettingItemWithArrowCallableBinding> {

        public Adapter(Context context) {
            super(context);
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.setting_item_with_arrow_callable;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            SettingItemWithArrowCallableBinding binding = DataBindingUtil.getBinding(holder.itemView);
            binding.title.setText(items.get(position).second);
            binding.setIndex(Integer.parseInt(items.get(position).first));
            binding.setCallback(MultiSigEntryBaseFragment.this);
        }

        @Override
        protected void onBindItem(SettingItemWithArrowCallableBinding binding, Pair<String, String> item) {
        }
    }
}
