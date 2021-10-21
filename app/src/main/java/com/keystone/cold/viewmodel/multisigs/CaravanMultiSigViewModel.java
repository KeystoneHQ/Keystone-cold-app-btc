package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.ExtendedPublicKeyVersion;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.db.entity.MultiSigWalletEntity;

import org.json.JSONArray;

import java.util.List;

public class CaravanMultiSigViewModel extends LegacyMultiSigViewModel {

    public CaravanMultiSigViewModel(@NonNull Application application) {
        super(application);
        mode = "caravan";
    }

    @Override
    public void setDefaultMultisigWallet(String walletFingerprint) {
        Utilities.setDefaultCaravanMultisigWallet(getApplication(), getXfp(), walletFingerprint);
    }

    @Override
    protected String getDefaultMultisigWallet() {
        return Utilities.getDefaultCaravanMultisigWallet(getApplication(), getXfp());
    }

    @Override
    @NonNull
    protected MultiSigWalletEntity createMultiSigWalletEntity(int threshold, Account account, String name, JSONArray xpubsInfo, String creator, int total, List<String> xpubs) {
        String verifyCode = calculateWalletVerifyCode(threshold, xpubs, account.getPath());
        String walletFingerprint = verifyCode + getXfp();
        String walletName = !TextUtils.isEmpty(name) ? name : "KT_" + verifyCode + "_" + threshold + "-" + total;
        MultiSigWalletEntity wallet = new MultiSigWalletEntity(
                walletName,
                threshold,
                total,
                account.getPath(),
                xpubsInfo.toString(),
                getXfp(),
                Utilities.isMainNet(getApplication()) ? "main" : "testnet", verifyCode, creator, mode);
        wallet.setWalletFingerPrint(walletFingerprint + "_" + mode);
        return wallet;
    }

    public String getXPub(Account account) {
        String xPub = new GetExtendedPublicKeyCallable(account.getPath()).call();
        xPub = ExtendedPublicKeyVersion.convertXPubVersion(xPub,
                Utilities.isMainNet(getApplication()) ? ExtendedPublicKeyVersion.xpub : ExtendedPublicKeyVersion.tpub);
        return xPub;
    }
}
