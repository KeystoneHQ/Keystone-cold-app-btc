package com.keystone.cold.ui.fragment.main.scan.scanner.scanstate;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.exception.FingerPrintNotMatchException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.exception.UnknownTransactionException;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.R;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.viewmodel.exceptions.UnknowQrCodeException;
import com.keystone.cold.viewmodel.multisigs.PsbtCasaConfirmViewModel;
import com.keystone.cold.viewmodel.multisigs.exceptions.NotMyCasaKeyException;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.spongycastle.util.encoders.Base64;

import java.util.List;

public class CasaScannerState extends ScannerState {
    protected PsbtCasaConfirmViewModel psbtCasaConfirmViewModel;

    public CasaScannerState(List<ScanResultTypes> desiredResults) {
        super(desiredResults);
    }

    @Override
    public void handleScanResult(ScanResult result) throws Exception {
        if (result.getType().equals(ScanResultTypes.UR_CRYPTO_PSBT)) {
            if (handleSignCryptoPSBT(result)) return;
            throw new UnknowQrCodeException("current watch wallet not support bc32 or psbt");
        }
    }

    @Override
    public boolean handleException(Exception e) {
        e.printStackTrace();
        mFragment.dismissLoading();
        if (e instanceof InvalidTransactionException) {
            InvalidTransactionException ex = (InvalidTransactionException) e;
            if (ex.getErrorCode() == InvalidTransactionException.IS_NOTMULTISIG_TX) {
                mFragment.alert(getString(R.string.wallet_not_match_tips), getString(R.string.wallet_not_match));
            } else {
                mFragment.alert(getString(R.string.incorrect_tx_data));
            }
            return true;
        } else if (e instanceof FingerPrintNotMatchException || e instanceof NotMyCasaKeyException) {
            mFragment.alert(getString(R.string.master_pubkey_not_match));
        } else if (e instanceof UnknowQrCodeException) {
            mFragment.alert(getString(R.string.unsupported_qrcode));
            return true;
        } else if (e instanceof UnknownTransactionException) {
            mFragment.alert(getString(R.string.electrum_decode_txn_fail),
                    getString(R.string.incorrect_tx_data));
            return true;
        }
        return super.handleException(e);
    }

    private boolean handleSignCryptoPSBT(ScanResult result) {
        CryptoPSBT cryptoPSBT = (CryptoPSBT) result.resolve();
        byte[] bytes = cryptoPSBT.getPsbt();
        String psbtB64 = Base64.toBase64String(bytes);
        handlePsbtBase64(psbtB64);
        return true;
    }

    private void handlePsbtBase64(String psbtB64) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            mFragment.showLoading("");
            if (psbtCasaConfirmViewModel == null) {
                psbtCasaConfirmViewModel = ViewModelProviders.of(mActivity).get(PsbtCasaConfirmViewModel.class);
            }
            psbtCasaConfirmViewModel.handleTx(psbtB64);
            psbtCasaConfirmViewModel.getObservableSignTx().observe(mActivity, jsonObject -> {
                if (jsonObject != null) {
                    psbtCasaConfirmViewModel.getObservableSignTx().postValue(null);
                    psbtCasaConfirmViewModel.getObservableSignTx().removeObservers(mActivity);
                    mFragment.dismissLoading();
                    Bundle bundle = new Bundle();
                    bundle.putString("psbt_base64", psbtB64);
                    bundle.putString("signTx", jsonObject.toString());
                    bundle.putBoolean("isMainNet", psbtCasaConfirmViewModel.isMainNet());
                    mFragment.navigate(R.id.action_to_psbtCasaTxConfirmFragment, bundle);
                }
            });
            psbtCasaConfirmViewModel.getParseTxException().observe(mActivity, e -> {
                if (e != null) {
                    psbtCasaConfirmViewModel.getParseTxException().postValue(null);
                    psbtCasaConfirmViewModel.getParseTxException().removeObservers(mActivity);
                    handleException(e);
                }
            });
        });
    }
}
