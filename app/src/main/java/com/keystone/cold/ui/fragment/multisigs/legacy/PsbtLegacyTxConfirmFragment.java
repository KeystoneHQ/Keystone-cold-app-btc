package com.keystone.cold.ui.fragment.multisigs.legacy;

import static com.keystone.cold.callables.FingerprintPolicyCallable.READ;
import static com.keystone.cold.callables.FingerprintPolicyCallable.TYPE_SIGN_TX;
import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.NORMAL;
import static com.keystone.cold.ui.fragment.main.FeeAttackChecking.FeeAttackCheckingResult.SAME_OUTPUTS;
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
import com.keystone.cold.db.entity.TxEntity;
import com.keystone.cold.encryptioncore.utils.ByteFormatter;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.FeeAttackChecking;
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
import com.keystone.cold.viewmodel.exceptions.NoMatchedMultisigWalletException;
import com.keystone.cold.viewmodel.exceptions.WatchWalletNotMatchException;
import com.keystone.cold.viewmodel.multisigs.MultiSigMode;
import com.keystone.cold.viewmodel.multisigs.PsbtLegacyConfirmViewModel;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PsbtLegacyTxConfirmFragment extends BaseFragment<PsbtTxConfirmFragmentBinding> {

    protected PsbtLegacyConfirmViewModel psbtLegacyConfirmViewModel;
    private SigningDialog signingDialog;
    protected TxEntity txEntity;
    private int feeAttackCheckingState;
    private FeeAttackChecking feeAttackChecking;
    private boolean signed;
    protected ProgressModalDialog progressModalDialog;
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
        initViewModel();
        progressModalDialog = new ProgressModalDialog();
        progressModalDialog.show(mActivity.getSupportFragmentManager(), "");
        subscribeTx();
        Bundle bundle = requireArguments();
        String signTx = bundle.getString("signTx");
        if (signTx != null) {
            psbtLegacyConfirmViewModel.generateTx(signTx);
            feeAttackCheckingState = bundle.getInt("feeAttach");
            if (feeAttackCheckingState != NORMAL) {
                feeAttackChecking = new FeeAttackChecking(this);
            }
        } else {
            String psbtBase64 = bundle.getString("psbt_base64");
            psbtLegacyConfirmViewModel.handleTx(psbtBase64);
        }
    }

    protected void initViewModel() {
        psbtLegacyConfirmViewModel = ViewModelProviders.of(this).get(PsbtLegacyConfirmViewModel.class);
    }

    private void subscribeTx() {
        observeEntity();
        observeException();
        observeFeeAttack();
    }

    private void observeEntity() {
        psbtLegacyConfirmViewModel.getObservableTx().observe(this, txEntity -> {
            if (txEntity != null) {
                progressModalDialog.dismiss();
                this.txEntity = txEntity;
                mBinding.setTx(txEntity);
                refreshUI();
            }
        });
    }

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

    private void observeFeeAttack() {
        psbtLegacyConfirmViewModel.getFeeAttachCheckingResult().observe(this, state -> {
            feeAttackCheckingState = state;
            if (state != NORMAL) {
                feeAttackChecking = new FeeAttackChecking(this);
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
        List<TransactionItem> items = new ArrayList<>();
        try {
            JSONArray inputs = new JSONArray(txEntity.getFrom());
            for (int i = 0; i < inputs.length(); i++) {
                JSONObject out = inputs.getJSONObject(i);
                items.add(new TransactionItem(i,
                        out.getLong("value"),
                        out.getString("address"),
                        txEntity.getCoinCode()));
            }
        } catch (JSONException e) {
            return;
        }
        if (items.size() == 0) {
            mBinding.txDetail.arrowDown.setVisibility(View.GONE);
        }
        TransactionItemAdapter adapter
                = new TransactionItemAdapter(mActivity,
                TransactionItem.ItemType.INPUT);
        adapter.setItems(items);
        mBinding.txDetail.fromList.setVisibility(View.VISIBLE);
        mBinding.txDetail.fromList.setAdapter(adapter);

    }

    private void refreshReceiveList() {
        String to = txEntity.getTo();
        List<TransactionItem> items = new ArrayList<>();
        try {
            JSONArray outputs = new JSONArray(to);
            for (int i = 0; i < outputs.length(); i++) {
                JSONObject output = outputs.getJSONObject(i);
                boolean isChange = output.optBoolean("isChange");
                String changePath = null;
                if (isChange) {
                    changePath = output.getString("changeAddressPath");
                }

                items.add(new TransactionItem(i,
                        output.getLong("value"),
                        output.getString("address"),
                        txEntity.getCoinCode(), changePath));
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
        if (!TextUtils.isEmpty(txEntity.getSignStatus())) {
            mBinding.txDetail.txSignStatus.setVisibility(View.VISIBLE);
            String signStatus = txEntity.getSignStatus();

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
        if (txEntity.getCoinCode().equals(Coins.BTC.coinCode())) {
            float fee = Float.parseFloat(txEntity.getFee().split(" ")[0]);
            if (fee > 0.01) {
                mBinding.txDetail.fee.setTextColor(Color.RED);
            }
        }
    }

    private void handleSign() {
        if (feeAttackCheckingState == SAME_OUTPUTS) {
            feeAttackChecking.showFeeAttackWarning();
            return;
        }
        if (signed) {
            ModalDialog.showCommonModal(mActivity, getString(R.string.broadcast_tx),
                    getString(R.string.multisig_already_signed), getString(R.string.know),
                    null);
            return;
        }

        boolean fingerprintSignEnable = new FingerprintPolicyCallable(READ, TYPE_SIGN_TX).call();
        if (txEntity != null) {
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
        String to = txEntity.getTo();
        String encryptedAddress = ByteFormatter.bytes2hex(
                new KeyStoreUtil().encrypt(to.getBytes(StandardCharsets.UTF_8)));
        return psbtLegacyConfirmViewModel.isAddressInWhiteList(encryptedAddress);
    }

    protected AuthenticateModal.OnVerify signWithVerifyInfo() {
        return token -> {
            psbtLegacyConfirmViewModel.setToken(token);
            psbtLegacyConfirmViewModel.handleSignPsbt(requireArguments().getString("psbt_base64"));
            subscribeSignState();
        };
    }

    protected void subscribeSignState() {
        psbtLegacyConfirmViewModel.getSignState().observe(this, s -> {
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
                    psbtLegacyConfirmViewModel.getSignState().removeObservers(this);
                    psbtLegacyConfirmViewModel.getSignState().setValue(ParsePsbtViewModel.STATE_NONE);
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
                    psbtLegacyConfirmViewModel.getSignState().removeObservers(this);
                    psbtLegacyConfirmViewModel.getSignState().setValue(ParsePsbtViewModel.STATE_NONE);
                }, 2000);
            }
        });
    }

    protected void onSignSuccess() {
        Bundle data = new Bundle();
        data.putString(KEY_TXID, txEntity.getTxId());
        data.putString(KEY_MULTISIG_MODE, MultiSigMode.LEGACY.name());
        navigate(R.id.action_to_psbt_broadcast, data);
    }
}