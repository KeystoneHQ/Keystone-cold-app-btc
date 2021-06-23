package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;

import androidx.annotation.NonNull;

import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.util.URRegistryHelper;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;

import org.spongycastle.util.encoders.Hex;

public class CasaMultiSigViewModel extends ViewModelBase {

    public CasaMultiSigViewModel(@NonNull Application application) {
        super(application);
    }

    public CryptoHDKey exportCryptoHDKey() {
        return URRegistryHelper.generateCryptoHDKey(MultiSig.CASA, Hex.decode(getXfp()), getXPub(MultiSig.CASA));
    }
}
