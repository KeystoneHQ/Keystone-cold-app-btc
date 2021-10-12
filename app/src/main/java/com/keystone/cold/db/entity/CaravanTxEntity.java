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

package com.keystone.cold.db.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "caravan_txs", indices = {@Index("txId")})
public class CaravanTxEntity extends TxEntity {
    @NonNull
    @Override
    public String toString() {
        return "CaravanTxEntity{" +
                "txId='" + txId + '\'' +
                ", coinId='" + coinId + '\'' +
                ", coinCode='" + coinCode + '\'' +
                ", amount='" + amount + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", fee='" + fee + '\'' +
                ", signedHex='" + signedHex + '\'' +
                ", timeStamp=" + timeStamp +
                ", memo='" + memo + '\'' +
                ", signId='" + signId + '\'' +
                ", belongTo='" + belongTo + '\'' +
                ", signStatus='" + signStatus + '\'' +
                '}';
    }
}
