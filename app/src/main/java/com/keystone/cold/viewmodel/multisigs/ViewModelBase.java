package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.ExtendedPublicKeyVersion;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.callables.GetMasterFingerprintCallable;

import java.util.HashMap;
import java.util.Map;

class ViewModelBase extends AndroidViewModel {
    protected final Map<Account, String> xPubMap = new HashMap<>();
    private final String xfp;

    public ViewModelBase(@NonNull Application application) {
        super(application);
        xfp = new GetMasterFingerprintCallable().call();
    }

    public String getXfp() {
        return xfp;
    }

    public String getXPub(Account account) {
        if (!xPubMap.containsKey(account)) {
            String xPub = new GetExtendedPublicKeyCallable(account.getPath()).call();
            xPubMap.put(account, ExtendedPublicKeyVersion.convertXPubVersion(xPub, account.getXPubVersion()));
        }
        return xPubMap.get(account);
    }
}
