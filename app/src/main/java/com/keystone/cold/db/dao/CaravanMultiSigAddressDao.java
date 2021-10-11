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

package com.keystone.cold.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.keystone.cold.db.entity.CaravanMultiSigAddressEntity;

import java.util.List;

@Dao
public interface CaravanMultiSigAddressDao {
    @Query("SELECT * FROM caravan_multi_sig_address where walletFingerPrint=:walletFingerPrint")
    List<CaravanMultiSigAddressEntity> loadAllCaravanMultiSigAddressSync(String walletFingerPrint);

    @Query("SELECT * FROM caravan_multi_sig_address where walletFingerPrint=:walletFingerPrint")
    LiveData<List<CaravanMultiSigAddressEntity>> loadAllCaravanMultiSigAddress(String walletFingerPrint);

    @Query("SELECT * FROM caravan_multi_sig_address where walletFingerPrint=:walletFingerPrint AND path=:path")
    CaravanMultiSigAddressEntity loadCaravanAddressByPath(String walletFingerPrint, String path);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(List<CaravanMultiSigAddressEntity> caravanAddressEntities);
}