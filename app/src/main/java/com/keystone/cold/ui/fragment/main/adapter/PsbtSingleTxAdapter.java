package com.keystone.cold.ui.fragment.main.adapter;

import com.keystone.coinlib.Util;
import com.keystone.coinlib.exception.FingerPrintNotMatchException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.utils.Account;
import com.keystone.cold.MainApplication;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.callables.GetMasterFingerprintCallable;
import com.keystone.cold.viewmodel.GlobalViewModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PsbtSingleTxAdapter {
    public JSONObject adapt(JSONObject psbt) throws JSONException, FingerPrintNotMatchException, InvalidTransactionException {
        if (psbt == null) {
            throw new InvalidTransactionException("parse failed,invalid psbt data");
        }
        JSONObject object = new JSONObject();
        JSONArray inputs = new JSONArray();
        JSONArray outputs = new JSONArray();
        adaptInputs(psbt.getJSONArray("inputs"), inputs);
        if (inputs.length() < 1) {
            throw new FingerPrintNotMatchException("no input match masterFingerprint");
        }
        adaptOutputs(psbt.getJSONArray("outputs"), outputs);
        object.put("inputs", inputs);
        object.put("outputs", outputs);
        return object;
    }

    private void adaptInputs(JSONArray psbtInputs, JSONArray inputs) throws JSONException {
        Account account = GlobalViewModel.getAccount(MainApplication.getApplication());
        for (int i = 0; i < psbtInputs.length(); i++) {
            JSONObject psbtInput = psbtInputs.getJSONObject(i);
            JSONObject in = new JSONObject();
            JSONObject utxo = new JSONObject();
            in.put("hash", psbtInput.getString("txId"));
            in.put("index", psbtInput.getInt("index"));
            JSONArray bip32Derivation = psbtInput.getJSONArray("hdPath");
            for (int j = 0; j < bip32Derivation.length(); j++) {
                JSONObject item = bip32Derivation.getJSONObject(j);
                String hdPath = item.getString("path");
                String fingerprint = item.getString("masterFingerprint");
                boolean match = false;
                if (matchRootXfp(fingerprint, hdPath)) {
                    match = true;
                }
                if (!match) {
                    match = matchKeyXfp(fingerprint);
                    hdPath = account.getPath() + hdPath.substring(1);
                }
                if (match) {
                    utxo.put("publicKey", item.getString("pubkey"));
                    utxo.put("value", psbtInput.optInt("value"));
                    in.put("utxo", utxo);
                    in.put("ownerKeyPath", hdPath);
                    in.put("masterFingerprint", item.getString("masterFingerprint"));
                    inputs.put(in);
                    break;
                }
            }

        }

    }

    private boolean matchRootXfp(String fingerprint, String path) {
        String rootXfp = new GetMasterFingerprintCallable().call();
        Account account = GlobalViewModel.getAccount(MainApplication.getApplication());
        return (fingerprint.equalsIgnoreCase(rootXfp)
                || Util.reverseHex(fingerprint).equalsIgnoreCase(rootXfp))
                && path.toUpperCase().startsWith(account.getPath());
    }

    private boolean matchKeyXfp(String fingerprint) {
        Account account = GlobalViewModel.getAccount(MainApplication.getApplication());
        String xpub = new GetExtendedPublicKeyCallable(account.getPath()).call();
        String xfp = Util.getExpubFingerprint(xpub);
        return (fingerprint.equalsIgnoreCase(xfp)
                || Util.reverseHex(fingerprint).equalsIgnoreCase(xfp));
    }

    private void adaptOutputs(JSONArray psbtOutputs, JSONArray outputs) throws JSONException {
        Account account = GlobalViewModel.getAccount(MainApplication.getApplication());
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
                    String fingerprint = item.getString("masterFingerprint");
                    boolean match = false;
                    if (matchRootXfp(fingerprint, hdPath)) {
                        match = true;
                    }
                    if (!match) {
                        match = matchKeyXfp(fingerprint);
                        hdPath = account.getPath().toLowerCase() + hdPath.substring(1);
                    }

                    if (match) {
                        out.put("isChange", true);
                        out.put("changeAddressPath", hdPath);

                    }
                }
            }
            outputs.put(out);
        }
    }
} 