package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.keystone.coinlib.ExtendPubkeyFormat;
import com.keystone.coinlib.Util;
import com.keystone.coinlib.coins.AbsTx;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.viewmodel.exceptions.InvalidMultisigPathException;

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
            String path = distinctPaths[i].replace(wallet.getExPubPath() + "/", "");
            String[] index = path.split("/");
            if (index.length != 2) {
                parseTxException.postValue(new InvalidMultisigPathException("maximum support depth of 11 layers"));
                return null;
            }
            String expub = new GetExtendedPublicKeyCallable(wallet.getExPubPath()).call();
            String pubKey = Util.getPublicKeyHex(
                    ExtendPubkeyFormat.convertExtendPubkey(expub, ExtendPubkeyFormat.xpub),
                    Integer.parseInt(index[0]), Integer.parseInt(index[1]));
            signer[i] = new ChipSigner(distinctPaths[i].toLowerCase(), authToken, pubKey);
        }
        return signer;
    }
}
