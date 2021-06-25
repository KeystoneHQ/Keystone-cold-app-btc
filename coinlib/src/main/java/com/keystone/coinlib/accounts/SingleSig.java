package com.keystone.coinlib.accounts;

import java.util.List;
import java.util.stream.Collectors;

public class SingleSig {
    public static final Account P2WPKH = Account.SINGLE_P2WPKH;
    public static final Account P2SH_P2WPKH = Account.SINGLE_P2SH_P2WPKH;
    public static final Account P2PKH = Account.SINGLE_P2PKH;

    public static final Account P2WPKH_TEST = Account.SINGLE_P2WPKH_TEST;
    public static final Account P2SH_P2WPKH_TEST = Account.SINGLE_P2SH_P2WPKH_TEST;
    public static final Account P2PKH_TEST = Account.SINGLE_P2PKH_TEST;

    public static List<Account> ofPath(String path) {
        return Account.ofPath(path).stream().filter(account -> !account.isMultiSig()).collect(Collectors.toList());
    }

    public static List<Account> ofPath(String path, boolean isMainNet) {
        return SingleSig.ofPath(path).stream().filter(account -> account.isMainNet() == isMainNet).collect(Collectors.toList());
    }
}
