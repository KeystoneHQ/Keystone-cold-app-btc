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
}
