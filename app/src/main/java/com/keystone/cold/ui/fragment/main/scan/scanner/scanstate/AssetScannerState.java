package com.keystone.cold.ui.fragment.main.scan.scanner.scanstate;

import static com.keystone.cold.Utilities.IS_SETUP_VAULT;
import static com.keystone.cold.ui.fragment.setup.WebAuthResultFragment.WEB_AUTH_DATA;

import android.os.Bundle;
import android.util.Log;

import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.exception.CoinNotFindException;
import com.keystone.coinlib.exception.FingerPrintNotMatchException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.exception.UnknownTransactionException;
import com.keystone.coinlib.utils.Base43;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.R;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.viewmodel.KeystoneTxViewModel;
import com.keystone.cold.viewmodel.PsbtSingleConfirmViewModel;
import com.keystone.cold.viewmodel.WatchWallet;
import com.keystone.cold.viewmodel.exceptions.UnknowQrCodeException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class AssetScannerState extends ScannerState {
    private static final String TAG = "AssetScannerState";
    private PsbtSingleConfirmViewModel psbtSigleTxConfirmViewModel;

    public AssetScannerState(List<ScanResultTypes> desiredResults) {
        super(desiredResults);
    }

    @Override
    public void handleScanResult(ScanResult result) throws Exception {
        if (result.getType().equals(ScanResultTypes.PLAIN_TEXT)) {
            if (handleSignElectrumPSBT(result)) return;
            throw new UnknowQrCodeException("not a electrum psbt transaction!");
        } else if (result.getType().equals(ScanResultTypes.UR_BYTES)) {
            if (handleWebAuth(result)) return;
            if (handleKeystoneTx(result)) return;
            if (handleSignUrBytesPSBT(result)) return;
            throw new UnknowQrCodeException("unknown qrcode");
        } else if (result.getType().equals(ScanResultTypes.UR_CRYPTO_PSBT)) {
            if (handleSignCryptoPSBT(result)) return;
            throw new WatchWalletNotMatchException("current watch wallet not support bc32 or psbt");
        }
    }

    @Override
    public boolean handleException(Exception e) {
        e.printStackTrace();
        mFragment.dismissLoading();
        if (e instanceof InvalidTransactionException) {
            InvalidTransactionException ex = (InvalidTransactionException) e;
            if (ex.getErrorCode() == InvalidTransactionException.IS_MULTISIG_TX) {
                mFragment.alert(getString(R.string.wallet_not_match_tips), getString(R.string.wallet_not_match));
            } else {
                mFragment.alert(getString(R.string.incorrect_tx_data));
            }
            return true;
        } else if (e instanceof CoinNotFindException) {
            mFragment.alert(null, getString(R.string.only_support_btc), null);
            return true;
        } else if (e instanceof JSONException) {
            mFragment.alert(getString(R.string.incorrect_qrcode));
            return true;
        } else if (e instanceof XfpNotMatchException) {
            mFragment.alert(getString(R.string.uuid_not_match));
            return true;
        } else if (e instanceof FingerPrintNotMatchException) {
            mFragment.alert(getString(R.string.master_pubkey_not_match));
            return true;
        } else if (e instanceof WatchWalletNotMatchException) {
            mFragment.alert(getString(R.string.error_hint),
                    getString(R.string.unknown_qrcode, WatchWallet.getWatchWallet(mActivity).getWalletName(mActivity)));
            return true;
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

    private boolean handleWebAuth(ScanResult result) {
        try {
            JSONObject object = new JSONObject(new String((byte[]) result.resolve(), StandardCharsets.UTF_8));
            JSONObject webAuth = object.optJSONObject("data");
            if (webAuth != null && webAuth.optString("type").equals("webAuth")) {
                String data = webAuth.getString("data");
                Bundle bundle = new Bundle();
                bundle.putString(WEB_AUTH_DATA, data);
                bundle.putBoolean(IS_SETUP_VAULT, false);
                mFragment.navigate(R.id.action_QRCodeScan_to_result, bundle);
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean handleKeystoneTx(ScanResult result) throws InvalidTransactionException,
            XfpNotMatchException, JSONException, UnknowQrCodeException, CoinNotFindException,
            WatchWalletNotMatchException {
        KeystoneTxViewModel viewModel = ViewModelProviders.of(mActivity).get(KeystoneTxViewModel.class);
        byte[] bytes = (byte[]) result.resolve();
        String hex = Hex.toHexString(bytes);
        JSONObject object = viewModel.decodeAsProtobuf(hex);
        if (object != null) {
            Log.i(TAG, "decodeAsProtobuf result: " + object);
            Bundle bundle = viewModel.decodeAsBundle(object);
            mFragment.navigate(R.id.action_to_txConfirmFragment, bundle);
            return true;
        }
        return false;
    }

    private boolean handleSignElectrumPSBT(ScanResult result) {
        byte[] data = Base43.decode(result.getData());
        if (new String(data).startsWith("psbt")) {
            String hex = result.getData();
            String psbtB64 = Base64.toBase64String(Base43.decode(hex));
            handlePsbtBase64(psbtB64);
            return true;
        }
        return false;
    }

    private boolean handleSignUrBytesPSBT(ScanResult result) {
        byte[] bytes = (byte[]) result.resolve();
        String hex = Hex.toHexString(bytes);
        if (hex.startsWith(Hex.toHexString("psbt".getBytes()))) {
            String psbtB64 = Base64.toBase64String(Hex.decode(hex));
            handlePsbtBase64(psbtB64);
            return true;
        }
        return false;
    }

    private boolean handleSignCryptoPSBT(ScanResult result) {
        WatchWallet watchWallet = WatchWallet.getWatchWallet(mActivity);
        if (watchWallet.supportBc32QrCode() && watchWallet.supportPsbt()) {
            CryptoPSBT cryptoPSBT = (CryptoPSBT) result.resolve();
            byte[] bytes = cryptoPSBT.getPsbt();
            String psbtB64 = Base64.toBase64String(bytes);
            handlePsbtBase64(psbtB64);
            return true;
        } else {
            return false;
        }
    }

    private void handlePsbtBase64(String psbtB64) {
        AppExecutors.getInstance().mainThread().execute(() -> {
            mFragment.showLoading("");
            if (psbtSigleTxConfirmViewModel == null) {
                psbtSigleTxConfirmViewModel = ViewModelProviders.of(mActivity).get(PsbtSingleConfirmViewModel.class);
            }
            psbtSigleTxConfirmViewModel.handleTx(psbtB64);
            psbtSigleTxConfirmViewModel.getObservableSignTx().observe(mActivity, jsonObject -> {
                if (jsonObject != null) {
                    psbtSigleTxConfirmViewModel.getObservableSignTx().postValue(null);
                    psbtSigleTxConfirmViewModel.getObservableSignTx().removeObservers(mActivity);
                    psbtSigleTxConfirmViewModel.getObservableTx().postValue(null);
                    psbtSigleTxConfirmViewModel.getObservableTx().removeObservers(mActivity);
                    mFragment.dismissLoading();
                    Bundle bundle = new Bundle();
                    bundle.putString("psbt_base64", psbtB64);
                    bundle.putString("signTx", jsonObject.toString());
                    Integer value = psbtSigleTxConfirmViewModel.getFeeAttachCheckingResult().getValue();
                    bundle.putInt("feeAttach", value != null ? value : 0);
                    mFragment.navigate(R.id.action_to_psbtSigleTxConfirmFragment, bundle);
                }
            });
            psbtSigleTxConfirmViewModel.getParseTxException().observe(mActivity, e -> {
                if (e != null) {
                    psbtSigleTxConfirmViewModel.getParseTxException().postValue(null);
                    psbtSigleTxConfirmViewModel.getParseTxException().removeObservers(mActivity);
                    handleException(e);
                }
            });
        });
    }
}
