package com.keystone.cold.util;

import com.keystone.coinlib.accounts.Account;
import com.keystone.coinlib.accounts.ExtendedPublicKey;
import com.sparrowwallet.hummingbird.registry.CryptoCoinInfo;
import com.sparrowwallet.hummingbird.registry.CryptoHDKey;
import com.sparrowwallet.hummingbird.registry.CryptoKeypath;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;
import com.sparrowwallet.hummingbird.registry.PathComponent;
import com.sparrowwallet.hummingbird.registry.ScriptExpression;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class URRegistryHelper {
    private static List<PathComponent> fromAccountToPathComponent(Account account) {
        String path = account.getPath();
        String[] rawPaths = path.split("/");
        return Arrays.stream(rawPaths).filter(rp -> !rp.equalsIgnoreCase("m")).map(rp -> {
            if (rp.contains("'")) {
                return new PathComponent(Integer.parseInt(rp.replace("'", "")), true);
            } else {
                return new PathComponent(Integer.parseInt(rp), false);
            }
        }).collect(Collectors.toList());
    }

    private static List<ScriptExpression> fromAccountToScriptExpressions(Account account) {
        String script = account.getScript();
        String[] rawScripts = script.split("-");
        return Arrays.stream(rawScripts).map(rs -> {
            switch (rs) {
                case "P2SH":
                    return ScriptExpression.SCRIPT_HASH;
                case "P2WSH":
                    return ScriptExpression.WITNESS_SCRIPT_HASH;
                case "P2WPKH":
                    return ScriptExpression.WITNESS_PUBLIC_KEY_HASH;
                case "P2PKH":
                    return ScriptExpression.PUBLIC_KEY_HASH;
            }
            throw new InvalidParameterException("invalid script" + rs);
        }).collect(Collectors.toList());
    }

    public static CryptoHDKey generateCryptoHDKey(Account account, byte[] masterFingerprint, String xPub) {
        return generateCryptoHDKey(account, masterFingerprint, xPub, true);
    }

    public static CryptoHDKey generateCryptoHDKey(Account account, byte[] masterFingerprint, String xPub, boolean isMainNet) {
        ExtendedPublicKey extendedPublicKey = new ExtendedPublicKey(xPub);
        List<PathComponent> pathComponents = fromAccountToPathComponent(account);
        CryptoKeypath origin = new CryptoKeypath(pathComponents, masterFingerprint, (int) extendedPublicKey.getDepth());

        return new CryptoHDKey(false, extendedPublicKey.getKey(),
                extendedPublicKey.getChainCode(),
                new CryptoCoinInfo(0, isMainNet ? 0 : 1),
                origin, null,
                extendedPublicKey.getParentFingerprint());
    }

    public static CryptoPSBT generateCryptoPSBT(byte[] psbt) {
        return new CryptoPSBT(psbt);
    }
}
