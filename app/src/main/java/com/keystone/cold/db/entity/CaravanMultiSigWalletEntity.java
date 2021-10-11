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


import androidx.room.Entity;
import androidx.room.Index;

@Entity(tableName = "caravan_multi_sig_wallet", indices = {@Index("walletFingerPrint")})
public class CaravanMultiSigWalletEntity extends MultiSigWalletEntity {
    public CaravanMultiSigWalletEntity(String walletName, int threshold, int total,
                                       String exPubPath, String exPubs, String belongTo,
                                       String network, String verifyCode, String creator) {
        super(walletName, threshold, total, exPubPath, exPubs, belongTo, network, verifyCode, creator);
    }

    @Override
    public String toString() {
        return "CaravanMultiSigWalletEntity{" +
                "WalletFingerPrint=" + walletFingerPrint +
                ", walletName='" + walletName + '\'' +
                ", threshold=" + threshold +
                ", total=" + total +
                ", exPubPath='" + exPubPath + '\'' +
                ", exPubs='" + exPubs + '\'' +
                ", belongTo='" + belongTo + '\'' +
                ", network='" + network + '\'' +
                ", creator='" + creator + '\'' +
                '}';
    }
}
