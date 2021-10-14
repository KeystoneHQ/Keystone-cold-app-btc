package com.keystone.cold.viewmodel.multisigs;

public enum MultiSigMode {
    LEGACY("legacy"),
    CASA("casa"),
    CARAVAN("caravan");

    private final String modeId;

    MultiSigMode(String modeId) {
        this.modeId = modeId;
    }

    public String getModeId() {
        return this.modeId;
    }

}
