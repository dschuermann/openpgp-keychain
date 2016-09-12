/*
 * Copyright (C) 2014-2015 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.service.PassphraseCacheService;
import org.sufficientlysecure.keychain.service.input.CryptoInputParcel;
import org.sufficientlysecure.keychain.service.input.RequiredInputParcel;
import org.sufficientlysecure.keychain.ui.CreateKeyActivity.FragAction;
import org.sufficientlysecure.keychain.ui.passphrasedialog.PassphraseDialogActivity;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.Preferences;

public class CreateKeyStartFragment extends Fragment {

    CreateKeyActivity mCreateKeyActivity;

    View mCreateKey;
    View mImportKey;
    View mSecurityToken;
    TextView mSkipOrCancel;
    public static final int REQUEST_CODE_IMPORT_KEY = 0x00007012;
    public static final int REQUEST_MASTER_PASSPHRASE = 1;

    /**
     * Creates new instance of this fragment
     */
    public static CreateKeyStartFragment newInstance() {
        CreateKeyStartFragment frag = new CreateKeyStartFragment();

        Bundle args = new Bundle();

        frag.setArguments(args);

        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.create_key_start_fragment, container, false);

        mCreateKey = view.findViewById(R.id.create_key_create_key_button);
        mImportKey = view.findViewById(R.id.create_key_import_button);
        mSecurityToken = view.findViewById(R.id.create_key_security_token_button);
        mSkipOrCancel = (TextView) view.findViewById(R.id.create_key_cancel);

        if (mCreateKeyActivity.mFirstTime) {
            mSkipOrCancel.setText(R.string.first_time_skip);
        } else {
            mSkipOrCancel.setText(R.string.btn_do_not_save);
        }

        mCreateKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CreateKeyNameFragment frag = CreateKeyNameFragment.newInstance();
                mCreateKeyActivity.loadFragment(frag, FragAction.TO_RIGHT);
                askForMasterPassphraseIfRequired();
            }
        });

        mSecurityToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CreateSecurityTokenWaitFragment frag = new CreateSecurityTokenWaitFragment();
                mCreateKeyActivity.loadFragment(frag, FragAction.TO_RIGHT);
            }
        });

        mImportKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mCreateKeyActivity, ImportKeysActivity.class);
                intent.setAction(ImportKeysActivity.ACTION_IMPORT_KEY_FROM_FILE_AND_RETURN);
                startActivityForResult(intent, REQUEST_CODE_IMPORT_KEY);
            }
        });

        mSkipOrCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCreateKeyActivity.mFirstTime) {
                    mCreateKeyActivity.setResult(Activity.RESULT_CANCELED);
                }
                mCreateKeyActivity.finish();
            }
        });

        return view;
    }

    private void askForMasterPassphraseIfRequired() {
        if (Preferences.getPreferences(mCreateKeyActivity).usesSinglePassphraseWorkflow()
                && mCreateKeyActivity.mPassphrase == null) {

            // if already cached, then just use the cached one
            try {
                mCreateKeyActivity.mPassphrase =
                        PassphraseCacheService.getMasterPassphrase(mCreateKeyActivity);
            } catch (PassphraseCacheService.KeyNotFoundException ignored) {}

            // otherwise, ask for it
            Intent passphraseIntent = new Intent(mCreateKeyActivity, PassphraseDialogActivity.class);
            RequiredInputParcel requiredInput =
                    RequiredInputParcel.createRequiredAppLockPassphrase();
            passphraseIntent.putExtra(PassphraseDialogActivity.EXTRA_REQUIRED_INPUT, requiredInput);
            startActivityForResult(passphraseIntent, REQUEST_MASTER_PASSPHRASE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_KEY) {
            if (resultCode == Activity.RESULT_OK) {
                if (mCreateKeyActivity.mFirstTime) {
                    Preferences prefs = Preferences.getPreferences(mCreateKeyActivity);
                    prefs.setFirstTime(false);
                    mCreateKeyActivity.finish();
                } else {
                    // just finish activity and return data
                    mCreateKeyActivity.setResult(Activity.RESULT_OK, data);
                    mCreateKeyActivity.finish();
                }
            }
        } else if (requestCode == REQUEST_MASTER_PASSPHRASE) {
            if (resultCode != Activity.RESULT_OK) {
                mCreateKeyActivity.loadFragment(null, FragAction.TO_LEFT);
                return;
            }
            CryptoInputParcel cryptoResult =
                    data.getParcelableExtra(PassphraseDialogActivity.RESULT_CRYPTO_INPUT);
            mCreateKeyActivity.mPassphrase = cryptoResult.getPassphrase();
        } else {
            Log.e(Constants.TAG, "No valid request code!");
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCreateKeyActivity = (CreateKeyActivity) getActivity();
    }

}
