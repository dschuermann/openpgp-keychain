/*
 * Copyright (C) 2015 Dominik Schürmann <dominik@dominikschuermann.de>
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
package org.sufficientlysecure.keychain.ui.base;

import android.app.Activity;

import org.sufficientlysecure.keychain.operations.results.ImportKeyResult;
import org.sufficientlysecure.keychain.pgp.CanonicalizedSecretKey;
import org.sufficientlysecure.keychain.service.ImportKeyringParcel;
import org.sufficientlysecure.keychain.ui.CreateKeyWizardActivity;
import org.sufficientlysecure.keychain.util.Passphrase;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Base fragment class for any wizard fragment
 */
public abstract class WizardFragment extends QueueingCryptoOperationFragment<ImportKeyringParcel,
        ImportKeyResult> implements CreateKeyWizardActivity.CreateKeyWizardListener {
    protected WizardFragmentListener mWizardFragmentListener;

    @Override
    public boolean onNextClicked() {
        return false;
    }

    @Override
    public boolean onBackClicked() {
        return true;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mWizardFragmentListener = (WizardFragmentListener) activity;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mWizardFragmentListener = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mWizardFragmentListener != null) {
            mWizardFragmentListener.onWizardFragmentVisible(this);
        }
    }

    @Override
    public void onQueuedOperationSuccess(ImportKeyResult result) {

    }

    @Override
    public ImportKeyringParcel createOperationInput() {
        return null;
    }

    /**
     * Helper method to add a new email to the email wizard fragment.
     */
    public void onRequestAddEmail(String email) {

    }
}