/*
 * Copyright (C) 2013-2014 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.ui.adapter;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.support.v4.app.FragmentActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.databinding.ImportKeysListItemBinding;
import org.sufficientlysecure.keychain.keyimport.ImportKeysListEntry;
import org.sufficientlysecure.keychain.keyimport.ParcelableKeyRing;
import org.sufficientlysecure.keychain.keyimport.processing.ImportKeysListener;
import org.sufficientlysecure.keychain.keyimport.processing.ImportKeysOperationCallback;
import org.sufficientlysecure.keychain.keyimport.processing.ImportKeysResultListener;
import org.sufficientlysecure.keychain.operations.ImportOperation;
import org.sufficientlysecure.keychain.operations.results.ImportKeyResult;
import org.sufficientlysecure.keychain.pgp.CanonicalizedKeyRing;
import org.sufficientlysecure.keychain.pgp.KeyRing;
import org.sufficientlysecure.keychain.pgp.exception.PgpKeyNotFoundException;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.provider.ProviderHelper;
import org.sufficientlysecure.keychain.service.ImportKeyringParcel;
import org.sufficientlysecure.keychain.ui.ViewKeyActivity;
import org.sufficientlysecure.keychain.ui.base.CryptoOperationHelper;
import org.sufficientlysecure.keychain.ui.util.KeyFormattingUtils;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.util.Log;
import org.sufficientlysecure.keychain.util.ParcelableFileCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ImportKeysAdapter extends RecyclerView.Adapter<ImportKeysAdapter.ViewHolder> implements ImportKeysResultListener {

    private FragmentActivity mActivity;
    private ImportKeysResultListener mListener;
    private boolean mNonInteractive;

    private List<ImportKeysListEntry> mData;
    private KeyState[] mKeyStates;
    private int mCurrent;

    private ProviderHelper mProviderHelper;

    public ImportKeysAdapter(FragmentActivity activity, ImportKeysListener listener,
                             boolean nonInteractive) {

        mActivity = activity;
        mListener = listener;
        mNonInteractive = nonInteractive;

        mProviderHelper = new ProviderHelper(activity);
    }

    public void setData(List<ImportKeysListEntry> data) {
        mData = data;

        mKeyStates = new KeyState[data.size()];
        for (int i = 0; i < mKeyStates.length; i++) {
            mKeyStates[i] = new KeyState();

            ImportKeysListEntry entry = mData.get(i);
            long keyId = KeyFormattingUtils.convertKeyIdHexToKeyId(entry.getKeyIdHex());
            try {
                KeyRing keyRing;
                if (entry.isSecretKey()) {
                    keyRing = mProviderHelper.getCanonicalizedSecretKeyRing(keyId);
                } else {
                    keyRing = mProviderHelper.getCachedPublicKeyRing(keyId);
                }
                mKeyStates[i].mAlreadyPresent = true;
                mKeyStates[i].mVerified = keyRing.getVerified() > 0;
            } catch (ProviderHelper.NotFoundException | PgpKeyNotFoundException ignored) {
            }
        }

        // If there is only one key, get it automatically
        if (mData.size() == 1) {
            mCurrent = 0;
            getKey(mData.get(0), true);
        }

        notifyDataSetChanged();
    }

    public void clearData() {
        mData = null;
        mKeyStates = null;
        notifyDataSetChanged();
    }

    /**
     * This method returns a list of all selected entries, with public keys sorted
     * before secret keys, see ImportOperation for specifics.
     *
     * @see ImportOperation
     */
    public List<ImportKeysListEntry> getEntries() {
        ArrayList<ImportKeysListEntry> result = new ArrayList<>();
        ArrayList<ImportKeysListEntry> secrets = new ArrayList<>();
        if (mData == null) {
            return result;
        }
        for (ImportKeysListEntry entry : mData) {
            // add this entry to either the secret or the public list
            (entry.isSecretKey() ? secrets : result).add(entry);
        }
        // add secret keys at the end of the list
        result.addAll(secrets);
        return result;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ImportKeysListItemBinding b;

        public ViewHolder(View view) {
            super(view);
            b = DataBindingUtil.bind(view);
            b.setNonInteractive(mNonInteractive);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mActivity);
        return new ViewHolder(inflater.inflate(R.layout.import_keys_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        final ImportKeysListItemBinding b = holder.b;
        final ImportKeysListEntry entry = mData.get(position);
        b.setEntry(entry);

        final KeyState keyState = mKeyStates[position];
        final boolean downloaded = keyState.mDownloaded;
        final boolean showed = keyState.mShowed;

        b.card.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!downloaded) {
                    mCurrent = position;
                    getKey(entry, true);
                } else {
                    changeState(position, !showed);
                }
            }
        });

        b.extra.importKey.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                getKey(entry, false);
            }
        });

        b.extra.showKey.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                long keyId = KeyFormattingUtils.convertKeyIdHexToKeyId(entry.getKeyIdHex());
                Intent intent = new Intent(mActivity, ViewKeyActivity.class);
                intent.setData(KeyRings.buildGenericKeyRingUri(keyId));
                mActivity.startActivity(intent);
            }
        });

        b.extraContainer.setVisibility(showed ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return mData != null ? mData.size() : 0;
    }

    public void getKey(ImportKeysListEntry entry, boolean skipSave) {
        ImportKeyringParcel inputParcel = prepareKeyOperation(entry, skipSave);
        ImportKeysResultListener listener = skipSave ? this : mListener;
        ImportKeysOperationCallback cb = new ImportKeysOperationCallback(listener, inputParcel);
        int message = skipSave ? R.string.progress_downloading : R.string.progress_importing;
        CryptoOperationHelper opHelper = new CryptoOperationHelper<>(1, mActivity, cb, message);
        opHelper.cryptoOperation();
    }

    private ImportKeyringParcel prepareKeyOperation(ImportKeysListEntry entry, boolean skipSave) {
        ArrayList<ParcelableKeyRing> keysList = null;
        String keyserver = null;

        ParcelableKeyRing keyRing = entry.getParcelableKeyRing();
        if (keyRing.mBytes != null) {
            // instead of giving the entries by Intent extra, cache them into a
            // file to prevent Java Binder problems on heavy imports
            // read FileImportCache for more info.
            try {
                // We parcel this iteratively into a file - anything we can
                // display here, we should be able to import.
                ParcelableFileCache<ParcelableKeyRing> cache =
                        new ParcelableFileCache<>(mActivity, ImportOperation.CACHE_FILE_NAME);
                cache.writeCache(keyRing);
            } catch (IOException e) {
                Log.e(Constants.TAG, "Problem writing cache file", e);
                Notify.create(mActivity, "Problem writing cache file!", Notify.Style.ERROR).show();
            }
        } else {
            keysList = new ArrayList<>();
            keysList.add(keyRing);
            keyserver = entry.getKeyserver();
        }

        return new ImportKeyringParcel(keysList, keyserver, skipSave);
    }

    @Override
    public void handleResult(ImportKeyResult result) {
        boolean resultStatus = result.success();
        Log.e(Constants.TAG, "getKey result: " + resultStatus);
        if (resultStatus) {
            ArrayList<CanonicalizedKeyRing> canKeyRings = result.mCanonicalizedKeyRings;
            if (canKeyRings.size() == 1) {
                CanonicalizedKeyRing keyRing = canKeyRings.get(0);
                Log.e(Constants.TAG, "Key ID: " + keyRing.getMasterKeyId() +
                        "| isRev: " + keyRing.isRevoked() + "| isExp: " + keyRing.isExpired());

                ImportKeysListEntry entry = mData.get(mCurrent);
                entry.setUpdated(result.isOkUpdated());

                mergeEntryWithKey(entry, keyRing);

                mKeyStates[mCurrent].mDownloaded = true;
                changeState(mCurrent, true);
            } else {
                throw new RuntimeException("getKey retrieved more than one key ("
                        + canKeyRings.size() + ")");
            }
        } else {
            result.createNotify(mActivity).show();
        }
    }

    private void mergeEntryWithKey(ImportKeysListEntry entry, CanonicalizedKeyRing keyRing) {
        entry.setRevoked(keyRing.isRevoked());
        entry.setExpired(keyRing.isExpired());

        Date expectedDate = entry.getDate();
        Date creationDate = keyRing.getCreationDate();
        if (expectedDate == null) {
            entry.setDate(creationDate);
        } else if (!expectedDate.equals(creationDate)) {
            throw new AssertionError("Creation date doesn't match the expected one");
        }
        entry.setKeyId(keyRing.getMasterKeyId());

        ArrayList<String> realUserIdsPlusKeybase = keyRing.getUnorderedUserIds();
        realUserIdsPlusKeybase.addAll(entry.getKeybaseUserIds());
        entry.setUserIds(realUserIdsPlusKeybase);
    }

    private class KeyState {
        public boolean mAlreadyPresent = false;
        public boolean mVerified = false;

        public boolean mDownloaded = false;
        public boolean mShowed = false;
    }

    private void changeState(int position, boolean showed) {
        KeyState keyState = mKeyStates[position];
        keyState.mShowed = showed;
        notifyItemChanged(position);
    }

}
