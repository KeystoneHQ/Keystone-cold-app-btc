package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.keystone.coinlib.ExtendPubkeyFormat;
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
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.db.entity.MultiSigAddressEntity;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.ui.fragment.main.adapter.PsbtMultiSigTxAdapter;
import com.keystone.cold.viewmodel.ParsePsbtViewModel;
import com.keystone.cold.viewmodel.exceptions.NoMatchedMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.multisigs.exceptions.NotMyCasaKeyException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

public class PsbtLegacyPsbtConfirmViewModel extends ParsePsbtViewModel {
    private static final String TAG = "SigleTxConfirmViewModel";
    private MultiSigWalletEntity wallet;

    public PsbtLegacyPsbtConfirmViewModel(@NonNull Application application) {
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
                if (!isMultigisTx) {
                    parseTxException.postValue(
                            new InvalidTransactionException("", InvalidTransactionException.IS_NOTMULTISIG_TX));
                    return;
                }
                JSONObject adaptTx = new PsbtMultiSigTxAdapter(MultiSigMode.CASA, wallet,false).adapt(psbtTx);
                JSONObject signTx = parsePsbtTx(adaptTx);
                Log.i(TAG, "signTx = " + signTx.toString(4));
                transaction = AbsTx.newInstance(signTx);
                if (transaction == null) {
                    observableTx.postValue(null);
                    parseTxException.postValue(new InvalidTransactionException("invalid transaction"));
                    return;
                }
                String walletFingerprint = null;
                if (signTx.has("btcTx")) {
                    walletFingerprint = signTx.getJSONObject("btcTx").getString("wallet_fingerprint");
                } else if (signTx.has("xtnTx")) {
                    walletFingerprint = signTx.getJSONObject("xtnTx").getString("wallet_fingerprint");
                }
                if (transaction instanceof UtxoTx) {
                    if (!checkMultisigChangeAddress(transaction)) {
                        observableTx.postValue(null);
                        parseTxException.postValue(new InvalidTransactionException("invalid change address"));
                        return;
                    }
                }
                TxEntity tx = generateMultisigTxEntity(signTx, walletFingerprint);
                observableTx.postValue(tx);
                if (Coins.BTC.coinCode().equals(transaction.getCoinCode())
                        || Coins.XTN.coinCode().equals(transaction.getCoinCode())) {
                    feeAttackChecking(tx);
                }
            } catch (JSONException e) {
                e.printStackTrace();
                parseTxException.postValue(new InvalidTransactionException("adapt failed,invalid psbt data"));
            } catch (WatchWalletNotMatchException | NoMatchedMultisigWalletException | NotMyCasaKeyException e) {
                e.printStackTrace();
                parseTxException.postValue(e);
            }
        });
    }

    private boolean checkMultisigChangeAddress(AbsTx utxoTx) {
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
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    private TxEntity generateMultisigTxEntity(JSONObject object, String walletFingerprint) throws JSONException {
        wallet = mRepository.loadMultisigWallet(walletFingerprint);
        TxEntity tx = new TxEntity();
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(20);
        coinCode = Objects.requireNonNull(transaction).getCoinCode();
        tx.setSignId(object.getString("signId"));
        tx.setTimeStamp(object.optLong("timestamp"));
        tx.setCoinCode(coinCode);
        tx.setCoinId(Coins.coinIdFromCoinCode(coinCode));
        tx.setFrom(getMultiSigFromAddress());
        tx.setTo(getToAddress());
        tx.setAmount(nf.format(transaction.getAmount()) + " " + transaction.getUnit());
        tx.setFee(nf.format(transaction.getFee()) + " BTC");
        tx.setMemo(transaction.getMemo());
        tx.setBelongTo(wallet.getWalletFingerPrint());
        String signStatus = null;
        if (object.has("btcTx")) {
            signStatus = object.getJSONObject("btcTx").getString("signStatus");
        } else if (object.has("xtnTx")) {
            signStatus = object.getJSONObject("xtnTx").getString("signStatus");
        }
        tx.setSignStatus(signStatus);
        return tx;
    }

    private String getMultiSigFromAddress() {
        String[] paths = transaction.getHdPath().split(AbsTx.SEPARATOR);
        String[] externalPath = Stream.of(paths)
                .filter(this::isExternalMulisigPath)
                .toArray(String[]::new);
        ensureMultisigAddressExist(externalPath);

        try {
            if (transaction instanceof UtxoTx) {
                JSONArray inputsClone = new JSONArray();
                JSONArray inputs = ((UtxoTx) transaction).getInputs();

                for (int i = 0; i < inputs.length(); i++) {
                    JSONObject input = inputs.getJSONObject(i);
                    long value = input.getJSONObject("utxo").getLong("value");
                    String hdpath = input.getString("ownerKeyPath");
                    hdpath = hdpath.replace(wallet.getExPubPath() + "/", "");
                    String[] index = hdpath.split("/");
                    String from = wallet.deriveAddress(
                            new int[]{Integer.parseInt(index[0]), Integer.parseInt(index[1])},
                            Utilities.isMainNet(getApplication()));
                    inputsClone.put(new JSONObject().put("value", value)
                            .put("address", from));
                }

                return inputsClone.toString();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "";
    }

    private boolean isExternalMulisigPath(@NonNull String path) {
        String[] split = path.replace(wallet.getExPubPath() + "/", "").split("/");
        return split.length == 2 && split[0].equals("0");
    }

    private void ensureMultisigAddressExist(String[] paths) {
        if (paths == null || paths.length == 0) {
            return;
        }
        String maxIndexHdPath = paths[0];
        int max = getAddressIndex(maxIndexHdPath);
        if (paths.length > 1) {
            max = getAddressIndex(paths[0]);
            for (String path : paths) {
                if (getAddressIndex(path) > max) {
                    max = getAddressIndex(path);
                    maxIndexHdPath = path;
                }
            }
        }

        MultiSigAddressEntity entity = mRepository.loadAllMultiSigAddress(wallet.getWalletFingerPrint(), maxIndexHdPath);
        if (entity == null) {
            List<MultiSigAddressEntity> address = mRepository.loadAllMultiSigAddressSync(wallet.getWalletFingerPrint());
            Optional<MultiSigAddressEntity> optional = address.stream()
                    .filter(addressEntity -> addressEntity.getPath()
                            .startsWith(wallet.getExPubPath() + "/" + 0))
                    .max((o1, o2) -> o1.getIndex() - o2.getIndex());
            int index = optional.map(MultiSigAddressEntity::getIndex).orElse(-1);
            if (index < max) {
                final CountDownLatch mLatch = new CountDownLatch(1);
                addingAddress.postValue(true);
                new LegacyMultiSigViewModel.AddAddressTask(wallet.getWalletFingerPrint(),
                        mRepository, mLatch::countDown, 0).execute(max - index);
                try {
                    mLatch.await();
                    addingAddress.postValue(false);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
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
                    updateTxSignStatus(tx);
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

                private void updateTxSignStatus(TxEntity tx) {
                    String signStatus = tx.getSignStatus();
                    String[] splits = signStatus.split("-");
                    int sigNumber = Integer.parseInt(splits[0]);
                    int reqSigNumber = Integer.parseInt(splits[1]);
                    int keyNumber = Integer.parseInt(splits[2]);
                    tx.setSignStatus((sigNumber + 1) + "-" + reqSigNumber + "-" + keyNumber);
                }
            };
            callback.startSign();
            Signer[] signer = initSigners();
            Btc btc = new Btc(new BtcImpl(Utilities.isMainNet(getApplication())));
            btc.signPsbt(psbt, callback, false, signer);
        });
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
            if (index.length != 2) return null;
            String expub = new GetExtendedPublicKeyCallable(wallet.getExPubPath()).call();
            String pubKey = Util.getPublicKeyHex(
                    ExtendPubkeyFormat.convertExtendPubkey(expub, ExtendPubkeyFormat.xpub),
                    Integer.parseInt(index[0]), Integer.parseInt(index[1]));
            signer[i] = new ChipSigner(distinctPaths[i].toLowerCase(), authToken, pubKey);
        }
        return signer;
    }
}
