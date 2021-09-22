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

package com.keystone.cold.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.keystone.coinlib.exception.InvalidPathException;
import com.keystone.coinlib.path.AddressIndex;
import com.keystone.coinlib.path.CoinPath;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.DataRepository;
import com.keystone.cold.MainApplication;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.db.entity.AddressEntity;
import com.keystone.cold.db.entity.CoinEntity;

import java.util.List;
import java.util.stream.Collectors;

public class CoinViewModel extends AndroidViewModel {

    private final DataRepository mRepository;

    public CoinViewModel(@NonNull Application application) {
        super(application);
        mRepository = ((MainApplication) application).getRepository();
    }

    public List<AddressEntity> filterChangeAddress(List<AddressEntity> addressEntities) {
        return addressEntities.stream()
                .filter(this::isChangeAddress)
                .collect(Collectors.toList());
    }

    public List<AddressEntity> filterReceiveAddress(List<AddressEntity> addressEntities) {
        return addressEntities.stream()
                .filter(addressEntity -> !isChangeAddress(addressEntity))
                .collect(Collectors.toList());
    }

    public List<AddressEntity> filterByAccountHdPath(List<AddressEntity> addressEntities, String hdPath) {
        return addressEntities.stream()
                .filter(addressEntity -> addressEntity.getPath().toUpperCase().startsWith(hdPath))
                .collect(Collectors.toList());
    }

    private boolean isChangeAddress(AddressEntity addressEntity) {
        String path = addressEntity.getPath();
        try {
            AddressIndex addressIndex = CoinPath.parsePath(path);
            return !addressIndex.getParent().isExternal();
        } catch (InvalidPathException e) {
            e.printStackTrace();
        }
        return false;
    }

    public LiveData<List<AddressEntity>> getAddress() {
        return mRepository.loadAllAddress();
    }

    public void updateAddress(AddressEntity addr) {
        AppExecutors.getInstance().diskIO().execute(() -> mRepository.updateAddress(addr));
    }

    public void initDefultAddress(boolean isChangeAddress, String accountHdPath) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String coinId = Utilities.currentCoin(getApplication()).coinId();
            List<AddressEntity> filteredEntity = filterByAccountHdPath(mRepository.loadAddressSync(coinId), accountHdPath);
            List<AddressEntity> addressEntities;
            if (isChangeAddress) {
                addressEntities = filterChangeAddress(filteredEntity);
            } else {
                addressEntities = filterReceiveAddress(filteredEntity);
            }
            if (addressEntities.isEmpty()) {
                CoinEntity coinEntity = mRepository.loadCoinSync(coinId);
                String xPub = new GetExtendedPublicKeyCallable(accountHdPath).call();
                new AddAddressViewModel.AddAddressTask(coinEntity, mRepository,
                        null, xPub, isChangeAddress ? 1 : 0)
                        .execute(1);
            }
        });
    }
}
