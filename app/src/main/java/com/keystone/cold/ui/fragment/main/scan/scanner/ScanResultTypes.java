package com.keystone.cold.ui.fragment.main.scan.scanner;


import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.registry.CryptoAccount;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.spongycastle.util.encoders.Hex;

import java.util.List;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

public enum ScanResultTypes {
    PLAIN_TEXT,
    UR_BYTES,
    UR_CRYPTO_PSBT,
    CRYPTO_ACCOUNT;


    public boolean isType(String text) {
        return true;
    }

    public boolean isType(UR ur) {
        try {
            Object decodeResult = ur.decodeFromRegistry();
            switch (this) {
                case UR_CRYPTO_PSBT:
                    return decodeResult instanceof CryptoPSBT;
                case UR_BYTES:
                    return decodeResult instanceof byte[];
                case CRYPTO_ACCOUNT:
                    return decodeResult instanceof CryptoAccount;
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public Object resolveURHex(String hex) {
        try {
            byte[] cborPayload = Hex.decode(hex);
            List<DataItem> items = CborDecoder.decode(cborPayload);
            DataItem dataItem = items.get(0);
            switch (this) {
                case UR_CRYPTO_PSBT:
                    return CryptoPSBT.fromCbor(dataItem);
                case UR_BYTES:
                    return ((ByteString) dataItem).getBytes();
                default:
                    return null;
            }

        } catch (CborException e) {
            e.printStackTrace();
            return null;
        }
    }
}
