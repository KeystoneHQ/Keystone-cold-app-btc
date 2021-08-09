package com.keystone.cold.ui.fragment.main.adapter;

import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.cold.DataRepository;
import com.keystone.cold.MainApplication;
import com.keystone.cold.callables.GetMasterFingerprintCallable;
import com.keystone.cold.db.entity.MultiSigWalletEntity;
import com.keystone.cold.util.HashUtil;
import com.keystone.cold.viewmodel.exceptions.NoMatchedMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.keystone.coinlib.Util.getExpubFingerprint;

public class PsbtLegacyTxAdapter {
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

    public JSONObject adapt(JSONObject psbt) throws JSONException, WatchWalletNotMatchException, NoMatchedMultisigWalletException, InvalidTransactionException {
        if (psbt == null) {
            throw new InvalidTransactionException("parse failed,invalid psbt data");
        }
        object = new JSONObject();
        JSONArray inputs = new JSONArray();
        JSONArray outputs = new JSONArray();
        adaptInputs(psbt.getJSONArray("inputs"), inputs);
        if (inputs.length() < 1) {
            throw new WatchWalletNotMatchException("no input match masterFingerprint");
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