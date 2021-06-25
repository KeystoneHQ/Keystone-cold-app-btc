package com.keystone.cold.ui.fragment.multisigs;

import com.keystone.coinlib.utils.Account;
import com.keystone.cold.R;
import com.keystone.cold.ui.fragment.setting.ListPreferenceFragment;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.keystone.cold.viewmodel.WatchWallet;

import static com.keystone.cold.ui.fragment.setting.MainPreferenceFragment.SETTING_ADDRESS_FORMAT;
import static com.keystone.cold.viewmodel.WatchWallet.getWatchWallet;

public class MultiSigPreferenceFragment extends ListPreferenceFragment {
    private static final String SETTING_MULTISIG_MODE = "setting_multisig_mode";

    @Override
    protected int getEntries() {
        return R.array.multisig_mode_list;
    }

    @Override
    protected int getValues() {
        return R.array.multisig_mode_list_values;
    }

    @Override
    protected String getKey() {
        return SETTING_MULTISIG_MODE;
    }

    @Override
    protected String defaultValue() {
        return MultiSigMode.LEGACY.getModeId();
    }

    @Override
    public void onSelect(int position) {
        String old = value;
        value = String.valueOf(position);
        if (!old.equals(value)) {
            setMultisigMode();
        }
    }

    private void setMultisigMode() {
        if (prefs.edit().putString(SETTING_MULTISIG_MODE, value).commit()) {
            WatchWallet wallet = getWatchWallet(mActivity);
            if (wallet.supportNativeSegwit()) {
                prefs.edit().putString(SETTING_ADDRESS_FORMAT, Account.P2WPKH.getType()).apply();
            } else if (wallet.supportNestedSegwit()) {
                prefs.edit().putString(SETTING_ADDRESS_FORMAT, Account.P2SH_P2WPKH.getType()).apply();
            }
        }
    }
}
