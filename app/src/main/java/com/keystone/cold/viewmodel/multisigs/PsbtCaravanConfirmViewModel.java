package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.keystone.coinlib.ExtendPubkeyFormat;
import com.keystone.coinlib.Util;
import com.keystone.coinlib.coins.AbsTx;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.encryption.ChipSigner;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.stream.Stream;

public class PsbtCaravanConfirmViewModel extends PsbtLegacyConfirmViewModel{
    private static final String TAG = "PsbtCaravanConfirmViewModel";
    public PsbtCaravanConfirmViewModel(@NonNull Application application) {
        super(application);
        mode = "caravan";
    }

    @Override
    protected Signer[] initSigners() {
        String[] paths = transaction.getHdPath().split(AbsTx.SEPARATOR);
        String[] distinctPaths = Stream.of(paths).distinct().toArray(String[]::new);
        Signer[] signer = new Signer[distinctPaths.length];

        String authToken = getAuthToken();
        if (TextUtils.isEmpty(authToken)) {
            Log.w(TAG, "authToken null");
            return null;
        }
        for (int i = 0; i < distinctPaths.length; i++) {
            String expubPath = getExPubPath(distinctPaths[i]);
            String path = distinctPaths[i].replace(expubPath + "/", "");
            String[] index = path.split("/");
            if (index.length != 2) {
                return null;
            }
            String expub = new GetExtendedPublicKeyCallable(expubPath).call();
            String pubKey = Util.getPublicKeyHex(
                    ExtendPubkeyFormat.convertExtendPubkey(expub, ExtendPubkeyFormat.xpub),
                    Integer.parseInt(index[0]), Integer.parseInt(index[1]));
            signer[i] = new ChipSigner(distinctPaths[i].toLowerCase(), authToken, pubKey);
        }
        return signer;
    }

    @Override
    protected String getExPubPath(String distinctPath) {
        String expubPath = wallet.getExPubPath();
        try {
            JSONArray jsonArray = new JSONArray(wallet.getExPubs());
            for (int i = 0; i < jsonArray.length(); i++) {
                String path = jsonArray.getJSONObject(i).optString("path");
                if (path.isEmpty()) break;
                if (distinctPath.startsWith(path)) {
                    expubPath = path;
                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return expubPath;
    }
}
