package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.keystone.coinlib.ExtendPubkeyFormat;
import com.keystone.coinlib.Util;
import com.keystone.coinlib.coins.AbsTx;
import com.keystone.coinlib.coins.BTC.Btc;
import com.keystone.coinlib.coins.BTC.BtcImpl;
import com.keystone.coinlib.coins.BTC.UtxoTx;
import com.keystone.coinlib.exception.FingerPrintNotMatchException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.exception.UnknownTransactionException;
import com.keystone.coinlib.interfaces.SignPsbtCallback;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.DataRepository;
import com.keystone.cold.MainApplication;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.ClearTokenCallable;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.callables.GetMasterFingerprintCallable;
import com.keystone.cold.db.entity.MultiSigAddressEntity;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.util.HashUtil;
import com.keystone.cold.viewmodel.ParsePsbtViewModel;
import com.keystone.cold.viewmodel.exceptions.NoMatchedMultisigWalletException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.keystone.coinlib.Util.getExpubFingerprint;
import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.DUPLICATE_TX;
import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.NORMAL;
import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.SAME_OUTPUTS;

public class PsbtLegacyConfirmViewModel extends ParsePsbtViewModel {
    private static final String TAG = "PsbtLegacyConfirmViewModel";
    protected final MutableLiveData<TxEntity> observableTx = new MutableLiveData<>();
    private MultiSigWalletEntity wallet;

    public PsbtLegacyConfirmViewModel(@NonNull Application application) {
        super(application);
        observableTx.setValue(null);
    }

    public void handleTx(String psbtBase64) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                initIsMainNet(psbtBase64);
                JSONObject signTx = parseTxData(psbtBase64);
                transaction = AbsTx.newInstance(signTx);
                checkTransaction();
                observableTx.postValue(generateLegacyTxEntity(signTx));
                observableSignTx.postValue(signTx);
            } catch (FingerPrintNotMatchException | NoMatchedMultisigWalletException | InvalidTransactionException e) {
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

    public void generateTx(String signTx) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                JSONObject jsonObject = new JSONObject(signTx);
                transaction = AbsTx.newInstance(jsonObject);
                observableTx.postValue(generateLegacyTxEntity(jsonObject));
            } catch (JSONException e) {
                e.printStackTrace();
                parseTxException.postValue(new InvalidTransactionException("invalid data"));
            }
        });
    }

    @Override
    protected void initIsMainNet(String psbtBase64) {
        isMainNet = Utilities.isMainNet(getApplication());
    }

    @Override
    protected JSONObject parseTxData(String psbtBase64) throws InvalidTransactionException, JSONException,
            FingerPrintNotMatchException, NoMatchedMultisigWalletException {
        Btc btc = new Btc(new BtcImpl(isMainNet));
        JSONObject psbtTx = btc.parsePsbt(psbtBase64);
        if (psbtTx == null) {
            throw new InvalidTransactionException("parse failed,invalid psbt data");
        }
        boolean isMultisigTx = psbtTx.getJSONArray("inputs").getJSONObject(0).getBoolean("isMultiSign");
        if (!isMultisigTx) {
            throw new InvalidTransactionException("", InvalidTransactionException.IS_NOTMULTISIG_TX);
        }
        PsbtLegacyTxAdapter psbtLegacyTxAdapter = new PsbtLegacyTxAdapter();
        JSONObject adaptTx = psbtLegacyTxAdapter.adapt(psbtTx);
        wallet = psbtLegacyTxAdapter.getWallet();
        return parsePsbtTx(adaptTx);
    }

    @Override
    protected void checkTransaction() throws InvalidTransactionException {
        if (transaction == null) {
            throw new InvalidTransactionException("invalid transaction");
        }
        if (transaction instanceof UtxoTx) {
            checkMultisigChangeAddress(transaction);
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
            Btc btc = new Btc(new BtcImpl(isMainNet));
            btc.signPsbt(psbt, callback, false, signer);
        });
    }

    private Signer[] initSigners() {
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

    protected void feeAttackChecking() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            String inputs = getMultiSigFromAddress();
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

    private void checkMultisigChangeAddress(AbsTx utxoTx) throws InvalidTransactionException {
        List<UtxoTx.ChangeAddressInfo> changeAddressInfo = ((UtxoTx) utxoTx).getChangeAddressInfo();
        if (changeAddressInfo == null || changeAddressInfo.isEmpty()) {
            return;
        }
        try {
            String exPubPath = wallet.getExPubPath();
            for (UtxoTx.ChangeAddressInfo info : changeAddressInfo) {
                String path = info.hdPath;
                String address = info.address;
                if (!path.startsWith(exPubPath)) {
                    throw new InvalidTransactionException("invalid path");
                }
                path = path.replace(exPubPath + "/", "");

                String[] index = path.split("/");

                if (index.length != 2) {
                    throw new InvalidTransactionException("invalid path length");
                }
                String expectedAddress = wallet.deriveAddress(
                        new int[]{Integer.parseInt(index[0]), Integer.parseInt(index[1])}, isMainNet);

                if (!expectedAddress.equals(address)) {
                    throw new InvalidTransactionException("invalid expectedAddress");
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            throw new InvalidTransactionException("NumberFormatException");
        }
    }

    private TxEntity generateLegacyTxEntity(JSONObject object) throws JSONException {
        String walletFingerprint = null;
        if (object.has("btcTx")) {
            walletFingerprint = object.getJSONObject("btcTx").getString("wallet_fingerprint");
        } else if (object.has("xtnTx")) {
            walletFingerprint = object.getJSONObject("xtnTx").getString("wallet_fingerprint");
        }
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
                            new int[]{Integer.parseInt(index[0]), Integer.parseInt(index[1])}, isMainNet);
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

    public MutableLiveData<TxEntity> getObservableTx() {
        return observableTx;
    }

    class PsbtLegacyTxAdapter {
        private int total;
        private int threshold;
        private String fingerprintsHash;
        private JSONObject object;
        private MultiSigWalletEntity wallet;
        private final DataRepository mRepository;

        public PsbtLegacyTxAdapter() {
            mRepository = MainApplication.getApplication().getRepository();
        }

        public MultiSigWalletEntity getWallet() {
            return wallet;
        }

        public JSONObject adapt(JSONObject psbt) throws JSONException, FingerPrintNotMatchException, NoMatchedMultisigWalletException, InvalidTransactionException {
            if (psbt == null) {
                throw new InvalidTransactionException("parse failed,invalid psbt data");
            }
            object = new JSONObject();
            JSONArray inputs = new JSONArray();
            JSONArray outputs = new JSONArray();
            adaptInputs(psbt.getJSONArray("inputs"), inputs);
            if (inputs.length() < 1) {
                throw new FingerPrintNotMatchException("no input match masterFingerprint");
            }
            adaptOutputs(psbt.getJSONArray("outputs"), outputs);
            object.put("inputs", inputs);
            object.put("outputs", outputs);
            object.put("multisig", true);
            object.put("wallet_fingerprint", wallet != null ? wallet.getWalletFingerPrint() : null);
            return object;
        }

        private void adaptInputs(JSONArray psbtInputs, JSONArray inputs) throws JSONException, NoMatchedMultisigWalletException {
            for (int i = 0; i < psbtInputs.length(); i++) {
                JSONObject psbtInput = psbtInputs.getJSONObject(i);
                JSONObject in = new JSONObject();
                JSONObject utxo = new JSONObject();
                in.put("hash", psbtInput.getString("txId"));
                in.put("index", psbtInput.getInt("index"));

                if (i == 0) {
                    String[] signStatus = psbtInput.getString("signStatus").split("-");
                    threshold = Integer.parseInt(signStatus[1]);
                    total = Integer.parseInt(signStatus[2]);
                    object.put("signStatus", psbtInput.getString("signStatus"));
                }

                JSONArray bip32Derivation = psbtInput.getJSONArray("hdPath");
                int length = bip32Derivation.length();
                if (length != total) break;
                String hdPath = "";
                List<String> fps = new ArrayList<>();
                for (int j = 0; j < total; j++) {
                    JSONObject item = bip32Derivation.getJSONObject(j);
                    String fingerprint = item.getString("masterFingerprint");
                    if (fingerprint.equalsIgnoreCase(new GetMasterFingerprintCallable().call())) {
                        hdPath = item.getString("path");
                    }
                    fps.add(fingerprint);
                }

                // the first input xpub info
                if (i == 0) {
                    fingerprintsHash = fingerprintsHash(fps);
                }

                //all input should have the same xpub info
                if (!fingerprintsHash(fps).equals(fingerprintsHash)) break;

                //find the exists multisig wallet match the xpub info
                if (wallet == null) {
                    List<MultiSigWalletEntity> wallets = mRepository.loadAllMultiSigWalletSync()
                            .stream()
                            .filter(w -> w.getTotal() == total && w.getThreshold() == threshold)
                            .collect(Collectors.toList());
                    for (MultiSigWalletEntity w : wallets) {
                        if (w.getTotal() != total || w.getThreshold() != threshold) continue;
                        JSONArray array = new JSONArray(w.getExPubs());
                        List<String> walletFps = new ArrayList<>();
                        List<String> walletRootXfps = new ArrayList<>();
                        for (int k = 0; k < array.length(); k++) {
                            JSONObject xpub = array.getJSONObject(k);
                            walletFps.add(getExpubFingerprint(xpub.getString("xpub")));
                            walletRootXfps.add(xpub.getString("xfp"));
                        }
                        if (fingerprintsHash(walletFps).equalsIgnoreCase(fingerprintsHash)
                                || (fingerprintsHash(walletRootXfps).equalsIgnoreCase(fingerprintsHash)
                                && hdPath.startsWith(w.getExPubPath()))) {
                            wallet = w;
                            break;
                        }
                    }
                }

                if (wallet != null) {
                    if (!hdPath.startsWith(wallet.getExPubPath())) {
                        hdPath = wallet.getExPubPath() + hdPath.substring(1);
                    }
                    utxo.put("publicKey", findMyPubKey(bip32Derivation));
                    utxo.put("value", psbtInput.optInt("value"));
                    in.put("utxo", utxo);
                    in.put("ownerKeyPath", hdPath);
                    in.put("masterFingerprint", wallet.getBelongTo());
                    inputs.put(in);
                } else {
                    throw new NoMatchedMultisigWalletException("no matched multisig wallet");
                }
            }
        }

        private String findMyPubKey(JSONArray bip32Derivation)
                throws JSONException {
            String xfp = wallet.getBelongTo();
            String fp = null;
            JSONArray array = new JSONArray(wallet.getExPubs());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj.getString("xfp").equalsIgnoreCase(xfp)) {
                    fp = getExpubFingerprint(obj.getString("xpub"));
                }
            }

            if (fp != null) {
                for (int i = 0; i < bip32Derivation.length(); i++) {
                    if (fp.equalsIgnoreCase(bip32Derivation.getJSONObject(i)
                            .getString("masterFingerprint"))) {
                        return bip32Derivation.getJSONObject(i).getString("pubkey");
                    }
                }
            }
            return "";
        }


        private String fingerprintsHash(List<String> fps) {
            String concat = fps.stream()
                    .map(String::toUpperCase)
                    .sorted()
                    .reduce((s1, s2) -> s1 + s2).orElse("");

            return Hex.toHexString(HashUtil.sha256(concat));
        }

        private void adaptOutputs(JSONArray psbtOutputs, JSONArray outputs) throws JSONException {
            for (int i = 0; i < psbtOutputs.length(); i++) {
                JSONObject psbtOutput = psbtOutputs.getJSONObject(i);
                JSONObject out = new JSONObject();
                String address = psbtOutput.getString("address");
                out.put("address", address);
                out.put("value", psbtOutput.getInt("value"));
                JSONArray bip32Derivation = psbtOutput.optJSONArray("hdPath");
                if (bip32Derivation != null) {
                    for (int j = 0; j < bip32Derivation.length(); j++) {
                        JSONObject item = bip32Derivation.getJSONObject(j);
                        String hdPath = item.getString("path");
                        String xfp = item.getString("masterFingerprint");
                        String rootXfp = new GetMasterFingerprintCallable().call();
                        if (xfp.equalsIgnoreCase(rootXfp)) {
                            if (!hdPath.startsWith(wallet.getExPubPath())) {
                                hdPath = wallet.getExPubPath() + hdPath.substring(1);
                            }
                            out.put("isChange", true);
                            out.put("changeAddressPath", hdPath);
                            break;
                        }
                    }
                }
                outputs.put(out);
            }
        }
    }
}