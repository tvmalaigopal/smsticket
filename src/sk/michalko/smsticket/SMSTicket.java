package sk.michalko.smsticket;

import sk.michalko.smsticket.handlers.SMSReceiver;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

public class SMSTicket extends ListActivity {


	static final String TAG = SMSTicket.class.getSimpleName();
	
	static boolean isWaitingResponse = false;
	static String[] PROJECTION = new String[] { "state", "validThrough" };
	
	Cursor cursorView = null;
	Cursor cursorExpire = null;
	SimpleCursorAdapter adapter = null;
	SQLiteDatabase db;
	
	String ticketDetails = "Details";
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		TicketOpenSqlHelper sqlHelper = TicketOpenSqlHelper.getInstance(this);
		db = sqlHelper.getWritableDatabase();

		cursorView = db.query("tickets", new String[] { "_id", "changed", "state",	"validThrough" }, null, null, null, null, "created ASC", "6");

		adapter = new SimpleCursorAdapter(this,	R.layout.item, cursorView, PROJECTION, new int[] { R.id.item_image,	R.id.item_text });
		adapter.setViewBinder(new IconViewBinder());
		setListAdapter(adapter);
		
		sanitizeDb();
		
		// Register refresh gui event receiver
		registerReceiver(refresh, new IntentFilter(getResources().getString(R.string.intent_update)));

		Button btnBuyTicket = (Button) findViewById(R.id.ButtonBuyTicket);
		btnBuyTicket.setOnClickListener(buttonListener);
		
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(refresh);
		setListAdapter(null);
		cursorView.close();
		super.onDestroy();
	}


	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// get ticket details according to id
		ticketDetails = "";
		TicketDao ticket = TicketDao.getById(String.valueOf(id), this.getBaseContext());
		ticketDetails = ticket.getSmsBody();
		showDialog(1);
		super.onListItemClick(l, v, position, id);
	}


	@Override
	protected Dialog onCreateDialog(int id) {
        return new AlertDialog.Builder(SMSTicket.this)
            .setTitle(R.string.dialog_ticket_details_title)
            .setMessage(R.string.dialog_ticket_details_message)
            .setPositiveButton(R.string.dialog_ticket_details_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            })
            .create();
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		AlertDialog alertDialog = (AlertDialog)dialog;
		alertDialog.setMessage(ticketDetails);
		int v=4;
		return;
	}

	public BroadcastReceiver refresh = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			sanitizeDb();
			cursorView.requery();
			adapter.notifyDataSetChanged();
			Log.d(TAG, "Notification: Tickets changed.");
		}
	};

	public View.OnClickListener buttonListener = new View.OnClickListener() {

		public void onClick(View v) {
			// See if we have received all ordered tickets
			// TicketDao ticket = TicketDao.getCurrent(v.getContext());

			// if (ticket == null || ticket.getState() == TicketState.TICKET_EXPIRED.toString()){
			sendSMS();
			Toast.makeText(getBaseContext(), "Sending ticket request.",	Toast.LENGTH_SHORT).show();
			// }
		}
	};

	public void sendSMS() {

		Context context = this.getBaseContext();

		TicketDao ticket = TicketDao.create(context);

		ticket.setState(TicketState.TICKET_ORDER_CREATED.toString());
		ticket.save(context);

		Uri uriTicketId = Uri.parse(ticket.getUuid());

		Intent intentSent = new Intent(this, SMSReceiver.class);
		intentSent.setAction(getResources().getString(R.string.intent_sms_sent));
		intentSent.setData(uriTicketId);
		PendingIntent intentSMSSent = PendingIntent.getBroadcast(context, 0, intentSent, 0);

		Intent intentDelivered = new Intent(this, SMSReceiver.class);
		intentDelivered.setAction(getResources().getString(R.string.intent_sms_delivered));
		intentDelivered.setData(uriTicketId);
		PendingIntent intentSMSDelivered = PendingIntent.getBroadcast(context, 0, intentDelivered, 0);

		SmsManager smsManager = SmsManager.getDefault();
		//smsManager.sendTextMessage("5554", null, "DPB .a.s. \nPrestupny CL 0,80EUR (24.10Sk) 1EUR=30.1260Sk \nPlatnost od 01-02-2011 12:40 do 01:50 hod. \ngwoea4qg3wt", intentSMSSent, intentSMSDelivered);
		smsManager.sendTextMessage("1100", null, " ", intentSMSSent, intentSMSDelivered);
		//smsManager.sendTextMessage("00421905547580", null, "DPB .a.s. Prestupny CL 0,80EUR (24.10Sk) 1EUR=30.1260Sk Platnost od 01-02-2011 12:40 do 01:50 hod. gwoea4qg3wt", intentSMSSent, intentSMSDelivered);

		Log.d(TAG, "SMS Ticket message sent. " + ticket.getUuid());

	}
	
	public void sanitizeDb() {
		// sanitize db
		// Check and remove unfinished (state < TICKET_VALID and created < now()- 10 min)
		// optionaly check received sms messages in case notification failed
		//db.execSQL("DELETE from tickets WHERE (state != 'TICKET_VALID' AND state != 'TICKET_EXPIRED') AND (created < datetime('now','localtime', '-10 minutes'))");
		db.execSQL("DELETE from tickets WHERE (validThrough is null) AND (created < datetime('now','localtime', '-10 minutes'))");
		// Check and update expired (state < TICKET_EXPIRED and validThrough < now())
		db.execSQL("UPDATE tickets SET state = 'TICKET_EXPIRED' WHERE (state = 'TICKET_VALID' AND validThrough < datetime('now'))");

	}
}