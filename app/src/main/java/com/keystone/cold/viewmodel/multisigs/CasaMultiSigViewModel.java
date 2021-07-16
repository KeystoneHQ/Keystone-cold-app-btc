package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.cold.DataRepository;
import com.keystone.cold.MainApplication;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.util.URRegistryHelper;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;

import org.spongycastle.util.encoders.Hex;

import java.util.List;

public class CasaMultiSigViewModel extends ViewModelBase {
    private final DataRepository repo;

    public CasaMultiSigViewModel(@NonNull Application application) {
        super(application);
        repo = ((MainApplication) application).getRepository();
    }

    public LiveData<List<CasaSignature>> allCasaSignatures() {
        return repo.loadCasaSignatures();
    }

    public CryptoHDKey exportCryptoHDKey() {
        return URRegistryHelper.generateCryptoHDKey(MultiSig.CASA, Hex.decode(getXfp()), getXPub(MultiSig.CASA));
    }
}
