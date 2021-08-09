package com.keystone.cold.ui.fragment.multisigs.casa;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;

import com.keystone.coinlib.Util;
import com.keystone.coinlib.accounts.ExtendedPublicKey;
import com.keystone.coinlib.utils.B58;
import com.keystone.cold.R;
import com.keystone.cold.callables.FingerprintPolicyCallable;
import com.keystone.cold.callables.GetExtendedPublicKeyCallable;
import com.keystone.cold.databinding.MultisigCasaSignMessageBinding;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.setup.PreImportFragment;
import com.keystone.cold.ui.modal.SigningDialog;
import com.keystone.cold.ui.views.AuthenticateModal;
import com.keystone.cold.viewmodel.multisigs.SignViewModel;

import org.spongycastle.util.encoders.Base64;
import org.spongycastle.util.encoders.Hex;

import static com.keystone.cold.callables.FingerprintPolicyCallable.READ;
import static com.keystone.cold.callables.FingerprintPolicyCallable.TYPE_SIGN_TX;
import static com.keystone.cold.ui.fragment.setup.PreImportFragment.ACTION;

public class CasaSignMessageFragment extends BaseFragment<MultisigCasaSignMessageBinding> {
    private SignViewModel signViewModel;
    private SigningDialog signingDialog;
    private final Runnable forgetPassword = () -> {
        Bundle bundle = new Bundle();
        bundle.putString(ACTION, PreImportFragment.ACTION_RESET_PWD);
        navigate(R.id.action_to_preImportFragment, bundle);
    };

    private String message;
    private String path;
    private String address;
    private String fileName = null;

    @Override
    protected int setView() {
        return R.layout.multisig_casa_sign_message;
    }

    @Override
    protected void init(View view) {
        signViewModel = ViewModelProviders.of(mActivity).get(SignViewModel.class);
        Bundle data = requireArguments();
        message = data.getString("message");
        String messageId = message.substring(0, 8);
        path = data.getString("path");
        String defaultFileName = "19700101" + "-" + "hc" + messageId + ".txt";
        fileName = data.getString("file_name", defaultFileName);
        int point = Math.max(path.lastIndexOf("'"), 0);
        String hardenedPath = path.substring(0, point + 1);
        String nonHardenedPath = path.substring(point + 2);
        String xPub = new GetExtendedPublicKeyCallable(hardenedPath).call();
        address = Util.deriveAddress(xPub, nonHardenedPath.split("/"));
        mBinding.message.setText(message);
        mBinding.path.setText(path);
        mBinding.address.setText(address);
        mBinding.toolbar.setNavigationOnClickListener(v -> {
            navigateUp();
        });
        mBinding.sign.setOnClickListener(v -> {
            handleSignMessage();
        });
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }

    private void handleSignMessage() {
        boolean fingerprintSignEnable = new FingerprintPolicyCallable(READ, TYPE_SIGN_TX).call();
        AuthenticateModal.show(mActivity, getString(R.string.password_modal_title), "", fingerprintSignEnable, token -> {
            signViewModel.setToken(token);
            signViewModel.handleSignMessage(message, path);
            subscribeSignStatus();
        }, forgetPassword);
    }

    private void subscribeSignStatus() {
        MutableLiveData<String> signStatus = signViewModel.getSignStatus();
        signStatus.observe(this, s -> {
            if (SignViewModel.STATE_SIGNING.equals(s)) {
                signingDialog = SigningDialog.newInstance();
                signingDialog.show(mActivity.getSupportFragmentManager(), "");
            } else if (SignViewModel.STATE_SIGN_SUCCESS.equals(s)) {
                if (signingDialog != null) {
                    signingDialog.setState(SigningDialog.STATE_SUCCESS);
                }
                new Handler().postDelayed(() -> {
                    if (signingDialog != null) {
                        signingDialog.dismiss();
                    }
                    signingDialog = null;
                    onSignSuccess();
                    signStatus.removeObservers(this);
                }, 500);
            } else if (SignViewModel.STATE_SIGN_FAIL.equals(s)) {
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
                    signStatus.setValue(SignViewModel.STATE_NONE);
                    signStatus.removeObservers(this);
                }, 2000);
            }
        });
    }

    private void onSignSuccess() {
        String signature = signViewModel.getSignMessageSignature().getValue();
        String signResult = constructResult(signature);
        Bundle data = new Bundle();
        data.putString("file_name", fileName);
        data.putString("sign_result", signResult);
        navigate(R.id.action_to_casaSignMessageResultFragment, data);
    }

    private String constructResult(String signature) {
        StringBuilder sb = new StringBuilder();
        String sigB64 = Base64.toBase64String(Hex.decode(signature));
        sb.append("-----BEGIN BITCOIN SIGNED MESSAGE-----\n");
        sb.append(message);
        sb.append("\n");
        sb.append("-----BEGIN SIGNATURE-----\n");
        sb.append(address);
        sb.append("\n");
        sb.append(sigB64);
        sb.append("\n");
        sb.append("-----END BITCOIN SIGNED MESSAGE-----");
        return sb.toString();
    }
}
