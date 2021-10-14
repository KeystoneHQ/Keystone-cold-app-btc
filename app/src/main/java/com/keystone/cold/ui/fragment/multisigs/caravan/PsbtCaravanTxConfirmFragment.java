package com.keystone.cold.ui.fragment.multisigs.caravan;

import static com.keystone.cold.ui.fragment.main.PsbtBroadcastTxFragment.KEY_MULTISIG_MODE;
import static com.keystone.cold.ui.fragment.main.PsbtBroadcastTxFragment.KEY_TXID;

import android.os.Bundle;

import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.cold.R;
import com.keystone.cold.ui.fragment.multisigs.legacy.PsbtLegacyTxConfirmFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.viewmodel.exceptions.NoMatchedMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.keystone.cold.viewmodel.multisigs.PsbtCaravanConfirmViewModel;

public class PsbtCaravanTxConfirmFragment extends PsbtLegacyTxConfirmFragment {

    @Override
    protected void onSignSuccess() {
        Bundle data = new Bundle();
        data.putString(KEY_TXID, txEntity.getTxId());
        data.putString(KEY_MULTISIG_MODE, MultiSigMode.CARAVAN.name());
        navigate(R.id.action_to_psbt_broadcast, data);
    }

    @Override
    protected void initViewModel() {
        psbtLegacyConfirmViewModel = ViewModelProviders.of(this).get(PsbtCaravanConfirmViewModel.class);
    }

    @Override
    protected void observeException() {
        psbtLegacyConfirmViewModel.getParseTxException().observe(this, ex -> {
            if (ex != null) {
                ex.printStackTrace();
                progressModalDialog.dismiss();
                String title = getString(R.string.electrum_decode_txn_fail);
                String errorMessage = getString(R.string.incorrect_tx_data);
                String buttonText = getString(R.string.confirm);
                if (ex instanceof WatchWalletNotMatchException) {
                    errorMessage = getString(R.string.master_pubkey_not_match);
                }
                if (ex instanceof InvalidTransactionException) {
                    InvalidTransactionException e = (InvalidTransactionException) ex;
                    if (e.getErrorCode() == InvalidTransactionException.IS_NOTMULTISIG_TX) {
                        title = getString(R.string.open_int_siglesig_wallet);
                        errorMessage = getString(R.string.open_int_siglesig_wallet_hint);
                    }
                    buttonText = getString(R.string.know);
                }
                if (ex instanceof NoMatchedMultisigWalletException) {
                    title = getString(R.string.no_matched_wallet);
                    errorMessage = getString(R.string.no_matched_wallet_hint);
                    buttonText = getString(R.string.know);

                }
                ModalDialog.showCommonModal(mActivity,
                        title,
                        errorMessage,
                        buttonText, null);
                navigateUp();
            }
        });
    }
}
