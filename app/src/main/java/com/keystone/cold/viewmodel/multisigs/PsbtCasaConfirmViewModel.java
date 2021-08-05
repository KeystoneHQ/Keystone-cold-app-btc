package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.keystone.coinlib.Util;
import com.keystone.coinlib.coins.AbsTx;
import com.keystone.coinlib.coins.BTC.Btc;
import com.keystone.coinlib.coins.BTC.BtcImpl;
import com.keystone.coinlib.coins.BTC.UtxoTx;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.interfaces.SignPsbtCallback;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.ClearTokenCallable;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.ui.fragment.main.adapter.PsbtMultiSigTxAdapter;
import com.keystone.cold.viewmodel.ParsePsbtViewModel;
import com.keystone.cold.viewmodel.exceptions.NoMatchedMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.multisigs.exceptions.NotMyCasaKeyException;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PsbtCasaConfirmViewModel extends ParsePsbtViewModel {
    private static final String TAG = "SigleTxConfirmViewModel";

    private final MutableLiveData<CasaSignature> observableCasaSignature = new MutableLiveData<>();
    private boolean isCasaMainnet;
    protected MultiSigWalletEntity wallet;

    public PsbtCasaConfirmViewModel(@NonNull Application application) {
        super(application);
        observableCasaSignature.setValue(null);
        isCasaMainnet = Utilities.isMainNet(getApplication());
    }

    public MutableLiveData<CasaSignature> getObservableCasaSignature() {
        return observableCasaSignature;
    }

    public boolean isCasaMainnet() {
        return isCasaMainnet;
    }

    @Override
    public void parseTxData(Bundle bundle) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                String psbtBase64 = bundle.getString("psbt_base64");
                Btc btc = new Btc(new BtcImpl(isCasaMainnet));
                JSONObject psbtTx = btc.parsePsbt(psbtBase64);
                if (psbtTx == null) {
                    parseTxException.postValue(new InvalidTransactionException("parse failed,invalid psbt data"));
                    return;
                }
                boolean isMultigisTx = psbtTx.getJSONArray("inputs").getJSONObject(0).getBoolean("isMultiSign");
                if (!isMultigisTx) {
                    parseTxException.postValue(
                            new InvalidTransactionException("", InvalidTransactionException.IS_NOTMULTISIG_TX));
                    return;
                }
                PsbtMultiSigTxAdapter psbtMultiSigTxAdapter = new PsbtMultiSigTxAdapter(MultiSigMode.CASA, wallet);
                JSONObject adaptTx = psbtMultiSigTxAdapter.adapt(psbtTx);
                isCasaMainnet = psbtMultiSigTxAdapter.isCasaMainnet();
                JSONObject signTx = parsePsbtTx(adaptTx);
                Log.i(TAG, "signTx = " + signTx.toString(4));
                transaction = AbsTx.newInstance(signTx);
                if (transaction == null) {
                    observableCasaSignature.postValue(null);
                    parseTxException.postValue(new InvalidTransactionException("invalid transaction"));
                    return;
                }
                if (transaction instanceof UtxoTx) {
                    if (!checkMultisigChangeAddress(transaction)) {
                        observableCasaSignature.postValue(null);
                        parseTxException.postValue(new InvalidTransactionException("invalid change address"));
                        return;
                    }
                }
                CasaSignature sig = generateCasaSignature(signTx);
                observableCasaSignature.postValue(sig);
            } catch (JSONException e) {
                e.printStackTrace();
                parseTxException.postValue(new InvalidTransactionException("adapt failed,invalid psbt data"));
            } catch (WatchWalletNotMatchException | NoMatchedMultisigWalletException | NotMyCasaKeyException e) {
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
                    CasaSignature casaSignature = observableCasaSignature.getValue();
                    Objects.requireNonNull(casaSignature);
                    if (TextUtils.isEmpty(txId)) {
                        txId = "unknown_txid_" + Math.abs(casaSignature.hashCode());
                    }
                    updateCasaSignatureStatus(casaSignature);
                    casaSignature.setTxId(txId);
                    casaSignature.setSignedHex(psbtB64);
                    Long id = mRepository.insertCasaSignature(casaSignature);
                    casaSignature.setId(id);
                    signState.postValue(STATE_SIGN_SUCCESS);
                    new ClearTokenCallable().call();
                }

                @Override
                public void postProgress(int progress) {

                }

                private void updateCasaSignatureStatus(CasaSignature casaSignature) {
                    String signStatus = casaSignature.getSignStatus();
                    String[] splits = signStatus.split("-");
                    int sigNumber = Integer.parseInt(splits[0]);
                    int reqSigNumber = Integer.parseInt(splits[1]);
                    int keyNumber = Integer.parseInt(splits[2]);
                    casaSignature.setSignStatus((sigNumber + 1) + "-" + reqSigNumber + "-" + keyNumber);
                }
            };
            callback.startSign();
            Signer[] signer = initSigners();
            Btc btc = new Btc(new BtcImpl(Utilities.isMainNet(getApplication())));
            btc.signPsbt(psbt, callback, false, signer);
        });
    }

    @Override
    public Signer[] initSigners() {
        String[] paths = transaction.getHdPath().split(AbsTx.SEPARATOR);
        String[] distinctPaths = Stream.of(paths).distinct().toArray(String[]::new);
        Signer[] signer = new Signer[distinctPaths.length];

        String authToken = getAuthToken();
        if (TextUtils.isEmpty(authToken)) {
            Log.w(TAG, "authToken null");
            return null;
        }
        for (int i = 0; i < distinctPaths.length; i++) {
            int point = Math.max(distinctPaths[i].lastIndexOf("'"), 0);
            String XPubPath = distinctPaths[i].substring(0, point + 1);
            String path = distinctPaths[i].replace(XPubPath + "/", "");
            String[] index = path.split("/");
            String expub = new GetExtendedPublicKeyCallable(XPubPath).call();
            String pubKey = Util.deriveFromKey(
                    expub, index);
            signer[i] = new ChipSigner(distinctPaths[i].toLowerCase(), authToken, pubKey);
        }
        return signer;
    }

    protected boolean checkMultisigChangeAddress(AbsTx utxoTx) {
        List<UtxoTx.ChangeAddressInfo> changeAddressInfo = ((UtxoTx) utxoTx).getChangeAddressInfo();
        if (changeAddressInfo == null || changeAddressInfo.isEmpty()) {
            return true;
        }

        try {
            String exPubPath = wallet.getExPubPath();
            for (UtxoTx.ChangeAddressInfo info : changeAddressInfo) {
                String path = info.hdPath;
                String address = info.address;
                if (!path.startsWith(exPubPath)) return false;
                path = path.replace(exPubPath + "/", "");

                String[] index = path.split("/");

                if (index.length != 2) return false;
                String expectedAddress = wallet.deriveAddress(
                        new int[]{Integer.parseInt(index[0]), Integer.parseInt(index[1])},
                        Utilities.isMainNet(getApplication()));

                if (!expectedAddress.equals(address)) {
                    return false;
                }
            }
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
        return true;
    }

    protected CasaSignature generateCasaSignature(JSONObject object) throws JSONException {
        CasaSignature sig = new CasaSignature();
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(20);
        sig.setFrom("");
        sig.setTo(getToAddress());
        sig.setAmount(nf.format(transaction.getAmount()) + " " + transaction.getUnit());
        sig.setFee(nf.format(transaction.getFee()) + " BTC");
        sig.setMemo(transaction.getMemo());
        String signStatus = null;
        if (object.has("btcTx")) {
            signStatus = object.getJSONObject("btcTx").getString("signStatus");
        } else if (object.has("xtnTx")) {
            signStatus = object.getJSONObject("xtnTx").getString("signStatus");
        }
        sig.setSignStatus(signStatus);
        return sig;
    }
}
