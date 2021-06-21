package com.keystone.coinlib.utils;

import com.keystone.coinlib.ExtendPubkeyFormat;

// this will be deprecated, please refer to com.keystone.coinlib.accounts.SingleSig
public enum Account {
    P2PKH("M/44'/0'/0'", "P2PKH"),
    P2SH_P2WPKH("M/49'/0'/0'", "P2SH-P2WPKH"),
    P2WPKH("M/84'/0'/0'", "P2WPKH"),

    P2PKH_TESTNET("M/44'/1'/0'", "P2PKH"),
    P2SH_P2WPKH_TESTNET("M/49'/1'/0'", "P2SH-P2WPKH"),
    P2WPKH_TESTNET("M/84'/1'/0'", "P2WPKH");

    private final String path;
    private final String type;

    Account(String path, String type) {

        this.path = path;
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public static Account ofPath(String path) {
        for (Account value : Account.values()) {
            if (value.getPath().equalsIgnoreCase(path)) {
                return value;
            }
        }
        return P2WPKH;
    }

    public String getXpubPrefix() {
        switch (this) {
            case P2PKH:
                return "xpub";
            case P2SH_P2WPKH:
                return "ypub";
            case P2WPKH:
                return "zpub";
            case P2PKH_TESTNET:
                return "tpub";
            case P2SH_P2WPKH_TESTNET:
                return "upub";
            case P2WPKH_TESTNET:
                return "vpub";
        }
        return "xpub";
    }

    public ExtendPubkeyFormat getXpubFormat() {
        switch (this) {
            case P2PKH:
                return ExtendPubkeyFormat.xpub;
            case P2SH_P2WPKH:
                return ExtendPubkeyFormat.ypub;
            case P2WPKH:
                return ExtendPubkeyFormat.zpub;
            case P2PKH_TESTNET:
                return ExtendPubkeyFormat.tpub;
            case P2SH_P2WPKH_TESTNET:
                return ExtendPubkeyFormat.upub;
            case P2WPKH_TESTNET:
                return ExtendPubkeyFormat.vpub;
        }
        return ExtendPubkeyFormat.xpub;
    }

    public boolean isMainNet() {
        return this == P2PKH || this == P2SH_P2WPKH || this == P2WPKH;
    }
}
