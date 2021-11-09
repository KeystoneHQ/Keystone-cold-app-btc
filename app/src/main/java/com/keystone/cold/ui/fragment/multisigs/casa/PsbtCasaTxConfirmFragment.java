package com.keystone.cold.ui.fragment.multisigs.casa;

import static com.keystone.cold.callables.FingerprintPolicyCallable.READ;
import static com.keystone.cold.callables.FingerprintPolicyCallable.TYPE_SIGN_TX;
import static com.keystone.cold.ui.fragment.main.PsbtBroadcastTxFragment.KEY_MULTISIG_MODE;
import static com.keystone.cold.ui.fragment.main.PsbtBroadcastTxFragment.KEY_TXID;
import static com.keystone.cold.ui.fragment.setup.PreImportFragment.ACTION;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;

import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.exception.InvalidTransactionException;
import com.keystone.coinlib.utils.Coins;
import com.keystone.cold.R;
import com.keystone.cold.Utilities;
import com.keystone.cold.callables.FingerprintPolicyCallable;
import com.keystone.cold.config.FeatureFlags;
import com.keystone.cold.databinding.PsbtTxConfirmFragmentBinding;
import com.keystone.cold.db.entity.CasaSignature;
import com.keystone.cold.encryptioncore.utils.ByteFormatter;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.TransactionItem;
import com.keystone.cold.ui.fragment.main.TransactionItemAdapter;
import com.keystone.cold.ui.fragment.setup.PreImportFragment;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.ui.modal.ProgressModalDialog;
import com.keystone.cold.ui.modal.SigningDialog;
import com.keystone.cold.ui.views.AuthenticateModal;
import com.keystone.cold.ui.views.OnMultiClickListener;
import com.keystone.cold.util.KeyStoreUtil;
import com.keystone.cold.viewmodel.ParsePsbtViewModel;
import com.keystone.cold.viewmodel.WatchWallet;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.keystone.cold.viewmodel.multisigs.PsbtCasaConfirmViewModel;
import com.keystone.cold.viewmodel.multisigs.exceptions.NotMyCasaKeyException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class PsbtCasaTxConfirmFragment extends BaseFragment<PsbtTxConfirmFragmentBinding> {
    private PsbtCasaConfirmViewModel psbtCasaTxConfirmViewModel;
    private SigningDialog signingDialog;
    private boolean signed;
    private CasaSignature casaSignature;
    private ProgressModalDialog progressModalDialog;
    private final Runnable forgetPassword = () -> {
        Bundle bundle = new Bundle();
        bundle.putString(ACTION, PreImportFragment.ACTION_RESET_PWD);
        navigate(R.id.action_to_preImportFragment, bundle);
    };

    @Override
    protected int setView() {
        return R.layout.psbt_tx_confirm_fragment;
    }

    @Override
    protected void init(View view) {
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        mBinding.txDetail.txIdInfo.setVisibility(View.GONE);
        mBinding.txDetail.export.setVisibility(View.GONE);
        mBinding.txDetail.qr.setVisibility(View.GONE);

        String walletName = WatchWallet.getWatchWallet(mActivity)
                .getWalletName(mActivity);
        mBinding.txDetail.watchWallet.setText(walletName);

        mBinding.sign.setOnClickListener(new OnMultiClickListener() {
            @Override
            public void onMultiClick(View v) {
                handleSign();
            }
        });
    }


    @Override
    protected void initData(Bundle savedInstanceState) {
        psbtCasaTxConfirmViewModel = ViewModelProviders.of(this).get(PsbtCasaConfirmViewModel.class);
        progressModalDialog = new ProgressModalDialog();
        progressModalDialog.show(mActivity.getSupportFragmentManager(), "");
        subscribeTx();
        Bundle bundle = requireArguments();
        psbtCasaTxConfirmViewModel.setMainNet(bundle.getBoolean("isMainNet"));
        String signTx = bundle.getString("signTx");
        if (signTx != null) {
            psbtCasaTxConfirmViewModel.generateTx(signTx);
        } else {
            String psbtBase64 = bundle.getString("psbt_base64");
            psbtCasaTxConfirmViewModel.handleTx(psbtBase64);
        }
    }

    private void subscribeTx() {
        observeEntity();
        observeException();
    }

    private void observeEntity() {
        psbtCasaTxConfirmViewModel.getObservableCasaSignature().observe(this, casaSignature -> {
            if (casaSignature != null) {
                mBinding.txDetail.network.setVisibility(View.VISIBLE);
                if (psbtCasaTxConfirmViewModel.isMainNet()) {
                    mBinding.txDetail.networkText.setText("Mainnet");
                } else {
                    mBinding.txDetail.networkText.setText("Testnet");
                }
                progressModalDialog.dismiss();
                this.casaSignature = casaSignature;
                mBinding.setTx(casaSignature);
                refreshUI();
            }
        });
    }

    private void observeException() {
        psbtCasaTxConfirmViewModel.getParseTxException().observe(this, ex -> {
            if (ex != null) {
                ex.printStackTrace();
                progressModalDialog.dismiss();
                String title = getString(R.string.electrum_decode_txn_fail);
                String errorMessage = getString(R.string.incorrect_tx_data);
                String buttonText = getString(R.string.confirm);
                if (ex instanceof WatchWalletNotMatchException || ex instanceof NotMyCasaKeyException) {
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
                ModalDialog.showCommonModal(mActivity,
                        title,
                        errorMessage,
                        buttonText, null);
                navigateUp();
            }
        });
    }

    private void refreshUI() {
        refreshFromList();
        refreshReceiveList();
        refreshSignStatus();
        checkBtcFee();
    }

    private void refreshFromList() {
        mBinding.txDetail.arrowDown.setVisibility(View.GONE);
    }

    private void refreshReceiveList() {
        String to = casaSignature.getTo();
        List<TransactionItem> items = new ArrayList<>();
        try {
            JSONArray outputs = new JSONArray(to);
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject output = outputs.getJSONObject(i);
                items.add(new TransactionItem(i,
                        output.getLong("value"),
                        output.getString("address"),
                        casaSignature.getCoinCode(), null));
            }
        } catch (JSONException e) {
            return;
        }
        TransactionItemAdapter adapter
                = new TransactionItemAdapter(mActivity,
                TransactionItem.ItemType.OUTPUT);
        adapter.setItems(items);
        mBinding.txDetail.toList.setVisibility(View.VISIBLE);
        mBinding.txDetail.toList.setAdapter(adapter);
    }

    private void refreshSignStatus() {
        if (!TextUtils.isEmpty(casaSignature.getSignStatus())) {
            mBinding.txDetail.txSignStatus.setVisibility(View.VISIBLE);
            String signStatus = casaSignature.getSignStatus();

            String[] splits = signStatus.split("-");
            int sigNumber = Integer.parseInt(splits[0]);
            int reqSigNumber = Integer.parseInt(splits[1]);

            String text;
            if (sigNumber == 0) {
                text = getString(R.string.unsigned);
            } else if (sigNumber < reqSigNumber) {
                text = getString(R.string.partial_signed);
            } else {
                text = getString(R.string.signed);
                signed = true;
            }

            mBinding.txDetail.signStatus.setText(text);
        } else {
            mBinding.txDetail.txSource.setVisibility(View.VISIBLE);
        }
    }

    private void checkBtcFee() {
        if (casaSignature.getCoinCode().equals(Coins.BTC.coinCode())) {
            try {
                Number parse = NumberFormat.getInstance().parse(casaSignature.getFee().split(" ")[0]);
                if (parse != null && parse.doubleValue() > 0.01) {
                    mBinding.txDetail.fee.setTextColor(Color.RED);
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleSign() {
        if (signed) {
            ModalDialog.showCommonModal(mActivity, getString(R.string.broadcast_tx),
                    getString(R.string.multisig_already_signed), getString(R.string.know),
                    null);
            return;
        }

        boolean fingerprintSignEnable = new FingerprintPolicyCallable(READ, TYPE_SIGN_TX).call();
        if (casaSignature != null) {
            if (FeatureFlags.ENABLE_WHITE_LIST) {
                if (isAddressInWhiteList()) {
                    AuthenticateModal.show(mActivity,
                            getString(R.string.password_modal_title),
                            "",
                            fingerprintSignEnable,
                            signWithVerifyInfo(), forgetPassword);
                } else {
                    Utilities.alert(mActivity, getString(R.string.hint),
                            getString(R.string.not_in_whitelist_reject),
                            getString(R.string.confirm),
                            () -> navigate(R.id.action_to_home));
                }

            } else {
                AuthenticateModal.show(mActivity,
                        getString(R.string.password_modal_title),
                        "",
                        fingerprintSignEnable,
                        signWithVerifyInfo(), forgetPassword);
            }
        } else {
            navigate(R.id.action_to_home);
        }
    }

    private boolean isAddressInWhiteList() {
        String to = casaSignature.getTo();
        String encryptedAddress = ByteFormatter.bytes2hex(
                new KeyStoreUtil().encrypt(to.getBytes(StandardCharsets.UTF_8)));
        return psbtCasaTxConfirmViewModel.isAddressInWhiteList(encryptedAddress);
    }

    protected AuthenticateModal.OnVerify signWithVerifyInfo() {
        return token -> {
            psbtCasaTxConfirmViewModel.setToken(token);
            psbtCasaTxConfirmViewModel.handleSignPsbt(
                    requireArguments().getString("psbt_base64"));
            subscribeSignState();
        };
    }

    protected void subscribeSignState() {
        psbtCasaTxConfirmViewModel.getSignState().observe(this, s -> {
            if (ParsePsbtViewModel.STATE_SIGNING.equals(s)) {
                signingDialog = SigningDialog.newInstance();
                signingDialog.show(mActivity.getSupportFragmentManager(), "");
            } else if (ParsePsbtViewModel.STATE_SIGN_SUCCESS.equals(s)) {
                if (signingDialog != null) {
                    signingDialog.setState(SigningDialog.STATE_SUCCESS);
                }
                new Handler().postDelayed(() -> {
                    if (signingDialog != null) {
                        signingDialog.dismiss();
                    }
                    signingDialog = null;
                    onSignSuccess();
                    psbtCasaTxConfirmViewModel.getSignState().removeObservers(this);
                    psbtCasaTxConfirmViewModel.getSignState().setValue(ParsePsbtViewModel.STATE_NONE);
                }, 500);
            } else if (ParsePsbtViewModel.STATE_SIGN_FAIL.equals(s)) {
                if (signingDialog == null) {
                    signingDialog = SigningDialog.newInstance();
                    signingDialog.show(mActivity.getSupportFragmentManager(), "");
                }
                new Handler().postDelayed(() -> signingDialog.setState(SigningDialog.STATE_FAIL), 1000);
                new Handler().postDelayed(() -> {
                    if (signingDialog != null) {
                        signingDialog.dismiss();
                    }
                    signingDialog = null;
                    psbtCasaTxConfirmViewModel.getSignState().removeObservers(this);
                    psbtCasaTxConfirmViewModel.getSignState().setValue(ParsePsbtViewModel.STATE_NONE);
                }, 2000);
            }
        });
    }

    protected void onSignSuccess() {
        Bundle data = new Bundle();
        data.putString(KEY_TXID, String.valueOf(casaSignature.getId()));
        data.putString(KEY_MULTISIG_MODE, MultiSigMode.CASA.name());
        navigate(R.id.action_to_psbt_broadcast, data);
    }
}