package com.keystone.cold.viewmodel.multisigs;

import static com.keystone.coinlib.Util.getExpubFingerprint;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.keystone.coinlib.ExtendPubkeyFormat;
import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.ExtendedPublicKeyVersion;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.viewmodel.exceptions.InvalidMultisigPathException;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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

    @Override
    @NonNull
    protected List<String> getXpubs(Account account, JSONArray xpubsInfo) throws XfpNotMatchException, InvalidMultisigPathException {
        boolean xfpMatch = false;
        List<String> xpubs = new ArrayList<>();
        for (int i = 0; i < xpubsInfo.length(); i++) {
            JSONObject obj;
            try {
                obj = xpubsInfo.getJSONObject(i);
                String xfp = obj.getString("xfp");
                String xpub = ExtendedPublicKeyVersion.convertXPubVersion(obj.getString("xpub"), account.getXPubVersion());
                String path = obj.optString("path");
                if (!path.isEmpty()) {
                    String[] strings = path.split("/");
                    if (strings.length == 1) {
                        throw new InvalidMultisigPathException("bip32Path not exist");
                    } else if (strings.length > 9) {
                        throw new InvalidMultisigPathException("maximum support depth of 8 layers");
                    }
                    String xPub = new GetExtendedPublicKeyCallable(path).call();
                    if ((xfp.equalsIgnoreCase(getXfp()) || xfp.equalsIgnoreCase(getExpubFingerprint(xPub)))
                            && ExtendPubkeyFormat.isEqualIgnorePrefix(xPub, xpub)) {
                        xfpMatch = true;
                    }
                } else {
                    if ((xfp.equalsIgnoreCase(getXfp()) || xfp.equalsIgnoreCase(getExpubFingerprint(getXPub(account))))
                            && ExtendPubkeyFormat.isEqualIgnorePrefix(getXPub(account), xpub)) {
                        xfpMatch = true;
                    }
                }
                xpubs.add(xpub);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        if (!xfpMatch) {
            throw new XfpNotMatchException("xfp not match");
        }
        return xpubs;
    }

    public String getXPub(Account account) {
        String xPub = new GetExtendedPublicKeyCallable(account.getPath()).call();
        xPub = ExtendedPublicKeyVersion.convertXPubVersion(xPub,
                Utilities.isMainNet(getApplication()) ? ExtendedPublicKeyVersion.xpub : ExtendedPublicKeyVersion.tpub);
        return xPub;
    }
}
