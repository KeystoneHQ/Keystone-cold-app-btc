/*
 *
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
 *
 */

package com.keystone.cold.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.MultiSig;
import com.keystone.coinlib.utils.B58;
import com.keystone.cold.AppExecutors;
import com.keystone.cold.update.utils.Storage;
import com.keystone.cold.viewmodel.exceptions.CollectExPubException;
import com.sparrowwallet.hummingbird.registry.CryptoAccount;
import com.sparrowwallet.hummingbird.registry.CryptoCoinInfo;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;
import com.sparrowwallet.hummingbird.registry.CryptoKeypath;
import com.sparrowwallet.hummingbird.registry.CryptoOutput;
import com.sparrowwallet.hummingbird.registry.PathComponent;
import com.sparrowwallet.hummingbird.registry.ScriptExpression;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.DataItem;

public class CollectXpubViewModel extends AndroidViewModel {

    private static final Pattern xpubFileName = Pattern.compile("(.*)[0-9a-fA-F]{8}(.*).json$");

    private List<XpubInfo> xpubInfos;
    public boolean startCollect;

    public CollectXpubViewModel(@NonNull Application application) {
        super(application);
    }


    public void initXpubInfo(int total) {
        xpubInfos = new ArrayList<>(total);
    }

    public List<XpubInfo> getXpubInfo() {
        return xpubInfos;
    }


    public static class XpubInfo {
        public int index;
        public String xfp;
        public String xpub;

        public XpubInfo(int index, String fingerprint, String xpub) {
            this.index = index;
            this.xfp = fingerprint;
            this.xpub = xpub;
        }
    }

    public LiveData<List<File>> loadXpubFile() {
        MutableLiveData<List<File>> result = new MutableLiveData<>();
        AppExecutors.getInstance().diskIO().execute(() -> {
            List<File> fileList = new ArrayList<>();
            Storage storage = Storage.createByEnvironment();
            if (storage != null) {
                File[] files = storage.getExternalDir().listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (xpubFileName.matcher(f.getName()).matches()
                                && !f.getName().startsWith(".")) {
                            fileList.add(f);
                        }
                    }
                }
            }
            fileList.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
            result.postValue(fileList);
        });
        return result;
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

    public CryptoOutput decodeCryptoOutput(String cborPayload) {
        try {
            List<DataItem> dataItems = CborDecoder.decode(Hex.decode(cborPayload));
            return CryptoOutput.fromCbor(dataItems.get(0));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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

    public String handleCollectExPubWithCryptoOutput(CryptoOutput cryptoOutput) throws CollectExPubException, JSONException {
        try {
            CryptoHDKey cryptoHDKey = cryptoOutput.getHdKey();
            if (cryptoHDKey != null) {
                JSONObject object = new JSONObject();
                CryptoKeypath origin = cryptoHDKey.getOrigin();
                if (origin == null) {
                    throw new CollectExPubException("invalid CryptoHDKey: origin is null");
                }

                String path = "m/" + origin.getPath();
                if (path == null) {
                    throw new CollectExPubException("invalid CryptoHDKey: origin path is null");
                }
                if (!MultiSig.isValidPath(path)) {
                    throw new CollectExPubException("invalid CryptoHDKey: origin path is invalid: " + path);
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
                    throw new CollectExPubException("invalid CryptoHDKey: parentFingerprint is null");
                }

                byte[] sourceFingerprint;
                if (origin.getSourceFingerprint() != null) {
                    sourceFingerprint = origin.getSourceFingerprint();
                } else {
                    throw new CollectExPubException("invalid CryptoHDKey: master fingerprint is null");
                }

                byte[] key = cryptoHDKey.getKey();
                if (key == null) {
                    throw new CollectExPubException("invalid CryptoHDKey: key is null");
                }
                byte[] chainCode = cryptoHDKey.getChainCode();
                if (chainCode == null) {
                    throw new CollectExPubException("invalid CryptoHDKey: chainCode is null");
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
                throw new CollectExPubException("invalid CryptoOutput: is not a CryptoHDKey");
            }
        } catch (IndexOutOfBoundsException e) {
            throw new CollectExPubException("invalid CryptoOutput");
        }
    }
}
