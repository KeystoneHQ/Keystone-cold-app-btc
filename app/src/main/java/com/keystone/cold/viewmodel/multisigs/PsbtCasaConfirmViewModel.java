package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.keystone.coinlib.Util;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.coinlib.coins.AbsTx;
import com.keystone.coinlib.coins.BTC.Btc;
import com.keystone.coinlib.coins.BTC.BtcImpl;
import com.keystone.coinlib.coins.BTC.UtxoTx;
import com.keystone.coinlib.exception.FingerPrintNotMatchException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.exception.UnknownTransactionException;
import com.keystone.coinlib.interfaces.SignPsbtCallback;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.callables.ClearTokenCallable;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.callables.GetMasterFingerprintCallable;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.util.HashUtil;
import com.keystone.cold.viewmodel.ParsePsbtViewModel;
import com.keystone.cold.viewmodel.multisigs.exceptions.NotMyCasaKeyException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class PsbtCasaConfirmViewModel extends ParsePsbtViewModel {
    private static final String TAG = "PsbtCasaConfirmViewModel";

    private final MutableLiveData<CasaSignature> observableCasaSignature = new MutableLiveData<>();
    protected MultiSigWalletEntity wallet;

    public PsbtCasaConfirmViewModel(@NonNull Application application) {
        super(application);
        observableCasaSignature.setValue(null);
    }

    public MutableLiveData<CasaSignature> getObservableCasaSignature() {
        return observableCasaSignature;
    }

    public void handleTx(String psbtBase64) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                initIsMainNet(psbtBase64);
                JSONObject signTx = parseTxData(psbtBase64);
                transaction = AbsTx.newInstance(signTx);
                checkTransaction();
                observableCasaSignature.postValue(generateCasaSignature(signTx));
                observableSignTx.postValue(signTx);
            } catch (FingerPrintNotMatchException | NotMyCasaKeyException | InvalidTransactionException e) {
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
                observableCasaSignature.postValue(generateCasaSignature(jsonObject));
            } catch (JSONException e) {
                e.printStackTrace();
                parseTxException.postValue(new InvalidTransactionException("invalid data"));
            }
        });
    }

    @Override
    protected void initIsMainNet(String psbtBase64) throws InvalidTransactionException, JSONException {
        Btc btc = new Btc(new BtcImpl(true));
        JSONObject psbtTx = btc.parsePsbt(psbtBase64);
        if (psbtTx == null) {
            throw new InvalidTransactionException("parse failed,invalid psbt data");
        }
        isMainNet = new PsbtCasaTxAdapter().isCasaMainnet(psbtTx);
    }

    @Override
    protected JSONObject parseTxData(String psbtBase64) throws InvalidTransactionException, JSONException,
            FingerPrintNotMatchException, NotMyCasaKeyException {
        Btc btc = new Btc(new BtcImpl(isMainNet));
        JSONObject psbtTx = btc.parsePsbt(psbtBase64);
        if (psbtTx == null) {
            throw new InvalidTransactionException("parse failed,invalid psbt data");
        }
        boolean isMultisigTx = psbtTx.getJSONArray("inputs").getJSONObject(0).getBoolean("isMultiSign");
        if (!isMultisigTx) {
            throw new InvalidTransactionException("", InvalidTransactionException.IS_NOTMULTISIG_TX);
        }
        PsbtCasaTxAdapter psbtCasaTxAdapter = new PsbtCasaTxAdapter();
        JSONObject adaptTx = psbtCasaTxAdapter.adapt(psbtTx);
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

    protected void checkMultisigChangeAddress(AbsTx utxoTx) throws InvalidTransactionException {
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
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidTransactionException("invalid change address");
        }
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
        sig.setBelongTo(mRepository.getBelongTo());
        String signStatus = null;
        if (object.has("btcTx")) {
            signStatus = object.getJSONObject("btcTx").getString("signStatus");
        } else if (object.has("xtnTx")) {
            signStatus = object.getJSONObject("xtnTx").getString("signStatus");
        }
        sig.setSignStatus(signStatus);
        return sig;
    }

    class PsbtCasaTxAdapter {
        private int total;
        private String fingerprintsHash;
        private JSONObject object;
        private boolean isCasaMainnet = true;
        private String mfp;

        public PsbtCasaTxAdapter() {
            mfp = new GetMasterFingerprintCallable().call();
        }

        public boolean isCasaMainnet(JSONObject psbt) throws JSONException {
            JSONArray psbtInputs = psbt.getJSONArray("inputs");
            for (int i = 0; i < psbtInputs.length(); i++) {
                JSONObject psbtInput = psbtInputs.getJSONObject(i);
                JSONArray bip32Derivation = psbtInput.getJSONArray("hdPath");
                String hdPath = "";
                for (int j = 0; j < bip32Derivation.length(); j++) {
                    JSONObject item = bip32Derivation.getJSONObject(j);
                    String fingerprint = item.getString("masterFingerprint");
                    if (fingerprint.equalsIgnoreCase(new GetMasterFingerprintCallable().call())) {
                        hdPath = item.getString("path");
                    }
                }
                String myCasaKey = findMyCasaKey(bip32Derivation);
                if (myCasaKey != null) {
                    if (!hdPath.startsWith(MultiSig.CASA.getPath())) {
                        hdPath = MultiSig.CASA.getPath() + hdPath.substring(1);
                    }
                    String path = hdPath.replace("m/", "");
                    String[] index = path.split("/");
                    isCasaMainnet = index[1].startsWith("0");
                }
            }
            return isCasaMainnet;
        }

        public JSONObject adapt(JSONObject psbt) throws JSONException, FingerPrintNotMatchException, NotMyCasaKeyException, InvalidTransactionException {
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
            return object;
        }

        private void adaptInputs(JSONArray psbtInputs, JSONArray inputs) throws JSONException, NotMyCasaKeyException {
            for (int i = 0; i < psbtInputs.length(); i++) {
                JSONObject psbtInput = psbtInputs.getJSONObject(i);
                JSONObject in = new JSONObject();
                JSONObject utxo = new JSONObject();
                in.put("hash", psbtInput.getString("txId"));
                in.put("index", psbtInput.getInt("index"));

                if (i == 0) {
                    String[] signStatus = psbtInput.getString("signStatus").split("-");
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

                String myCasaKey = findMyCasaKey(bip32Derivation);
                if (myCasaKey != null) {
                    if (!hdPath.startsWith(MultiSig.CASA.getPath())) {
                        hdPath = MultiSig.CASA.getPath() + hdPath.substring(1);
                    }
                    String path = hdPath.replace("m/", "");
                    String[] index = path.split("/");
                    isCasaMainnet = index[1].startsWith("0");
                    utxo.put("publicKey", myCasaKey);
                    utxo.put("value", psbtInput.optLong("value"));
                    in.put("utxo", utxo);
                    in.put("ownerKeyPath", hdPath);
                    in.put("masterFingerprint", mfp);
                    inputs.put(in);
                } else {
                    throw new NotMyCasaKeyException("no matched casa key found");
                }
            }
        }

        private String findMyCasaKey(JSONArray bip32Derivation) throws JSONException {
            for (int i = 0; i < bip32Derivation.length(); i++) {
                if (mfp.equalsIgnoreCase(bip32Derivation.getJSONObject(i).getString("masterFingerprint"))) {
                    return bip32Derivation.getJSONObject(i).getString("pubkey");
                }
            }
            return null;
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
                if (!isCasaMainnet) {
                    address = Util.convertAddressToTestnet(address);
                }
                out.put("address", address);
                out.put("value", psbtOutput.getLong("value"));
                outputs.put(out);
            }
        }
    }
}