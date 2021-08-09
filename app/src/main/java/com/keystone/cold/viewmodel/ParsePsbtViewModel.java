package com.keystone.cold.viewmodel;

import android.app.Application;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.googlecode.protobuf.format.JsonFormat;
import com.keystone.coinlib.Util;
import com.keystone.coinlib.coins.AbsTx;
import com.keystone.coinlib.coins.BTC.UtxoTx;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.interfaces.Signer;
import com.keystone.coinlib.utils.Account;
import com.keystone.cold.DataRepository;
import com.keystone.cold.MainApplication;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.GetMessageCallable;
import com.keystone.cold.callables.GetPasswordTokenCallable;
import com.keystone.cold.callables.VerifyFingerprintCallable;
import com.keystone.cold.db.entity.AccountEntity;
import com.keystone.cold.db.entity.CoinEntity;
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.protobuf.TransactionProtoc;
import com.keystone.cold.ui.views.AuthenticateModal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.security.SignatureException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.keystone.cold.viewmodel.GlobalViewModel.getAccount;
import static com.keystone.cold.viewmodel.WatchWallet.ELECTRUM;

public abstract class ParsePsbtViewModel extends AndroidViewModel {
    public static final String STATE_NONE = "";
    public static final String STATE_SIGNING = "signing";
    public static final String STATE_SIGN_FAIL = "signing_fail";
    public static final String STATE_SIGN_SUCCESS = "signing_success";
    protected final DataRepository mRepository;
    protected final MutableLiveData<Integer> feeAttachCheckingResult = new MutableLiveData<>();
    protected final MutableLiveData<Boolean> addingAddress = new MutableLiveData<>();
    protected final MutableLiveData<String> signState = new MutableLiveData<>();
    protected final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    protected final MutableLiveData<Exception> parseTxException = new MutableLiveData<>();
    protected AbsTx transaction;
    protected String coinCode;
    protected AuthenticateModal.OnVerify.VerifyToken token;

    public ParsePsbtViewModel(@NonNull Application application) {
        super(application);
        mRepository = MainApplication.getApplication().getRepository();
    }

    public MutableLiveData<Integer> getFeeAttachCheckingResult() {
        return feeAttachCheckingResult;
    }

    public MutableLiveData<String> getSignState() {
        return signState;
    }

    public MutableLiveData<Exception> getParseTxException() {
        return parseTxException;
    }

    protected int getAddressIndex(String hdPath) {
        String[] splits = hdPath.split("/");
        try {
            if (splits.length > 1) {
                return Integer.parseInt(splits[splits.length - 1]);
            }
        } catch (NumberFormatException ignore) {
        }
        return 0;
    }

    protected String getToAddress() {
        String to = transaction.getTo();
        if (transaction instanceof UtxoTx) {
            JSONArray outputs = ((UtxoTx) transaction).getOutputs();
            if (outputs != null) {
                return outputs.toString();
            }
        }
        return to;
    }

    protected JSONObject parsePsbtTx(JSONObject adaptTx) throws JSONException {
        Account account = getAccount(getApplication());
        WatchWallet wallet = WatchWallet.getWatchWallet(getApplication());
        String signId = WatchWallet.getWatchWallet(getApplication()).getSignId();
        if ((account == Account.P2WPKH || account == Account.P2WPKH_TESTNET) && wallet == ELECTRUM) {
            signId += "_NATIVE_SEGWIT";
        }
        boolean isMultisig = adaptTx.optBoolean("multisig");
        TransactionProtoc.SignTransaction.Builder builder = TransactionProtoc.SignTransaction.newBuilder();
        boolean isMainNet = Utilities.isMainNet(getApplication());
        builder.setCoinCode(Utilities.currentCoin(getApplication()).coinCode())
                .setSignId(isMultisig ? "PSBT_MULTISIG" : signId)
                .setTimestamp(generateAutoIncreaseId())
                .setDecimal(8);
        String signTransaction = new JsonFormat().printToString(builder.build());
        JSONObject signTx = new JSONObject(signTransaction);
        signTx.put(isMainNet ? "btcTx" : "xtnTx", adaptTx);
        return signTx;
    }

    private long generateAutoIncreaseId() {
        List<TxEntity> txEntityList = mRepository.loadAllTxsSync(Utilities.currentCoin(getApplication()).coinId());
        if (txEntityList == null || txEntityList.isEmpty()) {
            return 0;
        }
        return txEntityList.stream()
                .max(Comparator.comparing(TxEntity::getTimeStamp))
                .get()
                .getTimeStamp() + 1;
    }

    public boolean isAddressInWhiteList(String address) {
        try {
            return sExecutor.submit(() -> mRepository.queryWhiteList(address) != null).get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setToken(AuthenticateModal.OnVerify.VerifyToken token) {
        this.token = token;
    }

    protected String getAuthToken() {
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

    protected AccountEntity getAccountEntityByPath(String accountHdPath, CoinEntity coin) {
        List<AccountEntity> accountEntities = mRepository.loadAccountsForCoin(coin);
        Optional<AccountEntity> optional = accountEntities.stream()
                .filter(accountEntity ->
                        accountEntity.getHdPath().equals(accountHdPath.toUpperCase()))
                .findFirst();

        AccountEntity accountEntity;
        if (optional.isPresent()) {
            accountEntity = optional.get();
        } else {
            return null;
        }
        return accountEntity;
    }

    public abstract void parseTxData(Bundle bundle);

    public abstract void checkTransaction() throws InvalidTransactionException;

    public abstract void handleSignPsbt(String psbt);

    public abstract Signer[] initSigners();
}
