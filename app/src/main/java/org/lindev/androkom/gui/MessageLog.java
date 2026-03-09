package org.lindev.androkom.gui;

import org.lindev.androkom.App;
import org.lindev.androkom.AsyncMessages;
import org.lindev.androkom.LocalBinder;
import org.lindev.androkom.AsyncMessages.AsyncMessageSubscriber;
import org.lindev.androkom.KomServer;
import org.lindev.androkom.R;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class MessageLog extends ListActivity implements AsyncMessageSubscriber, ServiceConnection
{
    public static final String TAG = "Androkom";

    private ArrayAdapter<String> mAdapter;
    private int mLogIndex;
    private KomServer mKom;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.message_main);

        final View root = findViewById(R.id.message_log_root);

        final int paddingLeft = root.getPaddingLeft();
        final int paddingTop = root.getPaddingTop();
        final int paddingRight = root.getPaddingRight();
        final int paddingBottom = root.getPaddingBottom();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    paddingLeft + systemBars.left,
                    paddingTop + systemBars.top,
                    paddingRight + systemBars.right,
                    paddingBottom + systemBars.bottom
            );
            return insets;
        });

        mAdapter = new ArrayAdapter<String>(this, R.layout.message_log);
        setListAdapter(mAdapter);
        mLogIndex = 0;

        getApp().doBindService(this);
    }

    @Override
    public void onDestroy() {
        getApp().doUnbindService(this);
        super.onDestroy();
    }

    private void update() {
        if (mKom != null) {
            final AsyncMessages am = mKom.asyncMessagesHandler;

            while (mLogIndex < am.getLog().size()) {
                final Message msg = am.getLog().get(mLogIndex++);
                String msgStr = am.messageAsString(msg);
                if (msgStr != null) {
                    mAdapter.add(msgStr);
                }
            }

            final int count = getListView().getCount();
            if (count > 0) {
                mAdapter.notifyDataSetChanged();
                getListView().setSelection(count - 1);
            }
        }
    }

    public void asyncMessage(final Message msg) {
        update();
    }

    App getApp() {
        return (App) getApplication();
    }

    public void onServiceConnected(final ComponentName name, final IBinder service) {
        mKom = ((LocalBinder<KomServer>) service).getService();
        mKom.addAsyncSubscriber(this);
        update();
    }

    public void onServiceDisconnected(final ComponentName name) {
        mKom = null;
        mLogIndex = 0;
        mAdapter.clear();
        mAdapter.notifyDataSetChanged();
    }
}
