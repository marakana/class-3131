// Thunderstorm
package com.marakana.android.yamba.svc;

import java.util.ArrayList;
import java.util.List;

import com.marakana.android.yamba.R;
import com.marakana.android.yamba.YambaContract;
import com.marakana.android.yamba.clientlib.YambaClient;
import com.marakana.android.yamba.clientlib.YambaClientException;
import com.marakana.android.yamba.clientlib.YambaClient.Status;
import com.marakana.android.yamba.data.YambaDbHelper;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;


public class YambaService extends IntentService {
	private static final String TAG = "SVC";

	static final int OP_POST_COMPLETE = -1;
    static final int OP_POST = -2;
    static final int OP_POLL = -3;

    private static final String PARAM_OP = "YambaService.OP";    
    private static final String PARAM_STATUS = "YambaService.STATUS";

    private static class Hdlr extends Handler {
        private final YambaService svc;
        
        public Hdlr(YambaService svc) { this.svc = svc; }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case OP_POST_COMPLETE:
                svc.postComplete(msg.arg1);
                break;
            }
        }
    }
    
    public static void stopPolling(Context ctxt) {
    	Log.d(TAG, "stop polling");
        ((AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE))
            .cancel(createPollingIntent(ctxt));
    }
     
	public static void startPolling(Context ctxt) {
		Log.d(TAG, "start polling");
        ((AlarmManager) ctxt.getSystemService(Context.ALARM_SERVICE))
          .setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + 100, // start time
            pollInterval,
            createPollingIntent(ctxt));
    }
    
    public static void post(Context ctxt, String status) {
    	Log.d(TAG, "post: " + status);
        Intent i = new Intent(ctxt, YambaService.class);
        i.putExtra(PARAM_OP, OP_POST);
        i.putExtra(PARAM_STATUS, status);
        ctxt.startService(i);
    }
          
    private static PendingIntent createPollingIntent(Context ctxt) {
        Intent i = new Intent(ctxt, YambaService.class);
        i.putExtra(PARAM_OP, OP_POLL);
        return PendingIntent.getService(ctxt, OP_POLL, i, PendingIntent.FLAG_UPDATE_CURRENT);
	}

    private static final long pollInterval = 10 * 1000;

    private static synchronized long getPollInterval(Context ctxt) {
    	// blake's version gets this var from config file.
        return pollInterval;
    }

    private volatile YambaClient yamba;
    private static int pollSize;
    private Hdlr hdlr;
    
    public YambaService() { super(TAG);}

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "service created");
        
        hdlr = new Hdlr(this);
        
        pollSize = 10; // blake's version gets this from config file.

        yamba = new YambaClient("student", "password", "http://yamba.marakana.com/api");
    }

    @Override
    protected void onHandleIntent(Intent i) {
        switch (i.getIntExtra(PARAM_OP, 0)) {
          case OP_POST:
            doPost(i.getStringExtra(PARAM_STATUS));
            break;
          case OP_POLL:
            doPoll(i);
            break;
        }
    }
        
    protected void doPost(String statusMsg) {
    	Log.d(TAG, "posting: " + statusMsg);

        int msg = R.string.post_failed;
        try {
          yamba.postStatus(statusMsg);
          msg = R.string.post_succeeded;
        }
        catch (YambaClientException e) {
          Log.e(TAG, "Post failed: " + e, e);
        }

        Message.obtain(hdlr, OP_POST_COMPLETE, msg, 0).sendToTarget();        
    }
    
    protected void postComplete(int msg) {   
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
      }
    
    protected void doPoll(Intent i) {
      Log.i(TAG, "handlePoll - start");    
      List<Status> timeline;
      try { timeline = yamba.getTimeline(pollSize); }
  	  catch (YambaClientException e) {
		Log.e(TAG, "Polling failed!");    	      
		e.printStackTrace();
		return;
	  }

      long latest = getLatestStatusTime();
      
      List<ContentValues> rows = new ArrayList<ContentValues>();
      
      for (Status status: timeline) {
    	  long t = status.getCreatedAt().getTime();
    	  if (t < latest) { continue; }    	  

      	  ContentValues cv = new ContentValues();
          cv.put(YambaContract.Timeline.Columns.ID, Long.valueOf(status.getId()));
          cv.put(YambaContract.Timeline.Columns.CREATED_AT, Long.valueOf(t));
          cv.put(YambaContract.Timeline.Columns.USER, status.getUser());
          cv.put(YambaContract.Timeline.Columns.STATUS, status.getMessage());
    	  rows.add(cv);    	  
      }

      int n = rows.size();
      if (0 >= n) { return; }

      ContentValues[] vals = new ContentValues[n];
      getContentResolver()
          .bulkInsert(YambaContract.Timeline.URI, rows.toArray(vals));
    }
    
    private long getLatestStatusTime() {
        Cursor c = null;
        try {
            c = getContentResolver().query(
                    YambaContract.Timeline.URI,
                    new String[] { YambaContract.Timeline.Columns.MAX_TIMESTAMP },
                    null,
                    null,
                    null);

            return (!c.moveToNext())
                    ? Long.MIN_VALUE
                    : c.getLong(
                        c.getColumnIndex(YambaContract.Timeline.Columns.MAX_TIMESTAMP));
        }
        finally {
            if (null != c) { c.close(); }
        }
    }
}
