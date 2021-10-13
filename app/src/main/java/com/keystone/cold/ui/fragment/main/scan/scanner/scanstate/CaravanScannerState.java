package com.keystone.cold.ui.fragment.main.scan.scanner.scanstate;

import android.os.Bundle;

import com.keystone.cold.R;
import com.keystone.cold.ui.fragment.main.scan.scanner.ScanResultTypes;

import java.util.List;

public class CaravanScannerState extends LegacyScannerState{
    public CaravanScannerState(List<ScanResultTypes> desiredResults) {
        super(desiredResults);
    }

    @Override
    protected void navigateTo(Bundle bundle) {
        mFragment.navigate(R.id.action_to_psbtCaravanTxConfirmFragment, bundle);
    }
}
