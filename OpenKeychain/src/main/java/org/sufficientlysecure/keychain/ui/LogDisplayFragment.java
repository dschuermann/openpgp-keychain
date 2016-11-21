/*
 * Copyright (C) 2014 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.tonicartos.superslim.LayoutManager;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.operations.results.OperationResult;
import org.sufficientlysecure.keychain.operations.results.OperationResult.LogEntryParcel;
import org.sufficientlysecure.keychain.operations.results.OperationResult.LogLevel;
import org.sufficientlysecure.keychain.operations.results.OperationResult.SubLogEntryParcel;
import org.sufficientlysecure.keychain.provider.TemporaryFileProvider;
import org.sufficientlysecure.keychain.ui.adapter.NestedLogAdapter;
import org.sufficientlysecure.keychain.ui.dialog.ShareLogDialogFragment;
import org.sufficientlysecure.keychain.ui.util.FormattingUtils;
import org.sufficientlysecure.keychain.ui.util.Notify;
import org.sufficientlysecure.keychain.ui.util.Notify.Style;
import org.sufficientlysecure.keychain.ui.util.recyclerview.RecyclerFragment;

import java.io.IOException;
import java.io.OutputStream;


public class LogDisplayFragment extends RecyclerFragment<NestedLogAdapter>
        implements NestedLogAdapter.LogActionListener {
    private OperationResult mResult;

    public static final String EXTRA_RESULT = "log";
    private Uri mLogTempFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Intent intent = getActivity().getIntent();
        if (intent == null) {
            getActivity().finish();
            return;
        }

        if (savedInstanceState != null) {
            mResult = savedInstanceState.getParcelable(EXTRA_RESULT);
        } else {
            mResult = intent.getParcelableExtra(EXTRA_RESULT);
        }

        if (mResult == null) {
            getActivity().finish();
            return;
        }

        NestedLogAdapter adapter = new NestedLogAdapter(getContext(), mResult.getLog());
        adapter.setListener(this);
        setAdapter(adapter);

        setLayoutManager(new LayoutManager(getContext()));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // need to parcel this again, logs are only single-instance parcelable
        outState.putParcelable(EXTRA_RESULT, mResult);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.log_display, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_log_display_export_log:
                shareLog();
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void shareLog() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        String log = mResult.getLog().getPrintableOperationLog(getResources(), 0);

        // if there is no log temp file yet, create one
        if (mLogTempFile == null) {
            mLogTempFile = TemporaryFileProvider.createFile(getActivity(), "openkeychain_log.txt", "text/plain");
            try {
                OutputStream outputStream = activity.getContentResolver().openOutputStream(mLogTempFile);
                outputStream.write(log.getBytes());
            } catch (IOException | NullPointerException e) {
                Notify.create(activity, R.string.error_log_share_internal, Style.ERROR).show();
                return;
            }
        }


        ShareLogDialogFragment shareLogDialog = ShareLogDialogFragment.newInstance(mLogTempFile);
        shareLogDialog.show(getActivity().getSupportFragmentManager(), "shareLogDialog");
    }

    @Override
    public void onSubEntryClicked(SubLogEntryParcel subLogEntryParcel) {
        Intent intent = new Intent(getActivity(), LogDisplayActivity.class);
        intent.putExtra(LogDisplayFragment.EXTRA_RESULT, subLogEntryParcel.getSubResult());
        startActivity(intent);
    }
}
