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

 import static com.keystone.cold.ui.fragment.setting.MainPreferenceFragment.SETTING_CHOOSE_WATCH_WALLET;

 import android.content.Context;

 import com.keystone.cold.R;
 import com.keystone.cold.Utilities;

 public enum WatchWallet {
     KEYSTONE("0"),
     BLUE("1"),
     ELECTRUM("2"),
     BTCPAY("3"),
     SPARROW("4"),
     WASABI("5"),
     GENERIC("100");

     public static final String ELECTRUM_SIGN_ID = "electrum_sign_id";
     public static final String WASABI_SIGN_ID = "wasabi_sign_id";
     public static final String BLUE_WALLET_SIGN_ID = "blue_wallet_sign_id";
     public static final String BTCPAY_SIGN_ID = "blue_wallet_sign_id";
     public static final String SPARROW_SIGN_ID = "sparrow_wallet_sign_id";
     public static final String GENERIC_WALLET_SIGN_ID = "generic_wallet_sign_id";
     public static final String PSBT_MULTISIG_SIGN_ID = "PSBT_MULTISIG";

     private final String walletId;

     WatchWallet(String walletId) {
         this.walletId = walletId;
     }

     public String getWalletId() {
         return walletId;
     }

     public String getWalletName(Context context) {
         String[] wallets = context.getResources().getStringArray(R.array.watch_wallet_list);
         if (walletId.equals(GENERIC.getWalletId())) {
             return wallets[wallets.length - 1];
         } else {
             return wallets[Integer.parseInt(walletId)];
         }
     }

     public static WatchWallet getWatchWallet(Context context) {
         String wallet = Utilities.getPrefs(context)
                 .getString(SETTING_CHOOSE_WATCH_WALLET, KEYSTONE.getWalletId());
         return getWatchWalletById(wallet);
     }

     public static WatchWallet getWatchWalletById(String walletId) {
         WatchWallet selectWatchWallet = KEYSTONE;
         for (WatchWallet watchWallet : WatchWallet.values()) {
             if (watchWallet.getWalletId().equals(walletId)) {
                 selectWatchWallet = watchWallet;
                 break;
             }
         }
         return selectWatchWallet;
     }

     public boolean supportPsbt() {
         switch (this) {
             case GENERIC:
             case SPARROW:
             case BLUE:
             case BTCPAY:
             case WASABI:
                 return true;
             default:
                 return false;
         }
     }

     public boolean supportBc32QrCode() {
         switch (this) {
             case GENERIC:
             case SPARROW:
             case KEYSTONE:
             case BTCPAY:
             case BLUE:
                 return true;
             default:
                 return false;
         }
     }

     public boolean supportSdcard() {
         switch (this) {
             case ELECTRUM:
             case GENERIC:
             case SPARROW:
             case BTCPAY:
             case WASABI:
                 return true;
             default:
                 return false;
         }
     }

     public String getSignId() {
         switch (this) {
             case BLUE:
                 return BLUE_WALLET_SIGN_ID;
             case WASABI:
                 return WASABI_SIGN_ID;
             case GENERIC:
                 return GENERIC_WALLET_SIGN_ID;
             case ELECTRUM:
                 return ELECTRUM_SIGN_ID;
             case BTCPAY:
                 return BTCPAY_SIGN_ID;
             case SPARROW:
                 return SPARROW_SIGN_ID;
         }
         return "";
     }

     public boolean supportSwitchAccount() {
         switch (this) {
             case GENERIC:
             case ELECTRUM:
             case SPARROW:
                 return true;
             default:
                 return false;
         }
     }

     public boolean supportNativeSegwit() {
         switch (this) {
             case GENERIC:
             case SPARROW:
             case BTCPAY:
             case BLUE:
             case ELECTRUM:
             case WASABI:
                 return true;
         }
         return false;
     }

     public boolean supportNestedSegwit() {
         switch (this) {
             case KEYSTONE:
             case ELECTRUM:
             case GENERIC:
             case SPARROW:
                 return true;
         }
         return false;
     }

     public boolean supportTestnet() {
         switch (this) {
             case ELECTRUM:
             case BTCPAY:
             case GENERIC:
             case SPARROW:
                 return true;
         }
         return false;
     }

     public boolean supportUR2() {
         switch (this) {
             case GENERIC:
             case SPARROW:
                 return true;
         }
         return false;
     }
 }