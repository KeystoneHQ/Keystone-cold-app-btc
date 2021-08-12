/*
 * Copyright (c) 2021 Keystone
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * in the file COPYING.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.keystone.cold.viewmodel;

import android.app.Application;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.coinlib.exception.CoinNotFindException;
import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.utils.B58;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.GetMasterFingerprintCallable;
import com.keystone.cold.protocol.ZipUtil;
import com.keystone.cold.protocol.parser.ProtoParser;
import com.keystone.cold.ui.fragment.main.scan.legacy.QRCodeScanFragment;
import com.keystone.cold.ui.fragment.main.QrScanPurpose;
import com.keystone.cold.viewmodel.exceptions.CollectExPubWrongDataException;
import com.keystone.cold.viewmodel.exceptions.InvalidMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.UnknowQrCodeException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.exceptions.XfpNotMatchException;
import com.keystone.cold.viewmodel.multisigs.LegacyMultiSigViewModel;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.sparrowwallet.hummingbird.registry.CryptoAccount;
import com.sparrowwallet.hummingbird.registry.CryptoCoinInfo;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;
import com.sparrowwallet.hummingbird.registry.CryptoKeypath;
import com.sparrowwallet.hummingbird.registry.CryptoOutput;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;
import com.sparrowwallet.hummingbird.registry.PathComponent;
import com.sparrowwallet.hummingbird.registry.ScriptExpression;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.DataItem;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.keystone.cold.Utilities.IS_SETUP_VAULT;
import static com.keystone.cold.ui.fragment.main.TxConfirmFragment.KEY_TX_DATA;
import static com.keystone.cold.ui.fragment.setup.WebAuthResultFragment.WEB_AUTH_DATA;

public class QrScanViewModel extends AndroidViewModel {

    private static final String TAG = "Vault.Qrcode.QrScanViewModel";

    private final boolean isSetupVault;
    private QRCodeScanFragment fragment;

    private QrScanViewModel(@NonNull Application application, boolean isSetupVault) {
        super(application);
        this.isSetupVault = isSetupVault;
    }

    public void handleUrQrCode(QRCodeScanFragment owner, String hex) throws
            UnknowQrCodeException, XfpNotMatchException,
            InvalidTransactionException, JSONException, CoinNotFindException,
            WatchWalletNotMatchException,
            InvalidMultisigWalletException {
        this.fragment = owner;
        if (!TextUtils.isEmpty(hex)) {
            WatchWallet wallet = WatchWallet.getWatchWallet(getApplication());
            if (QrScanPurpose.IMPORT_MULTISIG_WALLET == fragment.getPurpose()) {
                JSONObject object = LegacyMultiSigViewModel.decodeColdCardWalletFile(new String(Hex.decode(hex), StandardCharsets.UTF_8));
                if (object == null) {
                    object = LegacyMultiSigViewModel.decodeCaravanWalletFile(new String(Hex.decode(hex), StandardCharsets.UTF_8));
                }
                if (object != null) {
                    fragment.handleImportMultisigWallet(hex);
                } else {
                    throw new InvalidMultisigWalletException("invalid multisig wallet qrcode");
                }
            } else if (QrScanPurpose.MULTISIG_TX == fragment.getPurpose()) {
                if (hex.startsWith(Hex.toHexString("psbt".getBytes()))) {
                    handleSignPsbt(hex);
                    return;
                }
                CryptoPSBT cryptoPSBT = decodeCryptoPSBT(hex);
                if (cryptoPSBT != null) {
                    handleSignPsbt(Hex.toHexString(cryptoPSBT.getPsbt()));
                    return;
                }
                throw new UnknowQrCodeException("unknow bc32 qrcode");
            } else if (isPsbt(hex)) {
                if (wallet.supportBc32QrCode()) {
                    WatchWallet watchWallet = WatchWallet.getWatchWallet(getApplication());
                    if (watchWallet.supportPsbt()) {
                        handleSignPsbt(hex);
                    }
                } else {
                    throw new UnknowQrCodeException("not support bc32 qrcode in current wallet mode");
                }
            } else if (tryDecodeAsJson(hex) != null) {
                JSONObject object = tryDecodeAsJson(hex);
                if (checkWebAuth(object)) return;
            } else if (decodeAsProtobuf(hex) != null) {
                JSONObject object = decodeAsProtobuf(hex);

                if (wallet == WatchWallet.KEYSTONE) {
                    decodeAndProcess(object);
                } else {
                    throw new UnknowQrCodeException("not support bc32 qrcode in current wallet mode");
                }
            } else {
                throw new UnknowQrCodeException("not support bc32 qrcode in current wallet mode");
            }
        }
    }

    private boolean isPsbt(String hex) {
        return hex.startsWith(Hex.toHexString("psbt".getBytes()));
    }

    private JSONObject decodeAsProtobuf(String hex) {
        hex = ZipUtil.unzip(hex);
        return new ProtoParser(Hex.decode(hex)).parseToJson();
    }

    private JSONObject tryDecodeAsJson(String hex) {
        try {
            return new JSONObject(new String(Hex.decode(hex)));
        } catch (Exception ignored) {
        }
        return null;
    }

    private CryptoPSBT decodeCryptoPSBT(String cborPayload) {
        try {
            List<DataItem> dataItems = CborDecoder.decode(Hex.decode(cborPayload));
            return CryptoPSBT.fromCbor(dataItems.get(0));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CryptoOutput decodeCryptoOutput(String cborPayload) {
        try {
            List<DataItem> dataItems = CborDecoder.decode(Hex.decode(cborPayload));
            return CryptoOutput.fromCbor(dataItems.get(0));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public CryptoAccount decodeCryptoAccount(String hex) {
        try {
            List<DataItem> dataItems = CborDecoder.decode(Hex.decode(hex));
            return CryptoAccount.fromCbor(dataItems.get(0));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<ScriptExpression> getScriptExpressionByAccount(Account account) {
        switch (account) {
            case MULTI_P2SH:
            case MULTI_P2SH_TEST:
                return Arrays.asList(ScriptExpression.SCRIPT_HASH);
            case MULTI_P2WSH:
            case MULTI_P2WSH_TEST:
                return Arrays.asList(ScriptExpression.WITNESS_SCRIPT_HASH);
            default:
                // MULTI_P2SH_P2WSH, MULTI_P2SH_P2WSH_TEST:
                return Arrays.asList(ScriptExpression.SCRIPT_HASH, ScriptExpression.WITNESS_SCRIPT_HASH);
        }
    }


    public CryptoOutput collectMultiSigCryptoOutputFromCryptoAccount(CryptoAccount cryptoAccount, Account targetAccount) {
        List<CryptoOutput> cryptoOutputs = cryptoAccount.getOutputDescriptors();
        if (cryptoOutputs.size() == 0) {
            return null;
        }
        List<ScriptExpression> scriptExpressions = getScriptExpressionByAccount(targetAccount);
        AtomicReference<CryptoOutput> result = new AtomicReference<>();
        //find target account;
        cryptoOutputs.stream().forEach(cryptoOutput -> {
            if (cryptoOutput.getScriptExpressions().equals(scriptExpressions)) {
                result.set(cryptoOutput);
            }
        });
        if (result.get() == null) {
            result.set(cryptoOutputs.get(0));
        }
        return result.get();
    }

    public String handleCollectExPubWithCryptoOutput(CryptoOutput cryptoOutput) throws CollectExPubWrongDataException, JSONException {
        try {
            CryptoHDKey cryptoHDKey = cryptoOutput.getHdKey();
            if (cryptoHDKey != null) {
                JSONObject object = new JSONObject();
                CryptoKeypath origin = cryptoHDKey.getOrigin();
                if (origin == null) {
                    throw new CollectExPubWrongDataException("invalid CryptoHDKey: origin is null");
                }

                String path = "m/" + origin.getPath();
                if (path == null) {
                    throw new CollectExPubWrongDataException("invalid CryptoHDKey: origin path is null");
                }
                if (!MultiSig.isValidPath(path)) {
                    throw new CollectExPubWrongDataException("invalid CryptoHDKey: origin path is invalid: " + path);
                }

                int depth;
                List<PathComponent> pathComponents = origin.getComponents();
                if (origin.getDepth() != null) {
                    depth = origin.getDepth();
                } else {
                    depth = pathComponents.size();
                }

                PathComponent lastPathComponent = pathComponents.get(pathComponents.size() - 1);

                boolean isTestnet = false;
                CryptoCoinInfo coinInfo = cryptoHDKey.getUseInfo();
                if (coinInfo != null) {
                    isTestnet = coinInfo.getNetwork().equals(CryptoCoinInfo.Network.TESTNET);
                }
                Account account = MultiSig.ofPath(path, !isTestnet).get(0);
                byte[] parentFingerprint = cryptoHDKey.getParentFingerprint();
                if (parentFingerprint == null) {
                    throw new CollectExPubWrongDataException("invalid CryptoHDKey: parentFingerprint is null");
                }

                byte[] sourceFingerprint;
                if (origin.getSourceFingerprint() != null) {
                    sourceFingerprint = origin.getSourceFingerprint();
                } else {
                    throw new CollectExPubWrongDataException("invalid CryptoHDKey: master fingerprint is null");
                }

                byte[] key = cryptoHDKey.getKey();
                if (key == null) {
                    throw new CollectExPubWrongDataException("invalid CryptoHDKey: key is null");
                }
                byte[] chainCode = cryptoHDKey.getChainCode();
                if (chainCode == null) {
                    throw new CollectExPubWrongDataException("invalid CryptoHDKey: chainCode is null");
                }

                byte[] xPubVersion = account.getXPubVersion().getVersionBytes();
                byte[] bytes = new byte[4 + 1 + 4 + 4 + 32 + 33];//version + depth + parentFingerprint + index + chainCode + key;
                byte[] index = BigInteger.valueOf(lastPathComponent.isHardened() ? 0x80000000 + lastPathComponent.getIndex() : lastPathComponent.getIndex()).toByteArray();

                System.arraycopy(xPubVersion, 0, bytes, 0, 4);
                bytes[4] = (byte) depth;
                System.arraycopy(parentFingerprint, 0, bytes, 5, 4);
                System.arraycopy(index, 0, bytes, 9, 4);
                System.arraycopy(chainCode, 0, bytes, 13, 32);
                System.arraycopy(key, 0, bytes, 45, 33);

                String xpub = new String(new B58().encodeToBytesChecked(bytes), StandardCharsets.US_ASCII);
                object.put("xpub", xpub);
                object.put("xfp", Hex.toHexString(sourceFingerprint));
                object.put("path", path);
                return object.toString();
            } else {
                throw new CollectExPubWrongDataException("invalid CryptoOutput: is not a CryptoHDKey");
            }
        } catch (IndexOutOfBoundsException e) {
            throw new CollectExPubWrongDataException("invalid CryptoOutput");
        }
    }

    private void handleSignPsbt(String hex) {
        Bundle bundle = new Bundle();
        boolean isMultisig = fragment.getPurpose() == QrScanPurpose.MULTISIG_TX;
        bundle.putString("psbt_base64", Base64.toBase64String(Hex.decode(hex)));
        bundle.putBoolean("multisig", isMultisig);
        if(isMultisig){
            bundle.putString("multisig_mode", MultiSigMode.LEGACY.name());
        }
//        fragment.navigate(R.id.action_to_psbtTxConfirmFragment, bundle);
    }

    private void decodeAndProcess(JSONObject object)
            throws InvalidTransactionException,
            CoinNotFindException,
            JSONException,
            XfpNotMatchException,
            UnknowQrCodeException, WatchWalletNotMatchException {
        logObject(object);

        String type = object.getString("type");
        switch (type) {
            case "TYPE_SIGN_TX":
                handleSignKeyStoneTx(object);
                break;
            default:
                throw new UnknowQrCodeException("unknow qrcode type " + type);
        }
    }

    private boolean checkWebAuth(JSONObject object) throws JSONException {
        JSONObject webAuth = object.optJSONObject("data");
        if (webAuth != null && webAuth.optString("type").equals("webAuth")) {
            handleWebAuth(webAuth);
            return true;
        }
        return false;
    }

    private void logObject(JSONObject object) {
        try {
            Log.w(TAG, "object = " + object.toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void handleWebAuth(JSONObject object) throws JSONException {
        String data = object.getString("data");
        Bundle bundle = new Bundle();
        bundle.putString(WEB_AUTH_DATA, data);
        bundle.putBoolean(IS_SETUP_VAULT, isSetupVault);
        if (isSetupVault) {
//            fragment.navigate(R.id.action_to_webAuthResultFragment, bundle);
        } else {
//            fragment.navigate(R.id.action_QRCodeScan_to_result, bundle);
        }
    }

    private void handleSignKeyStoneTx(JSONObject object)
            throws InvalidTransactionException,
            CoinNotFindException,
            XfpNotMatchException, WatchWalletNotMatchException {

        if (WatchWallet.getWatchWallet(getApplication())
                != WatchWallet.KEYSTONE) {
            throw new WatchWalletNotMatchException("");
        }

        checkXfp(object);
        try {
            String coinCode = object.getJSONObject("signTx")
                    .getString("coinCode");

            boolean isMainNet = Utilities.isMainNet(getApplication());
            if (!Coins.isCoinSupported(coinCode)
                    || object.getJSONObject("signTx").has("omniTx")
                    || !isMainNet) {
                throw new CoinNotFindException("not support " + coinCode);
            }
            Bundle bundle = new Bundle();
            bundle.putString(KEY_TX_DATA, object.getJSONObject("signTx").toString());
            fragment.navigate(R.id.action_to_txConfirmFragment, bundle);
        } catch (JSONException e) {
            throw new InvalidTransactionException("invalid transaction");
        }
    }

    private void checkXfp(JSONObject obj) throws XfpNotMatchException {
        String xfp = new GetMasterFingerprintCallable().call();
        if (!obj.optString("xfp").equals(xfp)) {
            throw new XfpNotMatchException("xfp not match");
        }
    }

    public static class Factory extends ViewModelProvider.NewInstanceFactory {
        @NonNull
        private final Application mApplication;

        private final boolean mIsSetupVault;

        public Factory(@NonNull Application application, boolean isSetupVault) {
            mApplication = application;
            mIsSetupVault = isSetupVault;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            //noinspection unchecked
            return (T) new QrScanViewModel(mApplication, mIsSetupVault);
        }
    }
}
