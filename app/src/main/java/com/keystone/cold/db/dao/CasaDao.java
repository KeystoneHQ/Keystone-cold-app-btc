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

import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.db.entity.TxEntity;

import java.util.List;

@Dao
public interface CasaDao {
    @Query("SELECT * FROM casa_signatures ORDER BY id DESC")
    LiveData<List<CasaSignature>> loadSignatures();

    @Query("SELECT * FROM casa_signatures")
    List<CasaSignature> loadTxsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(CasaSignature tx);

    @Query("SELECT * FROM casa_signatures WHERE id = :id")
    LiveData<CasaSignature> load(long id);

    @Query("SELECT * FROM casa_signatures WHERE id = :id")
    CasaSignature loadSync(int id);
}
