package org.lindev.androkom.gui;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import nu.dll.lyskom.ConfInfo;
import nu.dll.lyskom.Conference;
import nu.dll.lyskom.KomToken;
import nu.dll.lyskom.RpcFailure;
import nu.dll.lyskom.Text;

import org.lindev.androkom.App;
import org.lindev.androkom.KomServer;
import org.lindev.androkom.LocalBinder;
import org.lindev.androkom.LookupNameTask;
import org.lindev.androkom.LookupNameTask.LookupType;
import org.lindev.androkom.LookupNameTask.RunOnSuccess;
import org.lindev.androkom.R;
import org.lindev.androkom.text.CreateTextTask;
import org.lindev.androkom.text.CreateTextTask.CreateTextRunnable;
import org.lindev.androkom.text.Recipient;
import org.lindev.androkom.text.Recipient.RecipientType;
import org.lindev.androkom.text.SendTextTask;
import android.app.AlertDialog;
import android.app.TabActivity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.Toast;
import android.widget.TabHost.TabSpec;

public class TextCreator extends TabActivity implements ServiceConnection {
    public static final String TAG = "Androkom TextCreator";

    protected static final int RESULT_SPEECH = 1;
    
    private static final String TEXT_TAB_TAG = "text-tab-tag";
    private static final String RECIPIENTS_TAB_TAG = "recipients-tab-tag";

    private static final String INTENT_INITIAL_RECIPIENTS_ADDED = "initial-recipients-added";
    public static final String INTENT_REPLY_TO = "in-reply-to";
    public static final String INTENT_SUBJECT = "subject-line";
    public static final String INTENT_RECIPIENT = "recipient";
    public static final String INTENT_IS_MAIL = "is-mail";

    private KomServer mKom = null;
    private List<Recipient> mRecipients;
    private ArrayAdapter<Recipient> mAdapter;
    private int mReplyTo;
    private EditText mSubject;
    private EditText mBody;
    private double mLat, mLon, mPrecision=-1;
    LocationManager mlocManager=null;
    LocationListener mlocListener=null;
    
    public class CopyRecipientsTask extends
            AsyncTask<Integer, Void, List<Recipient>> {
        @Override
        protected List<Recipient> doInBackground(final Integer... args) {
            final List<Recipient> recipients = new ArrayList<Recipient>();

            try {
                final Text text = mKom.getTextbyNo(args[0]);
                if (text == null) {
                    return null;
                }
                for (int recip : text.getRecipients()) {
                    final Conference confStat = mKom.getConfStat(recip);
                    if (confStat != null) {
                        if (confStat.getConfInfo().confType.original()) {
                            recip = confStat.getSuperConf();
                        }
                        final String name = mKom.getConferenceName(recip);
                        if (name != null) {
                            recipients.add(new Recipient(mKom
                                    .getApplicationContext(), recip, name,
                                    RecipientType.RECP_TO));
                        }
                    }
                }
            } catch (final RpcFailure e) {
                return null;
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return recipients;
        }

        @Override
        public void onPostExecute(final List<Recipient> recipients) {
            if (recipients != null) {
                for (final Recipient recipient : recipients) {
                    add(recipient);
                }
            }
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_new_text_layout);

        /* Use the LocationManager class to obtain GPS locations */

        //mlocManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        //mlocListener = new MyLocationListener();
        //mlocManager.requestLocationUpdates( LocationManager.GPS_PROVIDER, 0, 0, mlocListener);
        
        initializeCommon();
        initializeRecipients();
        initializeTabs();
        initializeButtons();

        getApp().doBindService(this);

        ImageButton btnSpeak = (ImageButton) findViewById(R.id.btnSpeak);
        
        btnSpeak.setOnClickListener(new View.OnClickListener() {
 
            public void onClick(View v) {
 
                Intent intent = new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
 
                //intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM );
 
                try {
                    startActivityForResult(intent, RESULT_SPEECH);
                    //txtText.setText("");
                } catch (ActivityNotFoundException a) {
                    Toast t = Toast.makeText(getApplicationContext(),
                            "Opps! Your device doesn't support Speech to Text",
                            Toast.LENGTH_SHORT);
                    t.show();
                }
            }
        });
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case RESULT_SPEECH: {
            if (resultCode == RESULT_OK && null != data) {
                // Only in Android 4.0: data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES);
                
                ArrayList<String> textLista = data
                        .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                String text = textLista.get(0)+" ";
                int start = mBody.getSelectionStart();
                int end = mBody.getSelectionEnd();
                mBody.getText().replace(Math.min(start, end),
                        Math.max(start, end), text, 0, text.length());
            }
            break;
        }

        }
    }
    
    @Override
    protected void onDestroy() {
        if(mlocManager != null) {
            mlocManager.removeUpdates(mlocListener);
        }
        getApp().doUnbindService(this);
        super.onDestroy();
    }

    private void initializeCommon() {
        mSubject = (EditText) findViewById(R.id.subject);

        mBody = (EditText) findViewById(R.id.body);
        //registerForContextMenu (mBody);
        mBody.setOnCreateContextMenuListener(this);
        
        mReplyTo = getIntent().getIntExtra(INTENT_REPLY_TO, -1);
        if (mReplyTo > 0) {
            setTitle(getString(R.string.creator_comment_to) + mReplyTo);
        }
        else {
            setTitle(getString(R.string.creator_new_text));
        }
    }

    @SuppressWarnings("unchecked")
    private void initializeRecipients() {
        mAdapter = new ArrayAdapter<Recipient>(this, R.layout.message_log);
        mRecipients = (List<Recipient>) getLastNonConfigurationInstance();
        if (mRecipients == null) {
            mRecipients = new ArrayList<Recipient>();
        }
        for (final Recipient recipient : mRecipients) {
            mAdapter.add(recipient);
        }
        mAdapter.notifyDataSetChanged();
    }

    private void initializeTabs() {
        final TabHost tabHost = getTabHost();
        final TabSpec textTab = tabHost.newTabSpec(TEXT_TAB_TAG);
        textTab.setIndicator(getString(R.string.creator_text_title));
        textTab.setContent(R.id.create_text);
        tabHost.addTab(textTab);

        final TabSpec recipientsTab = tabHost.newTabSpec(RECIPIENTS_TAB_TAG);
        recipientsTab.setIndicator(getString(R.string.creator_recipents_title));
        recipientsTab.setContent(R.id.recipients_view);
        tabHost.addTab(recipientsTab);

        final ListView recipientsView = (ListView) findViewById(R.id.recipients);
        recipientsView.setAdapter(mAdapter);
        recipientsView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(final AdapterView<?> av, final View view, final int position, final long id) {
                final Recipient recipient = (Recipient) av.getItemAtPosition(position);
                showRemoveRecipientDialog(recipient);
            }
        });
    }

    private void initializeButtons() {
        final Button toButton = (Button) findViewById(R.id.add_to);
        final Button ccButton = (Button) findViewById(R.id.add_cc);
        final Button bccButton = (Button) findViewById(R.id.add_bcc);
        final Button sendButton = (Button) findViewById(R.id.send);
        final Button cancelButton = (Button) findViewById(R.id.cancel);

        toButton.setEnabled(false);
        ccButton.setEnabled(false);
        bccButton.setEnabled(false);
        sendButton.setEnabled(false);
        cancelButton.setEnabled(true);

        final View.OnClickListener buttonClickListener = new View.OnClickListener() {
            public void onClick(final View view) {
                if (view == toButton) {
                    showAddRecipientDialog(RecipientType.RECP_TO, LookupType.LOOKUP_BOTH);
                }
                else if (view == ccButton) {
                    showAddRecipientDialog(RecipientType.RECP_CC, LookupType.LOOKUP_BOTH);
                }
                else if (view == bccButton) {
                    showAddRecipientDialog(RecipientType.RECP_BCC, LookupType.LOOKUP_BOTH);
                }
                else if (view == sendButton) {
                    sendMessage();
                }
                else if (view == cancelButton) {
                    finish();
                }
            }
        };

        toButton.setOnClickListener(buttonClickListener);
        ccButton.setOnClickListener(buttonClickListener);
        bccButton.setOnClickListener(buttonClickListener);
        sendButton.setOnClickListener(buttonClickListener);
        cancelButton.setOnClickListener(buttonClickListener);
    }

    private void enableButtons() {
        ((Button) findViewById(R.id.add_to)).setEnabled(true);
        ((Button) findViewById(R.id.add_cc)).setEnabled(true);
        ((Button) findViewById(R.id.add_bcc)).setEnabled(true);
        ((Button) findViewById(R.id.send)).setEnabled(true);
    }

    /**
     * The menu key has been pressed, instantiate the requested
     * menu.
     */
    @Override 
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.textcreator, menu);
        return true;
    }

//    public void onPopulateContextMenu(ContextMenu menu, View view, Object o) {
//        MenuItem menuitem1 = menu.add(R.string.menu_rot13_label);
//        rot13menuid = menuitem1.getItemId();
//    }
    
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        
//        if (view.getId() == R.id.body) {
            //MenuItem menuitem1 = menu.add(R.string.menu_rot13_label);
            //rot13menuid = menuitem1.getItemId();
//            MenuItem menuitem2 = menu.add(R.string.menu_copy_label);
//            copymenuid = menuitem2.getItemId();
//            MenuItem menuitem3 = menu.add(R.string.menu_paste_label);
//            pastemenuid = menuitem3.getItemId();
//        }
    }

    /**
     * Called when user has selected a menu item from the 
     * menu button popup. 
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        activateUser();

        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_insertquotation_id:
        {
            Log.i(TAG, "insertquotation");
            String textToInsert = null;
            if (mReplyTo > 0) {
                try {
                    // TODO: Don't quote image, at least not as text...
                    try {
                        textToInsert = mKom.getTextbyNo(mReplyTo).getBodyString();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } catch (UnsupportedEncodingException e) {
                    Log.d(TAG, "onOptionsItemSelected:" + e);
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    Log.d(TAG, "onOptionsItemSelected:" + e);
                    e.printStackTrace();
                }
            }
            
            if ((textToInsert != null) && (mBody != null)) {
                if (textToInsert.endsWith("\n")) {
                    textToInsert.substring(0, textToInsert.length()-2);
                }
                textToInsert = "> " + textToInsert.replaceAll("\n", "\n> ");
                textToInsert = textToInsert + "\n\n";
                int start = mBody.getSelectionStart();
                int end = mBody.getSelectionEnd();
                mBody.getText().replace(Math.min(start, end),
                        Math.max(start, end), textToInsert, 0,
                        textToInsert.length());
            }
            return true;
        }
        case R.id.menu_insertlocation_id:
            Log.i(TAG, "insertlocation");
            if (mPrecision > 0) {
                String textToInsert = "<geo:" + mLat + "," + mLon + ";u="
                        + mPrecision + ">"; // ref RFC580
                int start = mBody.getSelectionStart();
                int end = mBody.getSelectionEnd();
                mBody.getText().replace(Math.min(start, end),
                        Math.max(start, end), textToInsert, 0,
                        textToInsert.length());
            } else {
                Toast.makeText(getApplicationContext(),
                        getString(R.string.no_location), Toast.LENGTH_SHORT)
                        .show();
            }
            return true;
        case R.id.menu_rot13paste_id :
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            CharSequence text = clipboard.getText();
            Log.d(TAG, "CharSequence from clipboard:"+text);
            String textstring = text.toString();
            Log.d(TAG, "String from clipboard:"+textstring);
            if (text.length() > 0) {
                String rotated = org.lindev.androkom.Rot13.cipher(textstring
                        .toString());
                int selectionStart = mBody.getSelectionStart();
                int selectionEnd = mBody.getSelectionEnd();
                Log.d(TAG, "selectionStart:"+selectionStart);
                Log.d(TAG, "selectionEnd:"+selectionEnd);
                mBody.append(rotated);
            }
            return true;
        case R.id.menu_rot13all_id :
            String currentText = mBody.getText().toString();
            String rotated = org.lindev.androkom.Rot13.cipher(currentText);
            mBody.setText(rotated);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        Log.d(TAG, "===============================================");
        //AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
        //        .getMenuInfo();
        if (item.getItemId() == rot13menuid) {
            Log.i(TAG, "rot13 menu selected");
            return true;
        } else if (item.getItemId() == copymenuid) {
            int selectionStart = mBody.getSelectionStart();
            int selectionEnd = mBody.getSelectionEnd();
            CharSequence bodyContent= mBody.getText().subSequence(selectionStart, selectionEnd);
            if (bodyContent.length()>0) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setText(bodyContent);
            }
            return true;
        } else if (item.getItemId() == pastemenuid) {
            int selectionStart = mBody.getSelectionStart();
            int selectionEnd = mBody.getSelectionEnd();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            CharSequence text = clipboard.getText();
            mBody.append(text, selectionStart, selectionEnd);
            return true;
        } else {
            return super.onContextItemSelected(item);
        }
    }

    private void showRemoveRecipientDialog(final Recipient recipient) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.creator_remove_recipient)+"\n"
                + recipient.recipientStr + "?");
        builder.setNegativeButton(getString(R.string.no), null);
        builder.setPositiveButton(getString(R.string.yes), new OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                remove(recipient);
            }
        });
        builder.create().show();
    }

    private void showAddRecipientDialog(final RecipientType type, final LookupType lookupType) {
        final EditText input = new EditText(this);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.creator_add_recipient));
        builder.setView(input);
        builder.setNegativeButton(getString(R.string.alert_dialog_cancel), null);
        builder.setPositiveButton(getString(R.string.alert_dialog_ok), new OnClickListener() {
            public void onClick(final DialogInterface dialog, final int which) {
                final String recip = input.getText().toString();
                new LookupNameTask(TextCreator.this, mKom, recip, lookupType, new RunOnSuccess() {
                    public void run(final ConfInfo conf) {
                        add(new Recipient(mKom.getApplicationContext(), conf.getNo(), conf.getNameString(), type));
                    }
                }).execute();
            }
        });
        builder.create().show();
    }

    private void sendMessage() {
        final String subject = mSubject.getText().toString();
        final String body = mBody.getText().toString();
        if (mRecipients.isEmpty()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.creator_no_recipients));
            builder.setPositiveButton(getString(R.string.alert_dialog_ok), null);
            builder.create().show();
            return;
        }

        new CreateTextTask(this, mKom, subject, body, mLat, mLon, mPrecision, mReplyTo, mRecipients, new CreateTextRunnable() {
            public void run(final Text text) {
                new SendTextTask(TextCreator.this, mKom, text, new Runnable() {
                    public void run() {
                        finish();
                    }
                }).execute();
            }
        }).execute();
    }

    private void remove(final Recipient recipient) {
        mRecipients.remove(recipient);
        mAdapter.remove(recipient);
        mAdapter.notifyDataSetChanged();
    }

    private void add(final Recipient recipient) {
        for (final Recipient recpt : mRecipients) {
            if (recpt.recipientId == recipient.recipientId) {
                Log.d(TAG, "Remove old recipient");
                remove(recpt);
            }
        }
        mRecipients.add(recipient);
        mAdapter.add(recipient);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mRecipients;
    }

    private void addInitialRecipients() {
        final String subject = getIntent().getStringExtra(INTENT_SUBJECT);
        final int recipient = getIntent().getIntExtra(INTENT_RECIPIENT, -1);
        final boolean isMail = getIntent().getBooleanExtra(INTENT_IS_MAIL, false);

        if (mReplyTo > 0) {
            new CopyRecipientsTask().execute(mReplyTo);
        }
        else if (isMail) {
            try {
                add(new Recipient(mKom.getApplicationContext(), mKom.getUserId(), mKom.getConferenceName(mKom.getUserId()), RecipientType.RECP_TO));
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                Log.d(TAG, "addInitialRecipients InterruptedException 1");
            }
            showAddRecipientDialog(RecipientType.RECP_TO, LookupType.LOOKUP_USERS);
        }
        else {
            showAddRecipientDialog(RecipientType.RECP_TO, LookupType.LOOKUP_BOTH);
        }

        if (subject != null) {
            mSubject.setText(subject);
        }
        if (recipient > 0) {
            try {
                add(new Recipient(mKom.getApplicationContext(), recipient, mKom.getConferenceName(recipient), RecipientType.RECP_TO));
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                Log.d(TAG, "addInitialRecipients InterruptedException 2");
            }
        }
    }

    public void activateUser() {
        new ActivateUserTask().execute();
    }

    /**
     * No need to wait for activate
     * 
     */
    private class ActivateUserTask extends AsyncTask<KomToken, Void, Void> {
        protected void onPreExecute() {
            Log.d(TAG, "ActivateUserTask.onPreExecute");
        }

        // worker thread (separate from UI thread)
        protected Void doInBackground(final KomToken... args) {
            try {
                mKom.activateUser();
            } catch (Exception e1) {
                Log.i(TAG, "Failed to activate user, exception:"+e1);
                //e1.printStackTrace();
                try {
                    mKom.logout();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    public void onServiceConnected(final ComponentName name, final IBinder service) {
        Log.d(TAG, "onServiceConnected start");
        mKom = ((LocalBinder<KomServer>) service).getService();

        if (!getIntent().getBooleanExtra(INTENT_INITIAL_RECIPIENTS_ADDED, false)) {
            addInitialRecipients();
            getIntent().putExtra(INTENT_INITIAL_RECIPIENTS_ADDED, true);
        }
        enableButtons();

        Log.d(TAG, "onServiceConnected done");
    }

    public void onServiceDisconnected(final ComponentName name) {
        Log.d(TAG, "onServiceDisconnected");
        mKom = null;
    }

    private App getApp() {
        return (App) getApplication();
    }

    /* Class My Location Listener */
    public class MyLocationListener implements LocationListener {

        public void onLocationChanged(Location loc) {
            mLat = loc.getLatitude();
            mLon = loc.getLongitude();
            mPrecision = loc.getAccuracy();
            String Text = getString(R.string.my_location) + "Latitud = "
                    + loc.getLatitude() + "Longitud = " + loc.getLongitude();
            Log.i(TAG, Text);
        }

        public void onProviderDisabled(String provider) {
            Log.i(TAG, "Gps Disabled");
        }

        public void onProviderEnabled(String provider) {
            Log.i(TAG, "Gps Enabled");
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.i(TAG, "Gps status changed");
        }

    }/* End of Class MyLocationListener */
    
    int rot13menuid=0;
    int copymenuid=0;
    int pastemenuid=0;
}
