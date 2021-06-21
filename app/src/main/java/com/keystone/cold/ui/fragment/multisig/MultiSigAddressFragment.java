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

package com.keystone.cold.ui.fragment.multisig;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.R;
import com.keystone.cold.databinding.AddressFragmentBinding;
import com.keystone.cold.databinding.MultisigAddressItemBinding;
import com.keystone.cold.db.entity.MultiSigAddressEntity;
import com.keystone.cold.ui.common.BaseBindingAdapter;

import java.util.List;
import java.util.Objects;

import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS;
import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS_NAME;
import static com.keystone.cold.ui.fragment.Constants.KEY_ADDRESS_PATH;
import static com.keystone.cold.ui.fragment.Constants.KEY_COIN_CODE;
import static com.keystone.cold.ui.fragment.Constants.KEY_COIN_ID;
import static com.keystone.cold.ui.fragment.Constants.KEY_IS_CHANGE_ADDRESS;
import static com.keystone.cold.ui.fragment.Constants.KEY_WALLET_FINGERPRINT;

public class MultiSigAddressFragment extends MultiSigBaseFragment<AddressFragmentBinding> {

    private boolean isChangeAddress;
    private List<MultiSigAddressEntity> addressEntities;
    private LiveData<List<MultiSigAddressEntity>> address;
    private final AddressClickCallback mAddrCallback = addr -> {
        Bundle bundle = Objects.requireNonNull(getArguments());
        Bundle data = new Bundle();
        data.putString(KEY_COIN_CODE, bundle.getString(KEY_COIN_CODE));
        data.putString(KEY_ADDRESS, addr.getAddress());
        data.putString(KEY_ADDRESS_NAME, addr.getName());
        data.putString(KEY_ADDRESS_PATH, addr.getPath());
        navigate(R.id.action_to_receiveCoinFragment, data);
    };

    private AddressAdapter mAddressAdapter;

    public static MultiSigAddressFragment newInstance(@NonNull String coinId,
                                                      boolean isChange,
                                                      String walletFingerprint) {
        MultiSigAddressFragment fragment = new MultiSigAddressFragment();
        Bundle args = new Bundle();
        args.putString(KEY_COIN_ID, coinId);
        args.putString(KEY_COIN_CODE, Coins.coinCodeFromCoinId(coinId));
        args.putBoolean(KEY_IS_CHANGE_ADDRESS, isChange);
        args.putString(KEY_WALLET_FINGERPRINT, walletFingerprint);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected int setView() {
        return R.layout.address_fragment;
    }

    @Override
    protected void init(View view) {
        super.init(view);
        mAddressAdapter = new AddressAdapter(mActivity, mAddrCallback);
        mBinding.addrList.setAdapter(mAddressAdapter);
    }

    @Override
    protected void initData(Bundle savedInstanceState) {
        Bundle data = Objects.requireNonNull(getArguments());
        isChangeAddress = data.getBoolean(KEY_IS_CHANGE_ADDRESS);
        Objects.requireNonNull(getParentFragment());

        String walletFingerprint = data.getString(KEY_WALLET_FINGERPRINT);
        loadAddress(walletFingerprint);
    }

    void loadAddress(String walletFingerprint) {
        if (address != null) {
            address.removeObservers(this);
            address = null;
        }
        address = viewModel.getMultiSigAddress(walletFingerprint);
        subscribeUi(address);
    }

    private void subscribeUi(LiveData<List<MultiSigAddressEntity>> address) {
        if (addressEntities != null) {
            updateAddressList(addressEntities);
        }
        address.observe(this, entities -> {
            addressEntities = entities;
            updateAddressList(entities);
        });
    }

    private void updateAddressList(List<MultiSigAddressEntity> entities) {

        if (isChangeAddress) {
            mAddressAdapter.setItems(viewModel.filterChangeAddress(entities));
        } else {
            mAddressAdapter.setItems(viewModel.filterReceiveAddress(entities));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }
}

class AddressAdapter extends BaseBindingAdapter<MultiSigAddressEntity, MultisigAddressItemBinding> {

    private final AddressClickCallback mAddressCallback;

    AddressAdapter(Context context, AddressClickCallback callback) {
        super(context);
        mAddressCallback = callback;

    }

    @Override
    protected int getLayoutResId(int viewType) {
        return R.layout.multisig_address_item;
    }

    @Override
    protected void onBindItem(MultisigAddressItemBinding binding, MultiSigAddressEntity item) {
        binding.setAddress(item);
        binding.setCallback(mAddressCallback);
    }
}

