package com.keystone.cold.viewmodel;

import android.app.Application;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.keystone.coinlib.Util;
import com.keystone.coinlib.coins.AbsTx;
import com.keystone.coinlib.coins.BTC.Btc;
import com.keystone.coinlib.coins.BTC.BtcImpl;
import com.keystone.coinlib.coins.BTC.UtxoTx;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.interfaces.SignPsbtCallback;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.ClearTokenCallable;
import com.keystone.cold.db.entity.AccountEntity;
import com.keystone.cold.db.entity.CoinEntity;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.ui.fragment.main.adapter.PsbtTxAdapter;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.stream.Stream;

import static com.keystone.cold.viewmodel.WatchWallet.ELECTRUM;

public class PsbtSiglePsbtConfirmViewModel extends ParsePsbtViewModel {
    private static final String TAG = "SigleTxConfirmViewModel";

    public PsbtSiglePsbtConfirmViewModel(@NonNull Application application) {
        super(application);
    }

    public void parseTxData(Bundle bundle) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String psbtBase64 = bundle.getString("psbt_base64");
                Btc btc = new Btc(new BtcImpl(Utilities.isMainNet(getApplication())));
                JSONObject psbtTx = btc.parsePsbt(psbtBase64);
                if (psbtTx == null) {
                    parseTxException.postValue(new InvalidTransactionException("parse failed,invalid psbt data"));
                    return;
                }
                boolean isMultigisTx = psbtTx.getJSONArray("inputs").getJSONObject(0).getBoolean("isMultiSign");
                if (isMultigisTx) {
                    parseTxException.postValue(
                            new InvalidTransactionException("", InvalidTransactionException.IS_MULTISIG_TX));
                    return;
                }
                JSONObject adaptTx = new PsbtTxAdapter().adapt(psbtTx);
                if (adaptTx.getJSONArray("inputs").length() == 0) {
                    parseTxException.postValue(
                            new InvalidTransactionException("master xfp not match, or nothing can be sign"));
                    return;
                }
                JSONObject signTx = parsePsbtTx(adaptTx);
                Log.i(TAG, "signTx = " + signTx.toString(4));
                transaction = AbsTx.newInstance(signTx);
                if (transaction == null) {
                    observableTx.postValue(null);
                    parseTxException.postValue(new InvalidTransactionException("invalid transaction"));
                    return;
                }
                if (transaction instanceof UtxoTx) {
                    if (!checkChangeAddress(transaction)) {
                        observableTx.postValue(null);
                        parseTxException.postValue(new InvalidTransactionException("invalid change address"));
                        return;
                    }
                }
                TxEntity tx = generateTxEntity(signTx);
                observableTx.postValue(tx);
                if (Coins.BTC.coinCode().equals(transaction.getCoinCode())
                        || Coins.XTN.coinCode().equals(transaction.getCoinCode())) {
                    feeAttackChecking(tx);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                parseTxException.postValue(new InvalidTransactionException("adapt failed,invalid psbt data"));
            } catch (WatchWalletNotMatchException e) {
                e.printStackTrace();
                parseTxException.postValue(e);
            }
        });
    }

    @Override
    public void handleSignPsbt(String psbt) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            SignPsbtCallback callback = new SignPsbtCallback() {
                @Override
                public void startSign() {
                    signState.postValue(STATE_SIGNING);
                }

                @Override
                public void onFail() {
                    signState.postValue(STATE_SIGN_FAIL);
                    new ClearTokenCallable().call();
                }

                @Override
                public void onSuccess(String txId, String psbtB64) {
                    TxEntity tx = observableTx.getValue();
                    Objects.requireNonNull(tx);
                    if (TextUtils.isEmpty(txId)) {
                        txId = "unknown_txid_" + Math.abs(tx.hashCode());
                    }
                    tx.setTxId(txId);
                    tx.setSignedHex(psbtB64);
                    mRepository.insertTx(tx);
                    signState.postValue(STATE_SIGN_SUCCESS);
                    new ClearTokenCallable().call();
                }

                @Override
                public void postProgress(int progress) {

                }
            };
            callback.startSign();
            Signer[] signer = initSigners();
            Btc btc = new Btc(new BtcImpl(Utilities.isMainNet(getApplication())));
            if (WatchWallet.getWatchWallet(getApplication()) == ELECTRUM) {
                btc.signPsbt(psbt, callback, false, signer);
            } else {
                btc.signPsbt(psbt, callback, signer);
            }
        });
    }

    @Override
    protected Signer[] initSigners() {
        String[] paths = transaction.getHdPath().split(AbsTx.SEPARATOR);
        String coinCode = transaction.getCoinCode();
        String[] distinctPaths = Stream.of(paths).distinct().toArray(String[]::new);
        Signer[] signer = new Signer[distinctPaths.length];

        String authToken = getAuthToken();
        if (TextUtils.isEmpty(authToken)) {
            Log.w(TAG, "authToken null");
            return null;
        }
        CoinEntity coinEntity = mRepository.loadCoinEntityByCoinCode(coinCode);
        for (int i = 0; i < distinctPaths.length; i++) {
            String accountHdPath = getAccountHdPath(distinctPaths[i]);
            if (accountHdPath == null) {
                return null;
            }
            AccountEntity accountEntity = getAccountEntityByPath(accountHdPath, coinEntity);
            if (accountEntity == null) {
                return null;
            }
            String pubKey = Util.getPublicKeyHex(accountEntity.getExPub(), distinctPaths[i]);
            signer[i] = new ChipSigner(distinctPaths[i].toLowerCase(), authToken, pubKey);
        }
        return signer;
    }
}
