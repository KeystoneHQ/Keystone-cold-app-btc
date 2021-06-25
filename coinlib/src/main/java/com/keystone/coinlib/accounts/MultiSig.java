package com.keystone.coinlib.accounts;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MultiSig {
    public static Account CASA = Account.MULTI_CASA;

    public static Account P2SH = Account.MULTI_P2SH;
    public static Account P2SH_P2WSH = Account.MULTI_P2SH_P2WSH;
    public static Account P2WSH = Account.MULTI_P2WSH;

    public static Account P2SH_TEST = Account.MULTI_P2SH_TEST;
    public static Account P2SH_P2WSH_TEST = Account.MULTI_P2SH_P2WSH_TEST;
    public static Account P2WSH_TEST = Account.MULTI_P2WSH_TEST;

    public static List<Account> values() {
        return Arrays.stream(Account.values()).filter(Account::isMultiSig).collect(Collectors.toList());
    }

    public static List<Account> ofPath(String path) {
        List<Account> list = Account.ofPath(path).stream().filter(Account::isMultiSig).collect(Collectors.toList());
        if (list.size() == 0) return Collections.singletonList(MultiSig.P2WSH);
        return list;
    }

    public static List<Account> ofPath(String path, boolean isMainNet) {
        return MultiSig.ofPath(path).stream().filter(account -> account.isMainNet() == isMainNet).collect(Collectors.toList());
    }

    public static List<Account> ofPrefix(String xPubVersionName) {
        return Account.ofPrefix(xPubVersionName).stream().filter(Account::isMultiSig).collect(Collectors.toList());
    }

    public static List<Account> ofPrefix(String xPubVersionName, boolean isMainNet) {
        return Account.ofPrefix(xPubVersionName).stream().filter(account -> account.isMainNet() == isMainNet).collect(Collectors.toList());
    }

    public static List<Account> ofScript(String script) {
        return Account.ofScript(script).stream().filter(Account::isMultiSig).collect(Collectors.toList());
    }

    public static List<Account> ofScript(String script, boolean isMainNet) {
        return Account.ofScript(script).stream().filter(account -> account.isMainNet() == isMainNet).collect(Collectors.toList());
    }

    public static boolean isValidPath(String path) {
        List<Account> list = Account.ofPath(path).stream().filter(Account::isMultiSig).collect(Collectors.toList());
        if (list.size() == 0) return false;
        return true;
    }
}
