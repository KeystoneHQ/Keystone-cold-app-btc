package com.keystone.coinlib.accounts;

import java.util.ArrayList;
import java.util.List;

public enum Account {
    SINGLE_P2PKH("m/44'/0'/0'", "P2PKH", false, true),
    SINGLE_P2SH_P2WPKH("m/49'/0'/0'", "P2SH-P2WPKH", false, true),
    SINGLE_P2WPKH("m/84'/0'/0'", "P2WPKH", false, true),

    SINGLE_P2PKH_TEST("m/44'/1'/0'", "P2PKH", false, false),
    SINGLE_P2SH_P2WPKH_TEST("m/49'/1'/0'", "P2SH-P2WPKH", false, false),
    SINGLE_P2WPKH_TEST("m/84'/1'/0'", "P2WPKH", false, false),

    MULTI_P2WSH("m/48'/0'/0'/2'", "P2WSH", true, true),
    MULTI_P2SH_P2WSH("m/48'/0'/0'/1'", "P2SH-P2WSH", true, true),
    MULTI_P2SH("m/45'", "P2SH", true, true),

    MULTI_P2WSH_TEST("m/48'/1'/0'/2'", "P2WSH", true, false),
    MULTI_P2SH_P2WSH_TEST("m/48'/1'/0'/1'", "P2SH-P2WSH", true, false),
    MULTI_P2SH_TEST("m/45'", "P2SH", true, false),

    MULTI_CASA("m", "P2SH-P2WSH", true, true);

    private final String path;
    private final String script;
    private final boolean isMultiSig;
    private final boolean isMainNet;

    Account(String path, String script, boolean isMultiSig, boolean isMainNet) {
        this.path = path;
        this.script = script;
        this.isMultiSig = isMultiSig;
        this.isMainNet = isMainNet;
    }

    public String getPath() {
        return path;
    }

    public String getScript() {
        return script;
    }

    public boolean isMultiSig() {
        return isMultiSig;
    }

    public boolean isMainNet() {
        return isMainNet;
    }

    protected static List<Account> ofPath(String path) {
        List<Account> list = new ArrayList<>();
        for (Account value : Account.values()) {
            if (value.getPath().equalsIgnoreCase(path)) {
                list.add(value);
            }
        }
        return list;
    }

    protected static List<Account> ofPrefix(String xPubVersionName) {
        List<Account> list = new ArrayList<>();
        for (Account value : Account.values()) {
            if (value.getXPubVersion().getName().equals(xPubVersionName)) {
                list.add(value);
            }
        }
        return list;
    }

    protected static List<Account> ofScript(String script) {
        List<Account> list = new ArrayList<>();
        for (Account value : Account.values()) {
            if (value.getScript().equals(script)) {
                list.add(value);
            }
        }
        return list;
    }


    public ExtendedPublicKeyVersion getXPubVersion() {
        switch (this) {
            case SINGLE_P2SH_P2WPKH:
                return ExtendedPublicKeyVersion.ypub;
            case SINGLE_P2WPKH:
                return ExtendedPublicKeyVersion.zpub;
            case SINGLE_P2PKH_TEST:
            case MULTI_P2SH_TEST:
                return ExtendedPublicKeyVersion.tpub;
            case SINGLE_P2SH_P2WPKH_TEST:
                return ExtendedPublicKeyVersion.upub;
            case SINGLE_P2WPKH_TEST:
                return ExtendedPublicKeyVersion.vpub;

            case MULTI_P2SH_P2WSH:
                return ExtendedPublicKeyVersion.Ypub;
            case MULTI_P2WSH:
                return ExtendedPublicKeyVersion.Zpub;
            case MULTI_P2SH_P2WSH_TEST:
                return ExtendedPublicKeyVersion.Upub;
            case MULTI_P2WSH_TEST:
                return ExtendedPublicKeyVersion.Vpub;
            default:
                /// SINGLE_P2PKH, MULTI_P2SH, MULTI_CASA
                return ExtendedPublicKeyVersion.xpub;
        }
    }
}
