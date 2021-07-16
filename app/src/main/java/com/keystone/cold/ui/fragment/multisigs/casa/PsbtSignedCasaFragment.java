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

package com.keystone.cold.ui.fragment.multisigs.casa;

import android.view.View;

import com.keystone.cold.db.entity.CasaSignature;
import com.sparrowwallet.hummingbird.registry.CryptoPSBT;

import org.spongycastle.util.encoders.Base64;

public class PsbtSignedCasaFragment extends SignedCasaFragment {

    @Override
    protected void displaySignResult(CasaSignature casaSignature) {
        mBinding.txDetail.txIdInfo.setVisibility(View.GONE);
        mBinding.txDetail.arrowDown.setVisibility(View.GONE);
        mBinding.txDetail.scanInfo.setVisibility(View.GONE);
        mBinding.txDetail.exportToSdcardHint.setVisibility(View.GONE);
        mBinding.txDetail.export.setVisibility(View.GONE);

        //show bc32 animated qr code
        mBinding.txDetail.dynamicQrcodeLayout.qrcode.setVisibility(View.VISIBLE);
        byte[] psbtBytes = Base64.decode(casaSignature.getSignedHex());
        mBinding.txDetail.dynamicQrcodeLayout.qrcode.setData(new CryptoPSBT(psbtBytes).toUR().toString());
        mBinding.txDetail.dynamicQrcodeLayout.hint.setVisibility(View.GONE);
        mBinding.txDetail.qrcodeLayout.qrcode.setVisibility(View.GONE);
        mBinding.txDetail.broadcastGuide.setVisibility(View.GONE);
        mBinding.txDetail.export.setVisibility(View.GONE);
        mBinding.txDetail.exportToSdcardHint.setVisibility(View.INVISIBLE);
    }
}
