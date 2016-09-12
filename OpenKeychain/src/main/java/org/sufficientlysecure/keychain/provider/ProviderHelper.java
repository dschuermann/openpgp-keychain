/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2014 Vincent Breitmoser <v.breitmoser@mugenguild.com>
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

package org.sufficientlysecure.keychain.provider;


import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.keyimport.EncryptedSecretKeyRing;
import org.sufficientlysecure.keychain.keyimport.ParcelableKeyRing;
import org.sufficientlysecure.keychain.operations.ImportOperation;
import org.sufficientlysecure.keychain.operations.results.ChangePassphraseWorkflowResult;
import org.sufficientlysecure.keychain.operations.results.ConsolidateResult;
import org.sufficientlysecure.keychain.operations.results.CreateSecretKeyRingCacheResult;
import org.sufficientlysecure.keychain.operations.results.ImportKeyResult;
import org.sufficientlysecure.keychain.operations.results.MigrateSymmetricResult;
import org.sufficientlysecure.keychain.operations.results.OperationResult;
import org.sufficientlysecure.keychain.operations.results.OperationResult.LogType;
import org.sufficientlysecure.keychain.operations.results.OperationResult.OperationLog;
import org.sufficientlysecure.keychain.pgp.Progressable;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRingData;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.provider.KeychainContract.UpdatedKeys;
import org.sufficientlysecure.keychain.util.KeyringPassphrases;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.ParcelableFileCache;
import org.sufficientlysecure.keychain.util.ParcelableFileCache.IteratorWithSize;
import org.sufficientlysecure.keychain.util.Passphrase;
import org.sufficientlysecure.keychain.util.Preferences;
import org.sufficientlysecure.keychain.util.ProgressFixedScaler;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * This class contains high level methods for database access. Despite its
 * name, it is not only a helper but actually the main interface for all
 * synchronous database operations.
 * <p/>
 * Operations in this class write logs. These can be obtained from the
 * OperationResultParcel return values directly, but are also accumulated over
 * the lifetime of the executing ProviderHelper object unless the resetLog()
 * method is called to start a new one specifically.
 */
public class ProviderHelper {
    private final ProviderReader mReader;
    private final ProviderWriter mWriter;
    protected final Context mContext;
    private final ContentResolver mContentResolver;
    private OperationLog mLog;

    protected int mIndent;

    public ProviderHelper(Context context) {
        this(context, new OperationLog(), 0);
    }

    public ProviderHelper(Context context, OperationLog log) {
        this(context, log, 0);
    }

    public ProviderHelper(Context context, OperationLog log, int indent) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mLog = log;
        mIndent = indent;
        mReader = ProviderReader.newInstance(this, mContentResolver);
        mWriter = ProviderWriter.newInstance(this, mContentResolver);
    }

    public static <T extends ProviderReader>
    ProviderHelper useCustomReaderForTest(Context context, Object outerObject, Class<T> readerClass)
    throws Exception {
        return new ProviderHelper(context, new OperationLog(), 0, outerObject, readerClass);
    }

    // TODO: can this be cleaned up with java8's new features?
    // for test use only
    private <T extends ProviderReader> ProviderHelper(Context context, OperationLog log, int indent,
                                                      Object outerObject, Class<T> customReaderClass) throws Exception {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mLog = log;
        mIndent = indent;
        mWriter = ProviderWriter.newInstance(this, mContentResolver);

        // use reflection to create an instance of the custom reader
        mReader = customReaderClass.getDeclaredConstructor(
                outerObject.getClass(),
                ProviderHelper.class,
                ContentResolver.class
        ).newInstance(outerObject, this, mContentResolver);
    }

    public OperationLog getLog() {
        return mLog;
    }

    public void log(LogType type) {
        if (mLog != null) {
            mLog.add(type, mIndent);
        }
    }

    public void log(LogType type, Object... parameters) {
        if (mLog != null) {
            mLog.add(type, mIndent, parameters);
        }
    }

    public void clearLog() {
        mLog = new OperationLog();
    }

    public ContentResolver getContentResolver() {
        return mContentResolver;
    }

    public ProviderReader read() {
        return mReader;
    }

    public ProviderWriter write() {
        return mWriter;
    }

    // Caches all secret keyrings we possess into a file
    @NonNull
    public CreateSecretKeyRingCacheResult createSecretKeyRingCache(Progressable progress, String fileName) {
        OperationLog log = new OperationLog();
        int indent = 0;
        log.add(LogType.MSG_CC, indent);
        progress.setPreventCancel();
        progress.setProgress(R.string.progress_cache_secret_keys, 0, 100);

        try {
            log.add(LogType.MSG_CC_CACHE_SECRET, indent);
            indent += 1;

            Cursor cursor = mContentResolver.query(KeyRings.buildUnifiedKeyRingsUri(),
                    new String[]{KeyRings.PRIVKEY_DATA, KeyRings.HAS_ANY_SECRET, KeyRings.MASTER_KEY_ID},
                    KeyRings.HAS_ANY_SECRET + "!=0", null, null);
            cacheKeyRings(cursor, 0, 2, fileName);
            //noinspection ConstantConditions, null is caught below
            cursor.close();

        } catch (NullPointerException | IOException e) {
            log.add(LogType.MSG_CC_ERROR_DB, indent + 1);
            return new CreateSecretKeyRingCacheResult(CreateSecretKeyRingCacheResult.RESULT_ERROR, log);
        }

        log.add(LogType.MSG_CC_SUCCESS, indent);
        return new CreateSecretKeyRingCacheResult(CreateSecretKeyRingCacheResult.RESULT_OK, log);
    }

    @NonNull
    public MigrateSymmetricResult migrateSymmetricOperation(Progressable progress, String fileName,
                                                            List<KeyringPassphrases> passphrasesList) {
        OperationLog log = new OperationLog();
        int indent = 0;
        ParcelableFileCache<ParcelableKeyRing> secRings;

        log.add(LogType.MSG_MI, indent);
        try {
            secRings = new ParcelableFileCache<>(mContext, fileName);
            IteratorWithSize<ParcelableKeyRing> itSecrets = secRings.readCache(false);
            int numSecret = itSecrets.getSize();

            log.add(LogType.MSG_MI_REIMPORT_SECRET, indent, numSecret);
            indent += 1;

            ImportOperation op = new ImportOperation(mContext, this, progress);
            ImportKeyResult result = op.serialKeyRingImport(itSecrets, numSecret, null, null, passphrasesList);
            log.add(result, indent);

            secRings.delete();
        } catch (IOException e) {
            Log.e(Constants.TAG, "error decoding secret keys from cache", e);
            log.add(LogType.MSG_MI_ERROR_DECODE_CACHE, indent);
            return new MigrateSymmetricResult(MigrateSymmetricResult.RESULT_ERROR, log);
        } finally {
            indent -= 1;
        }

        log.add(LogType.MSG_MI_SUCCESS, indent);
        return new MigrateSymmetricResult(MigrateSymmetricResult.RESULT_OK, log);
    }

    @NonNull
    public ChangePassphraseWorkflowResult revertWorkflowChangeOperation(Progressable progress,
                                                                        String fileName) {
        OperationLog log = new OperationLog();
        int indent = 0;
        ParcelableFileCache<ParcelableKeyRing> secRings;

        int currentProgress = 0;
        progress.setProgress(R.string.progress_revert_change_workflow, currentProgress, 100);
        progress.setPreventCancel();

        log.add(LogType.MSG_RW, indent);
        try {
            secRings = new ParcelableFileCache<>(mContext, fileName);
            IteratorWithSize<ParcelableKeyRing> itSecrets = secRings.readCache(false);
            int numSecret = itSecrets.getSize();

            log.add(LogType.MSG_RW_REIMPORT_SECRET, indent, numSecret);
            indent += 1;

            int progressInterval = 100 / numSecret;
            while (itSecrets.hasNext()) {
                progress.setProgress(currentProgress, 100);
                ParcelableKeyRing parcel = itSecrets.next();
                long masterKeyId = parcel.mMasterKeyId;
                byte[] encryptedBlob = parcel.mBytes;

                // directly replace the blob into the db
                ContentValues values = new ContentValues();
                values.put(KeyRingData.KEY_RING_DATA, encryptedBlob);
                Uri uri = KeyRingData.buildSecretKeyRingUri(masterKeyId);
                if (mContentResolver.update(uri, values, null, null) == 0) {
                    log.add(LogType.MSG_RW_DB_EXCEPTION, indent);
                }
                currentProgress += progressInterval;
            }

            secRings.delete();

        } catch (IOException e) {
            Log.e(Constants.TAG, "error retrieving secret keys from cache", e);
            log.add(LogType.MSG_RW_ERROR_IO, indent + 1);
            return new ChangePassphraseWorkflowResult(ChangePassphraseWorkflowResult.RESULT_ERROR, log);
        } finally {
            indent -= 1;
        }

        log.add(LogType.MSG_RW_SUCCESS, indent);
        return new ChangePassphraseWorkflowResult(ChangePassphraseWorkflowResult.RESULT_OK, log);
    }

    @NonNull
    public ChangePassphraseWorkflowResult changePassphraseWorkflowOperation(Progressable progress,
                                                                            String fileName,
                                                                            HashMap<Long, Passphrase> passphrases,
                                                                            Passphrase masterPassphrase,
                                                                            Boolean toSinglePassphraseWorkflow) {
        OperationLog log = new OperationLog();
        int indent = 0;
        ParcelableFileCache<ParcelableKeyRing> secRings;

        int currentProgress = 0;
        progress.setProgress(R.string.progress_change_workflow, currentProgress, 100);
        progress.setPreventCancel();

        log.add(LogType.MSG_PW, indent);
        try {
            secRings = new ParcelableFileCache<>(mContext, fileName);
            IteratorWithSize<ParcelableKeyRing> itSecrets = secRings.readCache(false);
            int numSecret = itSecrets.getSize();

            log.add(LogType.MSG_PW_REIMPORT_SECRET, indent, numSecret);
            indent += 1;

            int progressInterval = 100 / numSecret;
            SecretKey key = read().getMasterSecretKey(masterPassphrase);
            while (itSecrets.hasNext()) {
                progress.setProgress(currentProgress, 100);
                ParcelableKeyRing parcel = itSecrets.next();
                long masterKeyId = parcel.mMasterKeyId;
                byte[] encryptedBlob = parcel.mBytes;
                byte[] reEncrypted;

                if (toSinglePassphraseWorkflow) {
                    // currently, each ring has a different passphrase
                    Passphrase keyPassphrase = passphrases.get(masterKeyId);
                    byte[] decrypted = ByteArrayEncryptor.decryptByteArray(encryptedBlob, keyPassphrase.getCharArray());
                    reEncrypted = ByteArrayEncryptor.encryptWithMasterKey(decrypted, key);

                } else {
                    // currently, each ring is with intermediate key

                    byte[] decrypted = ByteArrayEncryptor.decryptWithMasterKey(encryptedBlob, key);
                    reEncrypted = ByteArrayEncryptor.encryptByteArray(decrypted, masterPassphrase.getCharArray());
                }

                // directly replace the blob in the db
                ContentValues values = new ContentValues();
                values.put(KeyRingData.KEY_RING_DATA, reEncrypted);
                Uri uri = KeyRingData.buildSecretKeyRingUri(masterKeyId);
                if (mContentResolver.update(uri, values, null, null) == 0) {
                    log.add(LogType.MSG_PW_DB_EXCEPTION, indent);
                    return new ChangePassphraseWorkflowResult(ChangePassphraseWorkflowResult.RESULT_ERROR, log);
                }
                currentProgress += progressInterval;
            }

            secRings.delete();

        } catch (ByteArrayEncryptor.IncorrectPassphraseException e) {
            Log.e(Constants.TAG, "Fatal bug in code, the passphrases we tried are incorrect", e);
            log.add(LogType.MSG_PW_BAD_PASSPHRASE, indent + 1);
            return new ChangePassphraseWorkflowResult(ChangePassphraseWorkflowResult.RESULT_ERROR, log);
        } catch (ByteArrayEncryptor.EncryptDecryptException e) {
            Log.e(Constants.TAG, "Encryption went wrong.", e);
            log.add(LogType.MSG_PW_ERROR_ENCRYPT_DECRYPT, indent + 1);
            return new ChangePassphraseWorkflowResult(ChangePassphraseWorkflowResult.RESULT_ERROR, log);
        } catch (IOException e) {
            Log.e(Constants.TAG, "error retrieving secret keys from cache", e);
            log.add(LogType.MSG_PW_ERROR_IO, indent + 1);
            return new ChangePassphraseWorkflowResult(ChangePassphraseWorkflowResult.RESULT_ERROR, log);
        } finally {
            indent -= 1;
        }

        log.add(LogType.MSG_PW_SUCCESS, indent);
        return new ChangePassphraseWorkflowResult(ChangePassphraseWorkflowResult.RESULT_OK, log);
    }

    @NonNull
    public ConsolidateResult consolidateDatabaseStep1(Progressable progress) {
        OperationLog log = new OperationLog();
        int indent = 0;

        log.add(LogType.MSG_CON, indent);
        indent += 1;

        if (mConsolidateCritical) {
            log.add(LogType.MSG_CON_RECURSIVE, indent);
            return new ConsolidateResult(ConsolidateResult.RESULT_OK, log);
        }

        progress.setProgress(R.string.progress_con_saving, 0, 100);

        // The consolidate operation can never be cancelled!
        progress.setPreventCancel();

        // 1a. fetch own public keyrings into a cache file
        try {
            log.add(LogType.MSG_CON_SAVE_OWN_PUBLIC, indent);
            indent += 1;

            Cursor cursor = mContentResolver.query(KeyRings.buildUnifiedKeyRingsUri(),
                    new String[]{KeyRings.PUBKEY_DATA, KeyRings.HAS_ANY_SECRET, KeyRings.MASTER_KEY_ID},
                    KeyRings.HAS_ANY_SECRET + "!=0", null, null);
            cacheKeyRings(cursor, 0, 2, "consolidate_own_public.pcl");
            //noinspection ConstantConditions, null is caught below
            cursor.close();

        } catch (NullPointerException e) {
            log.add(LogType.MSG_CON_ERROR_DB, indent);
            return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);

        } catch (IOException e) {
            Log.e(Constants.TAG, "error saving own public", e);
            log.add(LogType.MSG_CON_ERROR_IO_OWN_PUBLIC, indent);
            return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);

        } finally {
            indent -= 1;
        }

        progress.setProgress(R.string.progress_con_saving, 1, 100);

        // 1b. fetch all encrypted secret keyRing blocks and subKey type into a cache file
        try {
            log.add(LogType.MSG_CON_SAVE_SECRET, indent);
            indent += 1;

            cacheAllSecretKeyRingData("consolidate_secret.pcl");

        } catch (NullPointerException e) {
            log.add(LogType.MSG_CON_ERROR_DB, indent);
            return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);

        } catch (IOException e) {
            Log.e(Constants.TAG, "error saving secret", e);
            log.add(LogType.MSG_CON_ERROR_IO_SECRET, indent);
            return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);

        } finally {
            indent -= 1;
        }

        progress.setProgress(R.string.progress_con_saving, 3, 100);

        // 1c. fetch all public keyrings into a cache file
        try {
            log.add(LogType.MSG_CON_SAVE_FOREIGN_PUBLIC, indent);
            indent += 1;

            // Importing own public rings and all public rings ==
            // importing own public rings and foreign public rings
            Cursor cursor = mContentResolver.query(
                    KeyRingData.buildPublicKeyRingUri(),
                    new String[]{KeyRingData.KEY_RING_DATA, KeyRingData.MASTER_KEY_ID}, null, null, null);
            cacheKeyRings(cursor, 0, 1, "consolidate_foreign_public.pcl");
            //noinspection ConstantConditions, null is caught below
            cursor.close();

        } catch (NullPointerException e) {
            log.add(LogType.MSG_CON_ERROR_DB, indent);
            return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);

        } catch (IOException e) {
            Log.e(Constants.TAG, "error saving public", e);
            log.add(LogType.MSG_CON_ERROR_IO_FOREIGN_PUBLIC, indent);
            return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);

        } finally {
            indent -= 1;
        }

        log.add(LogType.MSG_CON_CRITICAL_IN, indent);
        Preferences.getPreferences(mContext).setCachedConsolidate(true);

        return consolidateDatabaseStep2(log, indent, progress, false);
    }

    private void cacheKeyRings(final Cursor cursor, final int keyRingPosition,
                               final int masterKeyIdPosition, String fileName)
            throws IOException, NullPointerException {
        // No keys existing might be a legitimate option, we write an empty file in that case
        cursor.moveToFirst();
        ParcelableFileCache<ParcelableKeyRing> cache = new ParcelableFileCache<>(mContext, fileName);

        cache.writeCache(cursor.getCount(), new Iterator<ParcelableKeyRing>() {
            ParcelableKeyRing ring;

            @Override
            public boolean hasNext() {
                if (ring != null) {
                    return true;
                }
                if (cursor.isAfterLast()) {
                    return false;
                }
                ring = new ParcelableKeyRing(
                        cursor.getBlob(keyRingPosition),
                        cursor.getLong(masterKeyIdPosition));
                cursor.moveToNext();
                return true;
            }

            @Override
            public ParcelableKeyRing next() {
                try {
                    return ring;
                } finally {
                    ring = null;
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        });
    }

    /**
     * Caches all secret keyrings owned to a file, together with subkey related data obtainable only
     * by reading the secret keyrings
     */
    private boolean cacheAllSecretKeyRingData(String fileName)
            throws IOException, NullPointerException{
        // No keys existing might be a legitimate option, we write an empty file in that case
        final Cursor cursor = mContentResolver.query(KeyRingData.buildSecretKeyRingUri(),
                new String[]{KeyRings.MASTER_KEY_ID}, null, null, null);

        if (cursor == null) {
            return false;
        } else {
            ParcelableFileCache<EncryptedSecretKeyRing> cache =
                    new ParcelableFileCache<>(mContext, fileName);

            ArrayList<EncryptedSecretKeyRing> secretKeys = new ArrayList<>();
            while (cursor.moveToNext()) {
                long masterKeyId = cursor.getLong(0);
                secretKeys.add(mWriter.getSecretKeyRingData(masterKeyId));
            }
            cursor.close();
            cache.writeCache(cursor.getCount(),secretKeys.iterator());
            return true;
        }

    }

    @NonNull
    public ConsolidateResult consolidateDatabaseStep2(Progressable progress) {
        return consolidateDatabaseStep2(new OperationLog(), 0, progress, true);
    }

    private static boolean mConsolidateCritical = false;
    protected boolean mConsolidatingOwnPublic = false;

    @NonNull
    private ConsolidateResult consolidateDatabaseStep2(
            OperationLog log, int indent, Progressable progress, boolean recovery) {

        synchronized (ProviderHelper.class) {
            if (mConsolidateCritical) {
                log.add(LogType.MSG_CON_ERROR_CONCURRENT, indent);
                return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);
            }
            mConsolidateCritical = true;
        }

        try {
            Preferences prefs = Preferences.getPreferences(mContext);

            if (recovery) {
                log.add(LogType.MSG_CON_RECOVER, indent);
                indent += 1;
            }

            if (!prefs.getCachedConsolidate()) {
                log.add(LogType.MSG_CON_ERROR_BAD_STATE, indent);
                return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);
            }

            // 2. wipe database (IT'S DANGEROUS)

            // first, backup our list of updated key times
            ArrayList<ContentValues> updatedKeysValues = new ArrayList<>();
            final int INDEX_MASTER_KEY_ID = 0;
            final int INDEX_LAST_UPDATED = 1;
            Cursor lastUpdatedCursor = mContentResolver.query(
                    UpdatedKeys.CONTENT_URI,
                    new String[]{
                            UpdatedKeys.MASTER_KEY_ID,
                            UpdatedKeys.LAST_UPDATED
                    },
                    null, null, null);
            while (lastUpdatedCursor.moveToNext()) {
                ContentValues values = new ContentValues();
                values.put(UpdatedKeys.MASTER_KEY_ID,
                        lastUpdatedCursor.getLong(INDEX_MASTER_KEY_ID));
                values.put(UpdatedKeys.LAST_UPDATED,
                        lastUpdatedCursor.getLong(INDEX_LAST_UPDATED));
                updatedKeysValues.add(values);
            }
            lastUpdatedCursor.close();

            log.add(LogType.MSG_CON_DB_CLEAR, indent);
            mContentResolver.delete(KeyRings.buildUnifiedKeyRingsUri(), null, null);

            ParcelableFileCache<ParcelableKeyRing> cacheOwnPublic, cacheAllPublic;
            ParcelableFileCache<EncryptedSecretKeyRing> cacheSecret;

            // Set flag that we have a cached consolidation here
            try {
                cacheOwnPublic = new ParcelableFileCache<>(mContext, "consolidate_own_public.pcl");
                IteratorWithSize<ParcelableKeyRing> itOwnPublics = cacheOwnPublic.readCache(false);
                int numPublic = itOwnPublics.getSize();

                log.add(LogType.MSG_CON_REIMPORT_OWN_PUBLIC, indent, numPublic);
                indent += 1;

                // 3. Re-Import own public keyrings from cache
                mConsolidatingOwnPublic = true;
                if (numPublic > 0) {
                    ImportKeyResult result = new ImportOperation(mContext, this,
                            new ProgressFixedScaler(progress, 10, 20, 100, R.string.progress_con_reimport))
                            .serialKeyRingImport(itOwnPublics, numPublic, null, null, new ArrayList<KeyringPassphrases>());
                    log.add(result, indent);
                } else {
                    log.add(LogType.MSG_CON_REIMPORT_PUBLIC_SKIP, indent);
                }

            } catch (IOException e) {
                Log.e(Constants.TAG, "error importing own public", e);
                log.add(LogType.MSG_CON_ERROR_OWN_PUBLIC, indent);
                return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);
            } finally {
                mConsolidatingOwnPublic = false;
                indent -= 1;
            }

            try {
                cacheSecret = new ParcelableFileCache<>(mContext, "consolidate_secret.pcl");
                IteratorWithSize<EncryptedSecretKeyRing> itSecrets = cacheSecret.readCache(false);
                int numSecret = itSecrets.getSize();
                log.add(LogType.MSG_CON_REIMPORT_SECRET, indent, numSecret);
                indent += 1;

                // 4. Re-Import secret keyrings & related subkey data from cache
                if (numSecret > 0) {
                    OperationResult result = mWriter.writeSecretKeyRingsToDb(itSecrets, numSecret);
                    log.add(result, indent);
                } else {
                    log.add(LogType.MSG_CON_REIMPORT_SECRET_SKIP, indent);
                }

            } catch (IOException e) {
                Log.e(Constants.TAG, "error importing secret", e);
                log.add(LogType.MSG_CON_ERROR_SECRET, indent);
                return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);
            } finally {
                indent -= 1;
            }

            try {

                cacheAllPublic = new ParcelableFileCache<>(mContext, "consolidate_foreign_public.pcl");
                IteratorWithSize<ParcelableKeyRing> itPublics = cacheAllPublic.readCache();
                int numPublics = itPublics.getSize();

                log.add(LogType.MSG_CON_REIMPORT_FOREIGN_PUBLIC, indent, numPublics);
                indent += 1;

                // 5. Re-Import foreign public keyrings from cache
                if (numPublics > 0) {
                    ImportKeyResult result = new ImportOperation(mContext, this,
                            new ProgressFixedScaler(progress, 25, 99, 100, R.string.progress_con_reimport))
                            .serialKeyRingImport(itPublics, numPublics, null, null, new ArrayList<KeyringPassphrases>());
                    log.add(result, indent);
                    // re-insert our backed up list of updated key times
                    // TODO: can this cause issues in case a public key re-import failed?
                    mContentResolver.bulkInsert(UpdatedKeys.CONTENT_URI,
                            updatedKeysValues.toArray(new ContentValues[updatedKeysValues.size()]));
                } else {
                    log.add(LogType.MSG_CON_REIMPORT_PUBLIC_SKIP, indent);
                }

            } catch (IOException e) {
                Log.e(Constants.TAG, "error importing all public", e);
                log.add(LogType.MSG_CON_ERROR_FOREIGN_PUBLIC, indent);
                return new ConsolidateResult(ConsolidateResult.RESULT_ERROR, log);
            } finally {
                indent -= 1;
            }

            log.add(LogType.MSG_CON_CRITICAL_OUT, indent);
            Preferences.getPreferences(mContext).setCachedConsolidate(false);

            // 6. Delete caches
            try {
                log.add(LogType.MSG_CON_DELETE_OWN_PUBLIC, indent);
                indent += 1;
                cacheOwnPublic.delete();
            } catch (IOException e) {
                // doesn't /really/ matter
                Log.e(Constants.TAG, "IOException during delete of own public cache", e);
                log.add(LogType.MSG_CON_WARN_DELETE_OWN_PUBLIC, indent);
            } finally {
                indent -= 1;
            }

            try {
                log.add(LogType.MSG_CON_DELETE_SECRET, indent);
                indent += 1;
                cacheSecret.delete();
            } catch (IOException e) {
                // doesn't /really/ matter
                Log.e(Constants.TAG, "IOException during delete of secret cache", e);
                log.add(LogType.MSG_CON_WARN_DELETE_SECRET, indent);
            } finally {
                indent -= 1;
            }

            try {
                log.add(LogType.MSG_CON_DELETE_FOREIGN_PUBLIC, indent);
                indent += 1;
                cacheAllPublic.delete();
            } catch (IOException e) {
                // doesn't /really/ matter
                Log.e(Constants.TAG, "IOException during deletion of public cache", e);
                log.add(LogType.MSG_CON_WARN_DELETE_FOREIGN_PUBLIC, indent);
            } finally {
                indent -= 1;
            }

            progress.setProgress(100, 100);
            log.add(LogType.MSG_CON_SUCCESS, indent);

            return new ConsolidateResult(ConsolidateResult.RESULT_OK, log);

        } finally {
            mConsolidateCritical = false;
        }

    }
}
