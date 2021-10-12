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

import com.keystone.cold.db.entity.CaravanTxEntity;

import java.util.List;

@Dao
public interface CaravanTxDao {
    @Query("SELECT * FROM txs where coinId = :coinId ORDER BY timeStamp DESC")
    LiveData<List<CaravanTxEntity>> loadTxs(String coinId);

    @Query("SELECT * FROM txs where coinId = :coinId")
    List<CaravanTxEntity> loadTxsSync(String coinId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CaravanTxEntity tx);

    @Query("SELECT * FROM txs WHERE txId = :id")
    LiveData<CaravanTxEntity> load(String id);

    @Query("SELECT * FROM txs WHERE txId = :id")
    CaravanTxEntity loadSync(String id);

    @Query("SELECT * FROM txs WHERE belongTo =:walletFingerprint")
    LiveData<List<CaravanTxEntity>> loadMultisigTxs(String walletFingerprint);

    @Query("DELETE FROM txs WHERE belongTo =:walletFingerPrint ")
    void deleteTxs(String walletFingerPrint);
}
