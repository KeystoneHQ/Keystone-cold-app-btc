package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;

import androidx.annotation.NonNull;

import com.keystone.coinlib.coins.BTC.UtxoTx;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PsbtCaravanConfirmViewModel extends PsbtLegacyConfirmViewModel{
    public PsbtCaravanConfirmViewModel(@NonNull Application application) {
        super(application);
        mode = "caravan";
    }

    @Override
    protected String getToAddress() {
        String to = transaction.getTo();
        if (transaction instanceof UtxoTx) {
            JSONArray outputs = ((UtxoTx) transaction).getOutputs();
            if (outputs == null) {
                return to;
            }
            if (!wallet.getExPubs().contains("path")) {
                return outputs.toString();
            }
            try {
                for (int i = 0; i < outputs.length(); i++) {
                    JSONObject jsonObject = outputs.getJSONObject(i);
                    if (jsonObject.optBoolean("isChange", false)) {
                        String changeAddressPath = jsonObject.getString("changeAddressPath");
                        changeAddressPath = changeAddressPath.replace(wallet.getExPubPath(), "*");
                        jsonObject.put("changeAddressPath", changeAddressPath);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return outputs.toString();
        }
        return to;
    }
}
