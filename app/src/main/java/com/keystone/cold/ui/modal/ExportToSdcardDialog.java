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

package com.keystone.cold.ui.modal;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.DialogFragment;

import com.keystone.cold.R;


public class ExportToSdcardDialog extends DialogFragment {

    private ExportToSdcardDialog(){

    }

    public static ExportToSdcardDialog newInstance(String fileName) {
        return newInstance(fileName, true);
    }

    public static ExportToSdcardDialog newInstance(String fileName, boolean success) {
        Bundle bundle = new Bundle();
        bundle.putString("fileName",fileName);
        bundle.putBoolean("success", success);

        ExportToSdcardDialog dialog = new ExportToSdcardDialog();
        dialog.setArguments(bundle);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View v = DataBindingUtil.inflate(LayoutInflater.from(getActivity()),
                R.layout.export_success,null,false).getRoot();
        TextView fileName = v.findViewById(R.id.file_name);
        ImageView img = v.findViewById(R.id.img);
        TextView text = v.findViewById(R.id.text);
        if (getArguments()!= null && !TextUtils.isEmpty(getArguments().getString("fileName"))) {
            fileName.setVisibility(View.VISIBLE);
            fileName.setText(getString(R.string.file_name,getArguments().getString("fileName")));
        }
        if (getArguments()!= null && !getArguments().getBoolean("success")) {
            img.setImageDrawable(getActivity().getDrawable(R.drawable.circle_negative));
            img.setImageTintList(ColorStateList.valueOf(Color.RED));
            text.setText(getString(R.string.export_failed));
        }

        Dialog dialog = new AlertDialog.Builder(getActivity(), R.style.dialog)
                .setView(v)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
}
