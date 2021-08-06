package com.keystone.cold.ui.fragment.multisigs.casa;

import static com.keystone.cold.viewmodel.GlobalViewModel.hasSdcard;
import static com.keystone.cold.viewmodel.GlobalViewModel.showNoSdcardModal;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

import com.keystone.cold.R;
import com.keystone.cold.databinding.CasaPerformHealthCheckBinding;
import com.keystone.cold.databinding.FileListItemBinding;
import com.keystone.cold.ui.common.BaseBindingAdapter;
import com.keystone.cold.ui.fragment.BaseFragment;
import com.keystone.cold.ui.fragment.main.electrum.Callback;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResult;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerState;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScannerViewModel;
import com.keystone.cold.ui.modal.ModalDialog;
import com.keystone.cold.update.utils.FileUtils;
import com.keystone.cold.update.utils.Storage;
import com.keystone.cold.viewmodel.exceptions.UnknowQrCodeException;
import com.keystone.cold.viewmodel.multisigs.CasaMultiSigViewModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CasaPerformHealthCheckFragment extends BaseFragment<CasaPerformHealthCheckBinding> implements Callback {
    public static final String TAG = "CasaHealthCheckFragment";
    protected CasaMultiSigViewModel casaMultiSigViewModel;
    private FileListAdapter adapter;
    private AtomicBoolean showEmpty;

    private static final Pattern messagePattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{14}$");
    private static final Pattern pathPattern = Pattern.compile("^m(/\\d+'?)+$");

    @Override
    protected int setView() {
        return R.layout.casa_perform_health_check;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    private static boolean isMessageValid(String message) {
        Matcher matcher = messagePattern.matcher(message);
        return matcher.matches();
    }

    private static boolean isPathValid(String path) {
        Matcher matcher = pathPattern.matcher(path);
        return matcher.matches();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.action_scan) {
            ViewModelProviders.of(mActivity).get(ScannerViewModel.class).setState(new ScannerState(Collections.singletonList(ScanResultTypes.UR_BYTES)) {
                @Override
                public void handleScanResult(ScanResult result) throws Exception {
                    if (result.getType().equals(ScanResultTypes.UR_BYTES)) {
                        if (handleCasaMessage(result)) return;
                        throw new UnknowQrCodeException("unknown qr code");
                    }
                }

                private boolean handleCasaMessage(ScanResult result) throws IOException {
                    byte[] bytes = (byte[]) result.resolve();
                    String signData = new String(bytes, StandardCharsets.UTF_8);
                    BufferedReader reader = new BufferedReader(new StringReader(signData));
                    String message = reader.readLine();
                    if (!isMessageValid(message))
                        throw new InvalidParameterException("invalid message");
                    String path = reader.readLine();
                    if (!isPathValid(path)) throw new InvalidParameterException("invalid path");
                    Bundle bundle = new Bundle();
                    bundle.putString("message", message);
                    bundle.putString("path", path);
                    mFragment.navigate(R.id.action_scanner_to_casaSignMessageFragment, bundle);
                    return true;
                }

                @Override
                public boolean handleException(Exception e) {
                    if (e instanceof InvalidParameterException) {
                        ModalDialog.showCommonModal(mActivity,
                                getString(R.string.electrum_decode_txn_fail),
                                getString(R.string.error_txn_file),
                                getString(R.string.confirm),
                                null);
                        return true;
                    }
                    return super.handleException(e);
                }
            });
            navigate(R.id.action_to_scanner);
            return true;
        }
        return false;
    }


    @Override
    protected void init(View view) {
        mBinding.toolbar.setNavigationOnClickListener(v -> navigateUp());
        casaMultiSigViewModel = ViewModelProviders.of(mActivity).get(CasaMultiSigViewModel.class);
        adapter = new FileListAdapter(mActivity, this);
        initViews();
    }

    private void initViews() {
        showEmpty = new AtomicBoolean(false);
        if (!hasSdcard()) {
            showEmpty.set(true);
            mBinding.emptyTitle.setText(R.string.no_sdcard);
            mBinding.emptyMessage.setText(R.string.no_sdcard_hint);
        } else {
            mBinding.list.setAdapter(adapter);
            casaMultiSigViewModel.loadUnsignedMessage().observe(this, files -> {
                if (files.size() > 0) {
                    adapter.setItems(files);
                } else {
                    showEmpty.set(true);
                    mBinding.emptyTitle.setText(R.string.no_unsigned_txn);
                    mBinding.emptyMessage.setText(R.string.no_unsigned_txn_hint);
                }
                updateUi();
            });
        }
        updateUi();
    }

    private void updateUi() {
        if (showEmpty.get()) {
            mBinding.emptyView.setVisibility(View.VISIBLE);
            mBinding.list.setVisibility(View.GONE);
        } else {
            mBinding.emptyView.setVisibility(View.GONE);
            mBinding.list.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void initData(Bundle savedInstanceState) {

    }

    @Override
    public void onClick(String file) {
        try {
            if (!hasSdcard()) {
                showNoSdcardModal(mActivity);
                initViews();
                return;
            }
            Storage storage = Storage.createByEnvironment();
            byte[] content = FileUtils.bufferlize(new File(storage.getExternalDir(), file));
            if (content != null) {
                String signData = new String(content, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(new StringReader(signData));
                String message = reader.readLine();
                if (!isMessageValid(message))
                    throw new InvalidParameterException("invalid message");
                String path = reader.readLine();
                if (!isPathValid(path)) throw new InvalidParameterException("invalid path");
                Bundle bundle = new Bundle();
                bundle.putString("message", message);
                bundle.putString("path", path);
                navigate(R.id.action_scanner_to_casaSignMessageFragment, bundle);
                return;
            }

            ModalDialog.showCommonModal(mActivity,
                    getString(R.string.electrum_decode_txn_fail),
                    getString(R.string.error_txn_file),
                    getString(R.string.confirm),
                    null);
        } catch (InvalidParameterException | IOException e) {
            e.printStackTrace();
            ModalDialog.showCommonModal(mActivity,
                    getString(R.string.electrum_decode_txn_fail),
                    getString(R.string.error_txn_file),
                    getString(R.string.confirm),
                    null);
        }
    }

    public static class FileListAdapter extends BaseBindingAdapter<String, FileListItemBinding> {
        private final Callback callback;

        FileListAdapter(Context context, Callback callback) {
            super(context);
            this.callback = callback;
        }

        @Override
        protected int getLayoutResId(int viewType) {
            return R.layout.file_list_item;
        }

        @Override
        protected void onBindItem(FileListItemBinding binding, String item) {
            binding.setFile(item);
            binding.setCallback(callback);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            super.onBindViewHolder(holder, position);
        }
    }


}
