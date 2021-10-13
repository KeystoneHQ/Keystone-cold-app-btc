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

import android.os.Bundle;
import android.view.View;

import androidx.databinding.ViewDataBinding;
import androidx.lifecycle.ViewModelProviders;

import com.keystone.cold.Utilities;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.viewmodel.multisigs.CaravanMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;

public abstract class MultiSigBaseFragment<T extends ViewDataBinding>
        extends BaseFragment<T> {
    protected LegacyMultiSigViewModel multiSigViewModel;
    protected String mode;

    @Override
    protected void init(View view) {
        if (Utilities.getMultiSigMode(mActivity).equals(MultiSigMode.CARAVAN.getModeId())) {
            multiSigViewModel = ViewModelProviders.of(mActivity).get(CaravanMultiSigViewModel.class);
            mode = "caravan";
        } else {
            multiSigViewModel = ViewModelProviders.of(mActivity).get(LegacyMultiSigViewModel.class);
            mode = "generic";
        }
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
    }


}
