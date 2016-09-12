/*
 * Copyright (C) 2014-2016 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2016 Alex Fong Jie Wen <alexfongg@gmail.com>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.ui.passphrasedialog;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import org.sufficientlysecure.keychain.service.input.CryptoInputParcel;
import org.sufficientlysecure.keychain.util.Passphrase;

abstract class BasePassphraseDialogFragment extends DialogFragment implements TextView.OnEditorActionListener {
    protected boolean mIsCancelled = false;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof PassphraseDialogActivity)) {
            throw new RuntimeException("Activity must be a " +
                    PassphraseDialogActivity.class.getSimpleName());
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        if (getActivity() == null) {
            return;
        }

        // note we need no synchronization here, this variable is only accessed in the ui thread
        mIsCancelled = true;
        Activity activity = getActivity();
        activity.setResult(Activity.RESULT_CANCELED);
        activity.finish();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);

        hideKeyboard();
    }

    private void hideKeyboard() {
        if (getActivity() == null) {
            return;
        }

        InputMethodManager inputManager = (InputMethodManager) getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        inputManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        // Associate the "done" button on the soft keyboard with the okay button in the view
        if (EditorInfo.IME_ACTION_DONE == actionId) {
            AlertDialog dialog = ((AlertDialog) getDialog());
            Button bt = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            bt.performClick();
            return true;
        }
        return false;
    }

    void returnWithPassphrase(Passphrase passphrase) {
        // any indication this isn't needed anymore, don't do it.
        if (mIsCancelled || getActivity() == null) {
            return;
        }
        CryptoInputParcel cryptoParcel = getArguments().getParcelable(PassphraseDialogActivity.EXTRA_CRYPTO_INPUT);

        // noinspection ConstantConditions, non-null cryptoParcel is handled in PassphraseDialogActivity.onCreate()
        cryptoParcel.mPassphrase = passphrase;

        ((PassphraseDialogActivity) getActivity()).handleResult(cryptoParcel);

        // required for closing the virtual keyboard
        dismiss();
        getActivity().finish();
    }
}
