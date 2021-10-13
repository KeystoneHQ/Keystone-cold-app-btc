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

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "multi_sig_address",
        foreignKeys = @ForeignKey(entity = MultiSigWalletEntity.class,
                parentColumns = "walletFingerPrint",
                childColumns = "walletFingerPrint", onDelete = CASCADE),
        indices = {@Index(value = "id",unique = true), @Index(value = "walletFingerPrint")})
public class MultiSigAddressEntity {
    @PrimaryKey(autoGenerate = true)
    @NonNull
    public long id;
    @NonNull
    protected String address; // address
    @NonNull
    protected int index; // address index
    @NonNull
    protected String walletFingerPrint; // belong to which multisig wallet
    @NonNull
    protected String path; // address path
    @NonNull
    protected int changeIndex;
    protected String name;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getWalletFingerPrint() {
        return walletFingerPrint;
    }

    public void setWalletFingerPrint(String walletFingerPrint) {
        this.walletFingerPrint = walletFingerPrint;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getChangeIndex() {
        return changeIndex;
    }

    public void setChangeIndex(int changeIndex) {
        this.changeIndex = changeIndex;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CaravanMultiSigAddressEntity toCaravanAddress(){
        CaravanMultiSigAddressEntity caravanMultiSigAddressEntity = new CaravanMultiSigAddressEntity();
        caravanMultiSigAddressEntity.setAddress(address);
        caravanMultiSigAddressEntity.setChangeIndex(changeIndex);
        caravanMultiSigAddressEntity.setIndex(index);
        caravanMultiSigAddressEntity.setName(name);
        caravanMultiSigAddressEntity.setWalletFingerPrint(walletFingerPrint);
        caravanMultiSigAddressEntity.setPath(path);
        caravanMultiSigAddressEntity.setId(id);
        return caravanMultiSigAddressEntity;
    }

    @Override
    public String toString() {
        return "MultiSigAddressEntity{" +
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
