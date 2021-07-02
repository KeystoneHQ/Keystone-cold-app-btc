package com.keystone.cold.db.entity;

import androidx.annotation.NonNull;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.keystone.cold.model.Tx;

@Entity(tableName = "casa_signature", indices = {@Index(value = "id", unique = true)})
public class CasaSignature implements Tx {
    @PrimaryKey(autoGenerate = true)
    @NonNull
    public Long id;
    private String txId;
    private String signedHex;
    private String signStatus;
    private String amount;
    private String from;
    private String to;
    private String fee;
    private String memo;

    public String getTxId() {
        return txId;
    }

    @Override
    public String getCoinId() {
        return "";
    }

    @Override
    public String getCoinCode() {
        return "BTC";
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public CasaSignature(String signedHex, String signStatus, String amount, String from, String to, String fee, String memo) {
        this.signedHex = signedHex;
        this.signStatus = signStatus;
        this.amount = amount;
        this.from = from;
        this.to = to;
        this.fee = fee;
        this.memo = memo;
    }

    @Ignore
    public CasaSignature() {
    }

    @NonNull
    public Long getId() {
        return id;
    }

    public String getSignedHex() {
        return signedHex;
    }

    @Override
    public long getTimeStamp() {
        return 0;
    }

    public void setId(@NonNull Long id) {
        this.id = id;
    }

    public String getSignStatus() {
        return signStatus;
    }

    public void setSignedHex(String signedHex) {
        this.signedHex = signedHex;
    }

    public void setSignStatus(String signStatus) {
        this.signStatus = signStatus;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setFee(String fee) {
        this.fee = fee;
    }

    public String getAmount() {
        return amount;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getFee() {
        return fee;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getMemo() {
        return memo;
    }

    @Override
    public String getSignId() {
        return "";
    }

    @Override
    public String getBelongTo() {
        return "";
    }

    @Override
    public String toString() {
        return "CasaSignature{" +
                "id=" + id +
                ", txId='" + txId + '\'' +
                ", signedHex='" + signedHex + '\'' +
                ", signStatus='" + signStatus + '\'' +
                ", amount='" + amount + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", fee='" + fee + '\'' +
                ", memo='" + memo + '\'' +
                '}';
    }
}
