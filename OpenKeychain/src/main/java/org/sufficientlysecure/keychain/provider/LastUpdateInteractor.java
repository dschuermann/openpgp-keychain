package org.sufficientlysecure.keychain.provider;


import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.Nullable;

import org.sufficientlysecure.keychain.provider.KeychainContract.UpdatedKeys;
import org.sufficientlysecure.keychain.provider.KeychainDatabase.Tables;


public class LastUpdateInteractor {
    private final ContentResolver contentResolver;
    private final DatabaseNotifyManager databaseNotifyManager;


    public static LastUpdateInteractor create(Context context) {
        return new LastUpdateInteractor(context.getContentResolver(), DatabaseNotifyManager.create(context));
    }

    private LastUpdateInteractor(ContentResolver contentResolver, DatabaseNotifyManager databaseNotifyManager) {
        this.contentResolver = contentResolver;
        this.databaseNotifyManager = databaseNotifyManager;
    }

    @Nullable
    public Boolean getSeenOnKeyservers(long masterKeyId) {
        Cursor cursor = contentResolver.query(
                UpdatedKeys.CONTENT_URI,
                new String[] { UpdatedKeys.SEEN_ON_KEYSERVERS },
                Tables.UPDATED_KEYS + "." + UpdatedKeys.MASTER_KEY_ID + " = ?",
                new String[] { "" + masterKeyId },
                null
        );
        if (cursor == null) {
            return null;
        }

        Boolean seenOnKeyservers;
        try {
            if (!cursor.moveToNext()) {
                return null;
            }
            seenOnKeyservers = cursor.isNull(0) ? null : cursor.getInt(0) != 0;
        } finally {
            cursor.close();
        }
        return seenOnKeyservers;
    }

    public void resetAllLastUpdatedTimes() {
        ContentValues values = new ContentValues();
        values.putNull(UpdatedKeys.LAST_UPDATED);
        values.putNull(UpdatedKeys.SEEN_ON_KEYSERVERS);
        contentResolver.update(UpdatedKeys.CONTENT_URI, values, null, null);
    }

    public Uri renewKeyLastUpdatedTime(long masterKeyId, boolean seenOnKeyservers) {
        boolean isFirstKeyserverStatusCheck = getSeenOnKeyservers(masterKeyId) == null;

        ContentValues values = new ContentValues();
        values.put(UpdatedKeys.MASTER_KEY_ID, masterKeyId);
        values.put(UpdatedKeys.LAST_UPDATED, GregorianCalendar.getInstance().getTimeInMillis() / 1000);
        if (seenOnKeyservers || isFirstKeyserverStatusCheck) {
            values.put(UpdatedKeys.SEEN_ON_KEYSERVERS, seenOnKeyservers);
        }

        // this will actually update/replace, doing the right thing™ for seenOnKeyservers value
        // see `KeychainProvider.insert()`
        Uri insert = contentResolver.insert(UpdatedKeys.CONTENT_URI, values);
        databaseNotifyManager.notifyKeyserverStatusChange(masterKeyId);
        return insert;
    }

    public List<byte[]> getFingerprintsForKeysOlderThan(long olderThan, TimeUnit timeUnit) {
        Cursor outdatedKeysCursor = contentResolver.query(
                KeychainContract.UpdatedKeys.CONTENT_URI,
                new String[] { KeychainContract.UpdatedKeys.FINGERPRINT, },
                KeychainContract.UpdatedKeys.LAST_UPDATED + " < ?",
                new String[] { Long.toString(timeUnit.toSeconds(olderThan)) },
                null
        );

        List<byte[]> fingerprintList = new ArrayList<>();
        if (outdatedKeysCursor == null) {
            return fingerprintList;
        }

        while (outdatedKeysCursor.moveToNext()) {
            byte[] fingerprint = outdatedKeysCursor.getBlob(0);
            fingerprintList.add(fingerprint);
        }
        outdatedKeysCursor.close();

        return fingerprintList;
    }
}
