/*
 * Copyright (c) 2021 Keystone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * in the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.keystone.cold.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.keystone.coinlib.ExtendPubkeyFormat;
import com.keystone.coinlib.coins.BTC.Btc;
import com.keystone.coinlib.coins.BTC.Deriver;
import com.keystone.coinlib.utils.Account;
import com.keystone.coinlib.utils.B58;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.BuildConfig;
import com.keystone.cold.DataRepository;
import com.keystone.cold.MainApplication;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.callables.GetMasterFingerprintCallable;
import com.keystone.cold.databinding.CommonModalBinding;
import com.keystone.cold.db.entity.AccountEntity;
import com.keystone.cold.db.entity.CoinEntity;
import com.keystone.cold.ui.modal.ExportToSdcardDialog;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.FileUtils;
import com.keystone.cold.update.utils.Storage;
import com.sparrowwallet.hummingbird.registry.CryptoAccount;
import com.sparrowwallet.hummingbird.registry.CryptoCoinInfo;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;
import com.sparrowwallet.hummingbird.registry.CryptoKeypath;
import com.sparrowwallet.hummingbird.registry.CryptoOutput;
import com.sparrowwallet.hummingbird.registry.PathComponent;
import com.sparrowwallet.hummingbird.registry.ScriptExpression;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.keystone.coinlib.ExtendPubkeyFormat.convertExtendPubkey;
import static com.keystone.cold.ui.fragment.setting.MainPreferenceFragment.SETTING_ADDRESS_FORMAT;

public class GlobalViewModel extends AndroidViewModel {

    private static final int DEFAULT_CHANGE_ADDRESS_NUM = 100;

    private final DataRepository mRepo;
    private final MutableLiveData<String> exPub = new MutableLiveData<>();
    private final MutableLiveData<List<String>> changeAddress = new MutableLiveData<>();
    private String xpub;
    private AccountEntity accountEntity;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (sp, key) -> {
        if (SETTING_ADDRESS_FORMAT.equals(key)) {
            deriveChangeAddress();
        }
    };

    public GlobalViewModel(@NonNull Application application) {
        super(application);
        mRepo = MainApplication.getApplication().getRepository();
        deriveChangeAddress();
        Utilities.getPrefs(application).registerOnSharedPreferenceChangeListener(listener);
    }

    public static Account getAccount(Context context) {
        boolean isMainNet = Utilities.isMainNet(context);
        SharedPreferences pref = Utilities.getPrefs(context);
        String type = pref.getString(SETTING_ADDRESS_FORMAT, Account.P2SH_P2WPKH.getType());
        for (Account account : Account.values()) {
            if (type.equals(account.getType()) && isMainNet == account.isMainNet()) {
                return account;
            }
        }
        return Account.P2SH_P2WPKH;
    }

    public static Btc.AddressType getAddressType(Context context) {
        switch (getAccount(context)) {
            case P2SH_P2WPKH:
            case P2SH_P2WPKH_TESTNET:
                return Btc.AddressType.P2SH_P2WPKH;
            case P2PKH:
            case P2PKH_TESTNET:
                return Btc.AddressType.P2PKH;
            case P2WPKH:
            case P2WPKH_TESTNET:
                return Btc.AddressType.P2WPKH;
        }
        return Btc.AddressType.P2WPKH;
    }


    public static String getAddressFormat(Context context) {
        switch (getAccount(context)) {
            case P2WPKH:
            case P2WPKH_TESTNET:
                return context.getString(R.string.native_segwit);
            case P2PKH:
            case P2PKH_TESTNET:
                return context.getString(R.string.p2pkh);
            case P2SH_P2WPKH:
            case P2SH_P2WPKH_TESTNET:
                return context.getString(R.string.nested_segwit);
        }
        return context.getString(R.string.nested_segwit);
    }

    public static JSONObject getXpubInfo(Context activity) {
        JSONObject xpubInfo = new JSONObject();
        Account account = getAccount(activity);
        String xpub = new GetExtendedPublicKeyCallable(account.getPath()).call();
        String masterKeyFingerprint = new GetMasterFingerprintCallable().call();
        try {
            xpubInfo.put("ExtPubKey", xpub);
            xpubInfo.put("MasterFingerprint", masterKeyFingerprint);
            xpubInfo.put("AccountKeyPath", account.getPath().substring(2));
            xpubInfo.put("KeystoneVaultFirmwareVersion", BuildConfig.VERSION_NAME);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return xpubInfo;
    }

    public static String getOutputDescriptionString(Context activity) {
        String masterKeyFingerprint = new GetMasterFingerprintCallable().call();
        Account account = getAccount(activity);
        String xpub = new GetExtendedPublicKeyCallable(account.getPath()).call();
        String path = account.getPath().substring(1);
        String convertedXPub = convertExtendPubkey(xpub, account.getXpubFormat());
        switch (account) {
            case P2PKH:
            case P2PKH_TESTNET:
                return String.format("pkh([%s%s]%s/0/*)", masterKeyFingerprint, path, convertedXPub);
            case P2SH_P2WPKH:
            case P2SH_P2WPKH_TESTNET:
                return String.format("sh(wpkh([%s%s]%s/0/*))", masterKeyFingerprint, path, convertedXPub);
            case P2WPKH:
            case P2WPKH_TESTNET:
                return String.format("wpkh([%s%s]%s/0/*)", masterKeyFingerprint, path, convertedXPub);
        }
        return "";
    }

    public static CryptoAccount generateCryptoAccount(Context activity) {
        String masterKeyFingerprint = new GetMasterFingerprintCallable().call();
        CryptoAccount cryptoAccount = new CryptoAccount(Hex.decode(masterKeyFingerprint), Arrays.asList(generateOutputDescription(activity)));
        return cryptoAccount;
    }

    public static CryptoOutput generateOutputDescription(Context activity) {
        String masterKeyFingerprint = new GetMasterFingerprintCallable().call();
        Account account = getAccount(activity);
        String xpub = new GetExtendedPublicKeyCallable(account.getPath()).call();
        byte[] xpubBytes = new B58().decodeAndCheck(xpub);
        byte[] parentFp = Arrays.copyOfRange(xpubBytes, 5, 9);
        byte[] key = Arrays.copyOfRange(xpubBytes, 45, 78);
        byte[] chainCode = Arrays.copyOfRange(xpubBytes, 13, 45);
        int depth = xpubBytes[4];
        List<ScriptExpression> scriptExpressions = new ArrayList<>();
        int network = 0;
        int purpose = 0;
        switch (account) {
            case P2PKH:
                scriptExpressions.add(ScriptExpression.PUBLIC_KEY_HASH);
                network = 0;
                purpose = 44;
                break;
            case P2PKH_TESTNET:
                scriptExpressions.add(ScriptExpression.PUBLIC_KEY_HASH);
                network = 1;
                purpose = 44;
                break;
            case P2SH_P2WPKH:
                scriptExpressions.add(ScriptExpression.SCRIPT_HASH);
                scriptExpressions.add(ScriptExpression.WITNESS_PUBLIC_KEY_HASH);
                network = 0;
                purpose = 49;
                break;
            case P2SH_P2WPKH_TESTNET:
                scriptExpressions.add(ScriptExpression.SCRIPT_HASH);
                scriptExpressions.add(ScriptExpression.WITNESS_PUBLIC_KEY_HASH);
                network = 1;
                purpose = 49;
                break;
            case P2WPKH:
                scriptExpressions.add(ScriptExpression.WITNESS_PUBLIC_KEY_HASH);
                network = 0;
                purpose = 84;
                break;
            case P2WPKH_TESTNET:
                scriptExpressions.add(ScriptExpression.WITNESS_PUBLIC_KEY_HASH);
                network = 1;
                purpose = 84;
                break;
        }

        CryptoCoinInfo coinInfo = new CryptoCoinInfo(1, network);

        CryptoKeypath origin = new CryptoKeypath(Arrays.asList(
                new PathComponent(purpose, true),
                new PathComponent(network, true),
                new PathComponent(0, true)), Hex.decode(masterKeyFingerprint), depth);

        CryptoKeypath children = new CryptoKeypath(Arrays.asList(
                new PathComponent(0, false),
                new PathComponent(false)), null);

        CryptoHDKey hdKey = new CryptoHDKey(false, key, chainCode, coinInfo, origin, children, parentFp);
        return new CryptoOutput(scriptExpressions, hdKey);
    }

    public static boolean hasSdcard() {
        Storage storage = Storage.createByEnvironment();
        return storage != null && storage.getExternalDir() != null;
    }

    public static boolean writeToSdcard(Storage storage, String content, String fileName) {
        return writeToSdcard(storage, content.getBytes(), fileName);
    }

    public static boolean writeToSdcard(Storage storage, byte[] content, String fileName) {
        File file = new File(storage.getExternalDir(), fileName);
        return FileUtils.writeBytes(file, content);
    }

    public static void showNoSdcardModal(AppCompatActivity activity) {
        ModalDialog modalDialog = ModalDialog.newInstance();
        CommonModalBinding binding = DataBindingUtil.inflate(
                LayoutInflater.from(activity), R.layout.common_modal,
                null, false);
        binding.title.setText(R.string.hint);
        binding.subTitle.setText(R.string.insert_sdcard_hint);
        binding.close.setVisibility(View.GONE);
        binding.confirm.setText(R.string.know);
        binding.confirm.setOnClickListener(vv -> modalDialog.dismiss());
        modalDialog.setBinding(binding);
        modalDialog.show(activity.getSupportFragmentManager(), "");
    }

    public static void showExportResult(AppCompatActivity activity, Runnable runnable, boolean success) {
        showExportResult(activity, runnable, success, "");
    }

    public static void showExportResult(AppCompatActivity activity, Runnable runnable, boolean success, String filename) {
        ExportToSdcardDialog dialog = ExportToSdcardDialog.newInstance(filename, success);
        dialog.show(activity.getSupportFragmentManager(), "");
        new Handler().postDelayed(() -> {
            dialog.dismiss();
            if (runnable != null) {
                runnable.run();
            }
        }, 1000);
    }

    public static void exportSuccess(AppCompatActivity activity, Runnable runnable) {
        ExportToSdcardDialog dialog = ExportToSdcardDialog.newInstance("");
        dialog.show(activity.getSupportFragmentManager(), "");
        new Handler().postDelayed(() -> {
            dialog.dismiss();
            if (runnable != null) {
                runnable.run();
            }
        }, 1000);
    }

    private void deriveChangeAddress() {
        AppExecutors.getInstance().networkIO().execute(() -> {
            ExpubInfo expubInfo = new ExpubInfo().getExPubInfo();
            xpub = expubInfo.expub;
            String path = expubInfo.hdPath;
            List<String> changes = new ArrayList<>();
            Btc.AddressType type;
            if (Account.P2SH_P2WPKH.getPath().equals(path)
                    || Account.P2SH_P2WPKH_TESTNET.getPath().equals(path)) {
                type = Btc.AddressType.P2SH_P2WPKH;
            } else if (Account.P2WPKH.getPath().equals(path)
                    || Account.P2WPKH_TESTNET.getPath().equals(path)) {
                type = Btc.AddressType.P2WPKH;
            } else {
                type = Btc.AddressType.P2PKH;
            }

            Deriver btcDeriver = new Deriver(Utilities.isMainNet(getApplication()));
            for (int i = 0; i < DEFAULT_CHANGE_ADDRESS_NUM; i++) {
                changes.add(btcDeriver.derive(xpub, 1, i, type));
            }
            changeAddress.postValue(changes);
        });
    }

    public LiveData<String> getExtendPublicKey() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ExpubInfo expubInfo = new ExpubInfo().getExPubInfo();
            String hdPath = expubInfo.getHdPath();
            String extPub = expubInfo.getExpub();
            Account account = Account.ofPath(hdPath);
            exPub.postValue(convertExtendPubkey(extPub,
                    ExtendPubkeyFormat.valueOf(account.getXpubPrefix())));
        });
        return exPub;
    }

    public AccountEntity getAccountEntity() {
        return accountEntity;
    }

    public LiveData<List<String>> getChangeAddress() {
        deriveChangeAddress();
        return changeAddress;
    }

    public String getXpub() {
        return xpub;
    }

    private class ExpubInfo {
        private String hdPath;
        private String expub;

        public String getHdPath() {
            return hdPath;
        }

        public String getExpub() {
            return expub;
        }

        public ExpubInfo getExPubInfo() {
            CoinEntity coinEntity = mRepo.loadCoinSync(Utilities.currentCoin(getApplication()).coinId());
            SharedPreferences sp = Utilities.getPrefs(getApplication());
            List<AccountEntity> accounts = mRepo.loadAccountsForCoin(coinEntity);
            String format = sp.getString(SETTING_ADDRESS_FORMAT, Account.P2SH_P2WPKH.getType());

            Account account;
            boolean isMainNet = Utilities.isMainNet(getApplication());
            if (Account.P2SH_P2WPKH.getType().equals(format)) {
                account = isMainNet ? Account.P2SH_P2WPKH : Account.P2SH_P2WPKH_TESTNET;
            } else if (Account.P2WPKH.getType().equals(format)) {
                account = isMainNet ? Account.P2WPKH : Account.P2WPKH_TESTNET;
            } else {
                account = isMainNet ? Account.P2PKH : Account.P2PKH_TESTNET;
            }
            for (AccountEntity entity : accounts) {
                if (entity.getHdPath().equals(account.getPath())) {
                    accountEntity = entity;
                    hdPath = entity.getHdPath();
                    expub = entity.getExPub();
                }
            }
            return this;
        }
    }
}
