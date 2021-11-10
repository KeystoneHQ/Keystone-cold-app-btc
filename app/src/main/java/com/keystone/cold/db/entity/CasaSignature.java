package com.keystone.cold.db.entity;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.keystone.cold.model.Tx;

import java.util.Objects;

@Entity(tableName = "casa_signature", indices = {@Index(value = "id", unique = true)})
public class CasaSignature implements Tx, FilterableItem {
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
    private String belongTo;
    private String addition;

    public void setAddition(String json) {
        this.addition = json;
    }

    public String getAddition() {
        return addition;
    }

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
        return belongTo;
    }

    public void setBelongTo(String belongTo) {
        this.belongTo = belongTo;
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
                ", belongTo='" + belongTo + '\'' +
                '}';
    }

    @Override
    public boolean filter(String s) {
        if (TextUtils.isEmpty(s)) {
            return true;
        }
        s = s.toLowerCase();
        return from.toLowerCase().contains(s)
                || to.toLowerCase().contains(s)
                || txId.toLowerCase().contains(s)
                || memo.toLowerCase().contains(s);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CasaSignature casaSignature = (CasaSignature) o;
        return Objects.equals(from, casaSignature.from) &&
                Objects.equals(to, casaSignature.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to);
    }
}
