package com.keystone.cold.db.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "casa_signatures", indices = {@Index("id")})
public class CasaSignature {
    @PrimaryKey(autoGenerate = true)
    public long id;
    private String signedHex;
    private String signStatus;
    private String amount;
    private String from;
    private String to;
    private String fee;
    private String memo;

    public CasaSignature(String signedHex, String signStatus, String amount, String from, String to, String fee, String memo) {
        this.signedHex = signedHex;
        this.signStatus = signStatus;
        this.amount = amount;
        this.from = from;
        this.to = to;
        this.fee = fee;
        this.memo = memo;
    }

    public CasaSignature() {
    }

    public long getId() {
        return id;
    }

    public String getSignedHex() {
        return signedHex;
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
}
