package com.keystone.cold.viewmodel;

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
import com.keystone.coinlib.coins.BTC.Deriver;
import com.keystone.coinlib.coins.BTC.UtxoTx;
import com.keystone.coinlib.exception.InvalidPathException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.exception.UnknownTransactionException;
import com.keystone.coinlib.interfaces.SignPsbtCallback;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.coinlib.path.AddressIndex;
import com.keystone.coinlib.path.CoinPath;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.ClearTokenCallable;
import com.keystone.cold.db.entity.AccountEntity;
import com.keystone.cold.db.entity.AddressEntity;
import com.keystone.cold.db.entity.CoinEntity;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.ui.fragment.main.adapter.PsbtSigleTxAdapter;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.DUPLICATE_TX;
import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.NORMAL;
import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.SAME_OUTPUTS;
import static com.keystone.cold.viewmodel.AddAddressViewModel.AddAddressTask.getAddressType;
import static com.keystone.cold.viewmodel.GlobalViewModel.getAccount;
import static com.keystone.cold.viewmodel.WatchWallet.ELECTRUM;

public class PsbtSigleConfirmViewModel extends ParsePsbtViewModel {
    private static final String TAG = "PsbtSigleConfirmViewModel";
    protected final MutableLiveData<TxEntity> observableTx = new MutableLiveData<>();

    public PsbtSigleConfirmViewModel(@NonNull Application application) {
        super(application);
        observableTx.setValue(null);
    }

    public void handleTx(Bundle bundle) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                initIsMainNet(null);
                JSONObject signTx = parseTxData(bundle);
                transaction = AbsTx.newInstance(signTx);
                checkTransaction();
                TxEntity tx = generateTxEntity(signTx);
                observableTx.postValue(tx);
            } catch (WatchWalletNotMatchException | InvalidTransactionException e) {
                e.printStackTrace();
                parseTxException.postValue(e);
            } catch (JSONException e) {
                e.printStackTrace();
                parseTxException.postValue(new InvalidTransactionException("invalid data"));
            } catch (Exception e) {
                e.printStackTrace();
                parseTxException.postValue(new UnknownTransactionException("unKnown transaction"));
            }
        });
    }

    @Override
    protected void initIsMainNet(Bundle bundle) {
        isMainNet = Utilities.isMainNet(getApplication());
    }

    @Override
    protected JSONObject parseTxData(Bundle bundle) throws Exception {
        String psbtBase64 = bundle.getString("psbt_base64");
        Btc btc = new Btc(new BtcImpl(isMainNet));
        JSONObject psbtTx = btc.parsePsbt(psbtBase64);
        if (psbtTx == null) {
            throw new InvalidTransactionException("parse failed,invalid psbt data");
        }
        boolean isMultisigTx = psbtTx.getJSONArray("inputs").getJSONObject(0).getBoolean("isMultiSign");
        if (isMultisigTx) {
            throw new InvalidTransactionException("", InvalidTransactionException.IS_MULTISIG_TX);
        }
        JSONObject adaptTx = new PsbtSigleTxAdapter().adapt(psbtTx);
        return parsePsbtTx(adaptTx);
    }

    @Override
    protected void checkTransaction() throws InvalidTransactionException {
        if (transaction == null) {
            throw new InvalidTransactionException("invalid transaction");
        }
        if (transaction instanceof UtxoTx) {
            checkChangeAddress(transaction);
        }
        if (Coins.BTC.coinCode().equals(transaction.getCoinCode())
                || Coins.XTN.coinCode().equals(transaction.getCoinCode())) {
            feeAttackChecking();
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
            Btc btc = new Btc(new BtcImpl(isMainNet));
            if (WatchWallet.getWatchWallet(getApplication()) == ELECTRUM) {
                btc.signPsbt(psbt, callback, false, signer);
            } else {
                btc.signPsbt(psbt, callback, signer);
            }
        });
    }

    private Signer[] initSigners() {
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

    protected void feeAttackChecking() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String inputs = getFromAddress();
            String outputs = getToAddress();
            List<TxEntity> txs = mRepository.loadAllTxSync(Utilities.currentCoin(getApplication()).coinId());
            for (TxEntity tx : txs) {
                if (inputs.equals(tx.getFrom()) && outputs.equals(tx.getTo())) {
                    feeAttachCheckingResult.postValue(DUPLICATE_TX);
                    break;
                } else if (outputs.equals(tx.getTo())) {
                    feeAttachCheckingResult.postValue(SAME_OUTPUTS);
                    break;
                } else {
                    feeAttachCheckingResult.postValue(NORMAL);
                }
            }
        });
    }

    private void checkChangeAddress(AbsTx utxoTx) throws InvalidTransactionException {
        List<UtxoTx.ChangeAddressInfo> changeAddressInfoList = ((UtxoTx) utxoTx).getChangeAddressInfo();
        if (changeAddressInfoList == null || changeAddressInfoList.isEmpty()) {
            return;
        }
        Deriver deriver = new Deriver(isMainNet);
        for (UtxoTx.ChangeAddressInfo changeAddressInfo : changeAddressInfoList) {
            String hdPath = changeAddressInfo.hdPath;
            String address = changeAddressInfo.address;
            String accountHdPath = getAccountHdPath(changeAddressInfo.hdPath);
            AccountEntity accountEntity = getAccountEntityByPath(accountHdPath,
                    mRepository.loadCoinEntityByCoinCode(utxoTx.getCoinCode()));
            if (accountEntity == null) {
                throw new InvalidTransactionException("invalid accountEntity");
            }
            String exPub = accountEntity.getExPub();
            try {
                AddressIndex addressIndex = CoinPath.parsePath(hdPath);
                int change = addressIndex.getParent().getValue();
                int index = addressIndex.getValue();
                String expectAddress = Objects.requireNonNull(deriver).derive(exPub, change,
                        index, getAddressType(accountEntity));
                if (!address.equals(expectAddress)) {
                    throw new InvalidTransactionException("change address is not equal");
                }
            } catch (InvalidPathException e) {
                e.printStackTrace();
                throw new InvalidTransactionException("InvalidPathException");
            }
        }
    }

    private String getAccountHdPath(String addressPath) {
        String accountHdPath;
        try {
            accountHdPath = CoinPath.parsePath(addressPath).getParent().getParent().toString();
        } catch (InvalidPathException e) {
            e.printStackTrace();
            return null;
        }
        return accountHdPath;
    }

    private TxEntity generateTxEntity(JSONObject object) throws JSONException {
        TxEntity tx = new TxEntity();
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(20);
        coinCode = Objects.requireNonNull(transaction).getCoinCode();
        tx.setSignId(object.getString("signId"));
        tx.setTimeStamp(object.optLong("timestamp"));
        tx.setCoinCode(coinCode);
        tx.setCoinId(Coins.coinIdFromCoinCode(coinCode));
        tx.setFrom(getFromAddress());
        tx.setTo(getToAddress());
        tx.setAmount(nf.format(transaction.getAmount()) + " " + transaction.getUnit());
        tx.setFee(nf.format(transaction.getFee()) + " BTC");
        tx.setMemo(transaction.getMemo());
        tx.setBelongTo(mRepository.getBelongTo());
        return tx;
    }

    private boolean isExternalPath(@NonNull String path) {
        try {
            return CoinPath.parsePath(path).getParent().isExternal();
        } catch (InvalidPathException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String getFromAddress() {
        if (!TextUtils.isEmpty(transaction.getFrom())) {
            return transaction.getFrom();
        }
        String[] paths = transaction.getHdPath().split(AbsTx.SEPARATOR);
        String[] externalPath = Stream.of(paths)
                .filter(this::isExternalPath)
                .toArray(String[]::new);
        ensureAddressExist(externalPath);

        try {
            if (transaction instanceof UtxoTx) {
                JSONArray inputsClone = new JSONArray();
                JSONArray inputs = ((UtxoTx) transaction).getInputs();

                CoinEntity coinEntity = mRepository.loadCoinSync(Coins.coinIdFromCoinCode(transaction.getCoinCode()));
                AccountEntity accountEntity =
                        mRepository.loadAccountsByPath(coinEntity.getId(), getAccount(getApplication()).getPath());

                for (int i = 0; i < inputs.length(); i++) {
                    JSONObject input = inputs.getJSONObject(i);
                    long value = input.getJSONObject("utxo").getLong("value");
                    String hdpath = input.getString("ownerKeyPath");
                    AddressIndex addressIndex = CoinPath.parsePath(hdpath);
                    int index = addressIndex.getValue();
                    int change = addressIndex.getParent().getValue();

                    String from = new Deriver(isMainNet).derive(accountEntity.getExPub()
                            , change, index, getAddressType(accountEntity));
                    inputsClone.put(new JSONObject().put("value", value)
                            .put("address", from));
                }

                return inputsClone.toString();
            }
        } catch (JSONException | InvalidPathException e) {
            e.printStackTrace();
        }

        return Stream.of(externalPath)
                .distinct()
                .map(path -> mRepository.loadAddressBypath(path).getAddressString())
                .reduce((s1, s2) -> s1 + AbsTx.SEPARATOR + s2)
                .orElse("");
    }

    private void ensureAddressExist(String[] paths) {
        if (paths == null || paths.length == 0) {
            return;
        }
        String maxIndexHdPath = paths[0];
        if (paths.length > 1) {
            int max = getAddressIndex(paths[0]);
            for (String path : paths) {
                if (getAddressIndex(path) > max) {
                    max = getAddressIndex(path);
                    maxIndexHdPath = path;
                }
            }
        }
        AddressEntity address = mRepository.loadAddressBypath(maxIndexHdPath);
        if (address == null) {
            addAddress(maxIndexHdPath);
        }
    }

    private void addAddress(String hdPath) {
        String accountHdPath;
        int pathIndex;
        try {
            AddressIndex index = CoinPath.parsePath(hdPath);
            pathIndex = index.getValue();
            accountHdPath = index.getParent().getParent().toString();
        } catch (InvalidPathException e) {
            e.printStackTrace();
            return;
        }

        CoinEntity coin = mRepository.loadCoinSync(Coins.coinIdFromCoinCode(coinCode));
        AccountEntity accountEntity = getAccountEntityByPath(accountHdPath, coin);
        if (accountEntity == null) return;

        List<AddressEntity> addressEntities = mRepository.loadAddressSync(coin.getCoinId());
        Optional<AddressEntity> addressEntityOptional = addressEntities
                .stream()
                .filter(addressEntity -> addressEntity.getPath()
                        .startsWith(accountEntity.getHdPath() + "/" + 0))
                .max((o1, o2) -> o1.getPath().compareTo(o2.getPath()));

        int index = -1;
        if (addressEntityOptional.isPresent()) {
            try {
                AddressIndex addressIndex = CoinPath.parsePath(addressEntityOptional.get().getPath());
                index = addressIndex.getValue();
            } catch (InvalidPathException e) {
                e.printStackTrace();
            }
        }

        if (index < pathIndex) {
            final CountDownLatch mLatch = new CountDownLatch(1);
            addingAddress.postValue(true);
            new AddAddressViewModel.AddAddressTask(coin, mRepository, mLatch::countDown,
                    accountEntity.getExPub(), 0).execute(pathIndex - index);
            try {
                mLatch.await();
                addingAddress.postValue(false);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public MutableLiveData<TxEntity> getObservableTx() {
        return observableTx;
    }
}