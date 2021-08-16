package com.keystone.cold.ui.fragment.main.adapter;

import com.keystone.coinlib.Util;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.coinlib.exception.FingerPrintNotMatchException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.cold.callables.GetMasterFingerprintCallable;
import com.keystone.cold.util.HashUtil;
import com.keystone.cold.viewmodel.multisigs.exceptions.NotMyCasaKeyException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;

public class PsbtCasaTxAdapter {
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
                isCasaMainnet = !index[1].equals("1'");
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
                isCasaMainnet = !index[1].equals("1'");
                utxo.put("publicKey", myCasaKey);
                utxo.put("value", psbtInput.optInt("value"));
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
            out.put("value", psbtOutput.getInt("value"));
            outputs.put(out);
        }
    }
} 