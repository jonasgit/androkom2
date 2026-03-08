package org.lindev.androkom;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Add new comment dialog. 
 * 
 * @author jonas
 *
 */
public class AddNewCommentToText extends Activity implements ServiceConnection {
    public static final String INTENT_TEXTNO = "textno";

    public static final String TAG = "Androkom AddNewCommentToText";

    private KomServer mKom;
    private EditText mCommentNo;

    private class AddNewCommentToTextTask extends AsyncTask<Integer, Void, String> {
        private ProgressDialog mDialog;

        @Override
        protected void onPreExecute() {
            mDialog = new ProgressDialog(AddNewCommentToText.this);
            mDialog.setCancelable(false);
            mDialog.setIndeterminate(true);
            mDialog.setMessage(getString(R.string.addnewcommenttotext_title));
            mDialog.show();
        }

        @Override
        protected String doInBackground(final Integer... args) {
            String result = "broken error";
            try {
                result =  mKom.addNewCommentToText(mTextNo, args[0]);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(final String arg) {
            mDialog.dismiss();
            if (arg != "") {
                Toast.makeText(getApplicationContext(),
                        arg, Toast.LENGTH_SHORT)
                        .show();                                
            }
            AddNewCommentToText.this.finish();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addnewcommenttotext);

        final View root = findViewById(R.id.addnewcommenttotext_root);

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

        mTextNo = (Integer) getIntent().getExtras().get(INTENT_TEXTNO);

        mCommentNo = (EditText) findViewById(R.id.commentno);
        
        Button doButton = (Button) findViewById(R.id.do_addnewcommenttotext);
        doButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                doAddNewCommentToText();
            }
        });
        getApp().doBindService(this);
    }

    @Override
    public void onDestroy() {
        getApp().doUnbindService(this);
        super.onDestroy();
    }

    void doAddNewCommentToText() {
        final String commentno = mCommentNo.getText().toString();

        new AddNewCommentToTextTask().execute(Integer.parseInt(commentno));
    }

    App getApp() {
        return (App) getApplication();
    }

    public void onServiceConnected(ComponentName name, IBinder service) {
        mKom = ((LocalBinder<KomServer>) service).getService();
    }

    public void onServiceDisconnected(ComponentName name) {
        mKom = null;
    }
    
    int mTextNo = 0;
}
