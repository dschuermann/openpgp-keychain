/*
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

package org.sufficientlysecure.keychain.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.widget.Toast;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.keyimport.ParcelableKeyRing;
import org.sufficientlysecure.keychain.operations.MigrateSymmetricOperation;
import org.sufficientlysecure.keychain.operations.results.CreateSecretKeyRingCacheResult;
import org.sufficientlysecure.keychain.operations.results.MigrateSymmetricResult;
import org.sufficientlysecure.keychain.pgp.UncachedKeyRing;
import org.sufficientlysecure.keychain.pgp.UncachedPublicKey;
import org.sufficientlysecure.keychain.pgp.exception.PgpGeneralException;
import org.sufficientlysecure.keychain.service.CreateSecretKeyRingCacheParcel;
import org.sufficientlysecure.keychain.service.MigrateSymmetricInputParcel;
import org.sufficientlysecure.keychain.service.input.CryptoInputParcel;
import org.sufficientlysecure.keychain.service.input.RequiredInputParcel;
import org.sufficientlysecure.keychain.ui.base.BaseActivity;
import org.sufficientlysecure.keychain.ui.base.CryptoOperationHelper;
import org.sufficientlysecure.keychain.ui.passphrasedialog.PassphraseDialogActivity;
import org.sufficientlysecure.keychain.util.KeyringPassphrases;
import org.sufficientlysecure.keychain.util.KeyringPassphrases.SubKeyInfo;
import org.sufficientlysecure.keychain.util.ParcelableFileCache;
import org.sufficientlysecure.keychain.util.Passphrase;
import org.sufficientlysecure.keychain.util.Preferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Used to migrate pgp keys in the database to symmetric key encrypted keyring blocks
 * Migrate consists of a consolidate, caching secret keyrings to a file,
 * setting the master passphrase, and then collection of passphrases for keys to be migrated
 *
 * The consolidate & caching are run one after the other,
 * to prevent bugs when displaying the progress dialog.
 *
 * Caching is required to provide a reliable source for keyrings when migrating.
 * The cache is created only if migration has not started.
 * The previously built cache is used otherwise
 */
public class MigrateSymmetricActivity extends BaseActivity {
    private static final int REQUEST_REPEAT_PASSPHRASE = 0x00007008;
    private static final int REQUEST_CONSOLIDATE = 0x00007009;
    public static final String FRAGMENT_TAG = "currentFragment";

    private List<KeyringPassphrases> mPassphrasesList;
    private Iterator<SubKeyInfo> mSubKeysForRepeatAskPassphrase;
    private CryptoOperationHelper mCryptoOpHelper;
    private Fragment mCurrentFragment;
    private Class mFirstFragmentClass;
    private Passphrase mMasterPassphrase;

    @Override
    protected void onResume() {
        super.onResume();
        // TODO: there are cases where the migrate activity is opened twice
        // no deterministic way to reproduce this bug has been found yet
        // this fail-safe is designed as a workabout to solve the problem
        if (Preferences.getPreferences(this).isAppLockReady()) {
            finish();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_migrate_symmetric);
        mToolbar.setNavigationIcon(null);
        mToolbar.setNavigationOnClickListener(null);

        mPassphrasesList = new ArrayList<>();

        // Check whether we're recreating a previously destroyed instance
        if (savedInstanceState != null) {
            mCurrentFragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        } else {
            Preferences prefs = Preferences.getPreferences(this);

            if (!prefs.isPartiallyMigrated()) {
                Intent consolidateIntent = new Intent(this, ConsolidateDialogActivity.class);
                consolidateIntent.putExtra(ConsolidateDialogActivity.EXTRA_CONSOLIDATE_RECOVERY, false);
                startActivityForResult(consolidateIntent, REQUEST_CONSOLIDATE);
            }

            MigrateSymmetricStartFragment frag = new MigrateSymmetricStartFragment();
            mFirstFragmentClass = frag.getClass();
            loadFragment(frag, FragAction.START);
        }
    }

    @Override
    public void onBackPressed() {
        if(mFirstFragmentClass.equals(mCurrentFragment.getClass())) {
            ActivityCompat.finishAffinity(this);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void initLayout() {
        setContentView(R.layout.migrate_symmetric_activity);
    }

    public enum FragAction {
        START,
        TO_RIGHT,
        TO_LEFT
    }

    public void loadFragment(Fragment fragment, FragAction action) {
        mCurrentFragment = fragment;

        // Add the fragment to the 'fragment_container' FrameLayout
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        switch (action) {
            case START:
                transaction.setCustomAnimations(0, 0);
                transaction.replace(R.id.migrate_fragment_container, fragment, FRAGMENT_TAG)
                        .commit();
                break;
            case TO_LEFT:
                getSupportFragmentManager().popBackStackImmediate();
                break;
            case TO_RIGHT:
                transaction.setCustomAnimations(R.anim.frag_slide_in_from_right, R.anim.frag_slide_out_to_left,
                        R.anim.frag_slide_in_from_left, R.anim.frag_slide_out_to_right);
                transaction.addToBackStack(null);
                transaction.replace(R.id.migrate_fragment_container, fragment, FRAGMENT_TAG)
                        .commit();
                break;

        }

        // do it immediately!
        getSupportFragmentManager().executePendingTransactions();
    }

    public void finishedSettingMasterPassphrase(Passphrase masterPassphrase) {
        mMasterPassphrase = masterPassphrase;

        // continue migration
        collectPassphrasesForKeyRings();
    }

    private void collectPassphrasesForKeyRings() {
        ArrayList<SubKeyInfo> subKeyInfos = new ArrayList<>();
        try {
            ParcelableFileCache<ParcelableKeyRing> secretKeyRings =
                    new ParcelableFileCache<>(getApplication(), MigrateSymmetricOperation.CACHE_FILE_NAME);
            ParcelableFileCache.IteratorWithSize<ParcelableKeyRing> itSecrets = secretKeyRings.readCache(false);
            while(itSecrets.hasNext()) {
                ParcelableKeyRing ring = itSecrets.next();
                subKeyInfos.addAll(getSubKeyInfosFromRing(UncachedKeyRing.decodeFromData(ring.mBytes)));
            }
        } catch (IOException | PgpGeneralException e) {
            // not a problem, cache will be created when dialog is started again
            Toast.makeText(getApplicationContext(),
                    R.string.migrate_error_accessing_cache, Toast.LENGTH_LONG).show();
            ActivityCompat.finishAffinity(MigrateSymmetricActivity.this);
        }

        mSubKeysForRepeatAskPassphrase = subKeyInfos.iterator();
        if (mSubKeysForRepeatAskPassphrase.hasNext()) {
            startPassphraseActivity();
        } else {
            startMigration(new ArrayList<KeyringPassphrases>());
        }

    }

    private List<SubKeyInfo> getSubKeyInfosFromRing(UncachedKeyRing ring) throws IOException {
        ArrayList<SubKeyInfo> list = new ArrayList<>();
        for (Iterator<UncachedPublicKey> keyIt = ring.getPublicKeys(); keyIt.hasNext(); ) {
            UncachedPublicKey key = keyIt.next();
            list.add(new SubKeyInfo(ring.getMasterKeyId(),
                    key.getKeyId(),
                    new ParcelableKeyRing(ring.getEncoded(), ring.getMasterKeyId())));
        }
        return list;
    }

    private void createCache() {
        CryptoOperationHelper.Callback<CreateSecretKeyRingCacheParcel, CreateSecretKeyRingCacheResult> callback =
                new CryptoOperationHelper.Callback<CreateSecretKeyRingCacheParcel, CreateSecretKeyRingCacheResult>() {
                    Activity activity = MigrateSymmetricActivity.this;

                    @Override
                    public CreateSecretKeyRingCacheParcel createOperationInput() {
                        return new CreateSecretKeyRingCacheParcel(MigrateSymmetricOperation.CACHE_FILE_NAME);
                    }

                    @Override
                    public void onCryptoOperationSuccess(CreateSecretKeyRingCacheResult result) {
                        Preferences.getPreferences(activity).setPartiallyMigrated(true);
                    }

                    @Override
                    public void onCryptoOperationCancelled() {
                        Toast.makeText(activity.getApplicationContext(),
                                R.string.migrate_error_cancelled, Toast.LENGTH_LONG).show();
                        ActivityCompat.finishAffinity(activity);
                    }

                    @Override
                    public void onCryptoOperationError(CreateSecretKeyRingCacheResult result) {
                        Toast.makeText(activity.getApplicationContext(),
                                R.string.migrate_error_cache_keys, Toast.LENGTH_LONG).show();
                        ActivityCompat.finishAffinity(activity);
                    }

                    @Override
                    public boolean onCryptoSetProgress(String msg, int progress, int max) {
                        return false;
                    }
                };

        mCryptoOpHelper =
                new CryptoOperationHelper<>(1, this, callback, R.string.progress_cache_secret_keys);

        mCryptoOpHelper.cryptoOperation();
    }

    private void startPassphraseActivity() {
        SubKeyInfo keyInfo = mSubKeysForRepeatAskPassphrase.next();
        ParcelableKeyRing parcelableKeyRing = keyInfo.mKeyRing;
        long subKeyId = keyInfo.mSubKeyId;
        long masterKeyId = keyInfo.mMasterKeyId;

        Intent intent = new Intent(this, PassphraseDialogActivity.class);

        // try using last entered passphrase if appropriate
        if (!mPassphrasesList.isEmpty()) {
            KeyringPassphrases prevKeyring = mPassphrasesList.get(mPassphrasesList.size() - 1);
            Passphrase passphrase = prevKeyring.getSingleSubkeyPassphrase();

            boolean sameMasterKey = masterKeyId == prevKeyring.mMasterKeyId;
            boolean prevSubKeysHaveSamePassphrase = prevKeyring.subKeysHaveSamePassphrase();
            if(sameMasterKey && prevSubKeysHaveSamePassphrase) {
                intent.putExtra(PassphraseDialogActivity.EXTRA_PASSPHRASE_TO_TRY, passphrase);
            }
        }

        RequiredInputParcel requiredInput = RequiredInputParcel.
                createRequiredImportKeyPassphrase(masterKeyId, subKeyId, parcelableKeyRing);
        requiredInput.mSkipCaching = true;
        intent.putExtra(PassphraseDialogActivity.EXTRA_REQUIRED_INPUT, requiredInput);
        startActivityForResult(intent, REQUEST_REPEAT_PASSPHRASE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONSOLIDATE : {
                if (resultCode != Activity.RESULT_OK) {
                    Toast.makeText(this, R.string.migrate_error_consolidating,
                            Toast.LENGTH_LONG).show();
                    ActivityCompat.finishAffinity(this);
                    return;
                }

                if (!Preferences.getPreferences(getApplicationContext()).isPartiallyMigrated()) {
                    createCache();
                }
                return;
            }
            case REQUEST_REPEAT_PASSPHRASE : {
                if (resultCode != Activity.RESULT_OK) {
                    Toast.makeText(this, R.string.migrate_cancelled_dialog,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                RequiredInputParcel requiredParcel = data.getParcelableExtra(PassphraseDialogActivity.EXTRA_REQUIRED_INPUT);
                CryptoInputParcel cryptoParcel = data.getParcelableExtra(PassphraseDialogActivity.RESULT_CRYPTO_INPUT);
                long masterKeyId = requiredParcel.getMasterKeyId();
                long subKeyId = requiredParcel.getSubKeyId();
                Passphrase passphrase = cryptoParcel.getPassphrase();

                // save passphrase if one is returned
                // could be stripped or diverted to card otherwise
                if(passphrase != null) {
                    boolean isNewKeyRing = (mPassphrasesList.isEmpty() ||
                            mPassphrasesList.get(mPassphrasesList.size() - 1).mMasterKeyId != masterKeyId);

                    if (isNewKeyRing) {
                        KeyringPassphrases newKeyring = new KeyringPassphrases(masterKeyId, mMasterPassphrase);
                        newKeyring.mSubkeyPassphrases.put(subKeyId, passphrase);
                        mPassphrasesList.add(newKeyring);
                    } else {
                        KeyringPassphrases prevKeyring = mPassphrasesList.get(mPassphrasesList.size() - 1);
                        prevKeyring.mSubkeyPassphrases.put(subKeyId, passphrase);
                    }
                }

                // check next subkey
                if (mSubKeysForRepeatAskPassphrase.hasNext()) {
                    startPassphraseActivity();
                } else {
                    startMigration(mPassphrasesList);
                }

                return;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
                if (mCryptoOpHelper != null) {
                    mCryptoOpHelper.handleActivityResult(requestCode, resultCode, data);
                }
            }
        }
    }

    private void startMigration(final List<KeyringPassphrases> passphrasesList) {
        CryptoOperationHelper.Callback<MigrateSymmetricInputParcel, MigrateSymmetricResult> callback =
                new CryptoOperationHelper.Callback<MigrateSymmetricInputParcel, MigrateSymmetricResult>() {
                    Activity activity = MigrateSymmetricActivity.this;

                    @Override
                    public MigrateSymmetricInputParcel createOperationInput() {
                        return new MigrateSymmetricInputParcel(passphrasesList);
                    }

                    @Override
                    public void onCryptoOperationSuccess(MigrateSymmetricResult result) {
                        finishSuccessfulMigration();
                    }

                    @Override
                    public void onCryptoOperationCancelled() {
                        Toast.makeText(activity.getApplicationContext(),
                                R.string.migrate_error_cancelled, Toast.LENGTH_LONG).show();
                        ActivityCompat.finishAffinity(activity);
                    }

                    @Override
                    public void onCryptoOperationError(MigrateSymmetricResult result) {
                        Toast.makeText(activity.getApplicationContext(),
                                R.string.migrate_error_migrating, Toast.LENGTH_LONG).show();
                        ActivityCompat.finishAffinity(activity);
                    }

                    @Override
                    public boolean onCryptoSetProgress(String msg, int progress, int max) {
                        return false;
                    }
                };
        mCryptoOpHelper =
                new CryptoOperationHelper<>(1, this, callback, R.string.progress_migrating);

        mCryptoOpHelper.cryptoOperation();
    }

    private void finishSuccessfulMigration() {
        Preferences preferences = Preferences.getPreferences(this);
        preferences.setHasMasterPassphrase(true);
        preferences.setUsingEncryptedKeyRings(true);
        preferences.setPartiallyMigrated(false);
        preferences.setIsAppLockReady(true);
        finish();
    }

}
