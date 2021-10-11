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

package com.keystone.cold.db.dao;


import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.keystone.cold.db.entity.CaravanMultiSigWalletEntity;

import java.util.List;

@Dao
public interface CaravanMultiSigWalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long add(CaravanMultiSigWalletEntity wallet);

    @Query("SELECT * FROM caravan_multi_sig_wallet WHERE belongTo=:xfp")
    LiveData<List<CaravanMultiSigWalletEntity>> loadAll(String xfp);

    @Query("SELECT * FROM caravan_multi_sig_wallet WHERE belongTo=:xfp")
    List<CaravanMultiSigWalletEntity> loadAllSync(String xfp);

    @Update
    int update(CaravanMultiSigWalletEntity walletEntity);

    @Query("DELETE FROM caravan_multi_sig_wallet WHERE walletFingerPrint=:walletFingerPrint")
    int delete(String walletFingerPrint);

    @Query("DELETE FROM caravan_multi_sig_wallet")
    int deleteAll();

    @Query("SELECT * FROM caravan_multi_sig_wallet WHERE walletFingerPrint=:walletFingerPrint")
    CaravanMultiSigWalletEntity loadWallet(String walletFingerPrint);
}