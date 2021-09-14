package com.keystone.cold.viewmodel.multisigs;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.keystone.coinlib.Util;
import com.keystone.coinlib.coins.AbsCoin;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.callables.GetMessageCallable;
import com.keystone.cold.callables.GetPasswordTokenCallable;
import com.keystone.cold.callables.VerifyFingerprintCallable;
import com.keystone.cold.encryption.ChipSigner;
import com.keystone.cold.ui.views.AuthenticateModal;

import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Objects;

public class SignViewModel extends AndroidViewModel {
    public static final String STATE_NONE = "";
    public static final String STATE_SIGNING = "signing";
    public static final String STATE_SIGN_FAIL = "signing_fail";
    public static final String STATE_SIGN_SUCCESS = "signing_success";
    public static final String TAG = "SignViewModel";
    private final MutableLiveData<String> signStatus;
    private AuthenticateModal.OnVerify.VerifyToken token;

    private final MutableLiveData<String> signMessageSignature;

    public MutableLiveData<String> getSignStatus() {
        return signStatus;
    }

    public MutableLiveData<String> getSignMessageSignature() {
        return signMessageSignature;
    }

    public SignViewModel(@NonNull Application application) {
        super(application);
        signStatus = new MutableLiveData<>();
        signStatus.postValue(STATE_NONE);
        signMessageSignature = new MutableLiveData<>();
        signMessageSignature.postValue(null);
    }

    public void resetAllState() {
        signStatus.postValue(STATE_NONE);
        signMessageSignature.postValue(null);
    }

    public void handleSignMessage(String message, String path) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                signStatus.postValue(STATE_SIGNING);
                AbsCoin coin = AbsCoin.newInstance("BTC");
                Objects.requireNonNull(coin);
                String messageHex = Hex.toHexString(message.getBytes(StandardCharsets.UTF_8));
                String result = coin.signMessage(messageHex, getSigner(path));
                Log.d(TAG, "handleSignMessage: " + result);
                if (!TextUtils.isEmpty(result)) {
                    signStatus.postValue(STATE_SIGN_SUCCESS);
                    signMessageSignature.postValue(result);
                } else {
                    signStatus.postValue(STATE_SIGN_FAIL);
                }
            } catch (Exception e) {
                e.printStackTrace();
                signStatus.postValue(STATE_SIGN_FAIL);
            }
        });
    }

    private String getAuthToken() {
        String authToken = null;
        if (!TextUtils.isEmpty(token.password)) {
            authToken = new GetPasswordTokenCallable(token.password).call();
        } else if (token.signature != null) {
            String message = new GetMessageCallable().call();
            if (!TextUtils.isEmpty(message)) {
                try {
                    token.signature.update(Hex.decode(message));
                    byte[] signature = token.signature.sign();
                    byte[] rs = Util.decodeRSFromDER(signature);
                    if (rs != null) {
                        authToken = new VerifyFingerprintCallable(Hex.toHexString(rs)).call();
                    }
                } catch (SignatureException e) {
                    e.printStackTrace();
                }
            }
        }
        AuthenticateModal.OnVerify.VerifyToken.invalid(token);
        return authToken;
    }

    public void setToken(AuthenticateModal.OnVerify.VerifyToken token) {
        this.token = token;
    }

    private Signer getSigner(String distinctPath) {
        String authToken = getAuthToken();
        if (TextUtils.isEmpty(authToken)) {
            Log.w(TAG, "authToken null");
            return null;
        }
        int point = Math.max(distinctPath.lastIndexOf("'"), 0);
        String XPubPath = distinctPath.substring(0, point + 1);
        String path = distinctPath.replace(XPubPath + "/", "");
        String[] index = path.split("/");
        String expub = new GetExtendedPublicKeyCallable(XPubPath).call();
        String pubKey = Util.deriveFromKey(
                expub, index);
        return new ChipSigner(distinctPath.toLowerCase(), authToken, pubKey);
    }
}
