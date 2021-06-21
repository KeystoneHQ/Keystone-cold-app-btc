package com.keystone.cold.viewmodel.multisigs;

public enum MultiSigMode {
    LEGACY("0"),
    CASA("1");

    private final String modeId;

    MultiSigMode(String modeId) {
        this.modeId = modeId;
    }

    public String getModeId() {
        return this.modeId;
    }

}
