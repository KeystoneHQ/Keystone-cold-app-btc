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

package com.keystone.cold.db.entity;


import static androidx.room.ForeignKey.CASCADE;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(tableName = "caravan_multi_sig_address",
        foreignKeys = @ForeignKey(entity = CaravanMultiSigWalletEntity.class,
                parentColumns = "walletFingerPrint",
                childColumns = "walletFingerPrint", onDelete = CASCADE),
        indices = {@Index(value = "id",unique = true), @Index(value = "walletFingerPrint")})
public class CaravanMultiSigAddressEntity extends MultiSigAddressEntity{

    @Override
    public String toString() {
        return "CaravanMultiSigAddressEntity{" +
                "id=" + id +
                ", address='" + address + '\'' +
                ", index=" + index +
                ", walletFingerPrint=" + walletFingerPrint +
                ", path='" + path + '\'' +
                ", changeIndex=" + changeIndex +
                ", name='" + name + '\'' +
                '}';
    }
}
