package pinak.mcn;


import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsMessage;
import android.content.res.Resources;
import android.util.Log;

import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by pinak on 27/6/14.
 */
public class IncomingSms extends BroadcastReceiver {
    //final SmsManager sms = SmsManager.getDefault();
    public void onReceive(Context context, Intent intent) {
        SmsMessage[] smsMessages = getMessagesFromIntent(intent);
        for (SmsMessage currentMessage : smsMessages) {
            String senderNumber = currentMessage.getDisplayOriginatingAddress();
            String message = currentMessage.getDisplayMessageBody();
            showNotification(senderNumber, message, context);
        }
    }

    private SmsMessage[] getMessagesFromIntent(Intent intent) {
        final Bundle bundle = intent.getExtras();
        try {
            if (bundle != null) {
                final Object[] pdusObj = (Object[]) bundle.get("pdus");
                SmsMessage[] smsMessages = new SmsMessage[pdusObj.length];
                for (int i = 0; i < pdusObj.length; i++) {
                    smsMessages[i] = SmsMessage.createFromPdu((byte[]) pdusObj[i]);
                }
                return smsMessages;
            }
        } catch (Exception e) {
          Log.e("SmsReceiver", "Exception smsReceiver" + e);
          }
        return null;
    }


    private void showNotification(String senderNumber, String message, Context context) {
        String caller = null;
        String callerName = null;
        long contactId = -1;
        Bitmap callerPhoto;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String intendedNumber = prefs.getString("pref_sender", "");
        if (!intendedNumber.equals(senderNumber)) {
            return;
        }
        Pattern pattern = Pattern.compile(prefs.getString("pref_regex", ""));
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            caller = matcher.group(1);
        }
        if (caller == null) {
            return;
        }

        //Create a uri to query contacts provider by appending caller to CONTENT_FILTER_URI
        Uri lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(caller));

        ContentResolver contentResolver = context.getContentResolver();

        //Perform the query to get a cursor with a table having DISPLAY_NAME and _ID
        Cursor contactLookup = contentResolver.query(lookupUri, new String[]{
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup._ID},
                null, null, null);

        if (contactLookup != null && contactLookup.getCount() > 0) {
            contactLookup.moveToNext();
            callerName = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract
                    .Data.DISPLAY_NAME));
            contactId = contactLookup.getLong(contactLookup.getColumnIndex(ContactsContract
            .Data._ID));
            contactLookup.close();
        }

        // Setup notification
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setContentTitle(context.getString(R.string.notification_title))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentText(caller)
                .setAutoCancel(true);

        // If caller in Contacts
        if (contactId != -1) {
            //set notification text to contact name
            mBuilder.setContentText(callerName);
            //fetch photo
            Uri contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
                    contactId);
            InputStream photoStream = ContactsContract.Contacts
                    .openContactPhotoInputStream(contentResolver, contactUri, true);
            callerPhoto = BitmapFactory.decodeStream(photoStream);

            // Resize photo according to the device
            final Resources res = context.getResources();
            if (callerPhoto != null) {
                final int idealWidth =
                        res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
                final int idealHeight =
                        res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
                callerPhoto = Bitmap.createScaledBitmap(callerPhoto, idealWidth, idealHeight, false);
                mBuilder.setLargeIcon(callerPhoto);

                // Set action of notification to open contact card
                Intent openContactCard = new Intent(Intent.ACTION_VIEW);
                openContactCard
                        .setData(contactUri)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent contentIntent = PendingIntent.getActivity(context,
                        0,
                        openContactCard,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
                mBuilder.setContentIntent(contentIntent);
            }
        }
        //Set up intent to call the caller
        Intent callCaller = new Intent(Intent.ACTION_CALL);
        callCaller.setData(Uri.parse("tel:" + caller));
        PendingIntent pCallCaller = PendingIntent.getActivity(context, 0, callCaller, 0);

        //set up intent to send message to caller
        Intent textCaller = new Intent(Intent.ACTION_SENDTO);
        textCaller.setData(Uri.parse("smsto:" + caller));
        PendingIntent pTextCaller = PendingIntent.getActivity(context, 0, textCaller, 0);

        //Add Actions for call and sms
        mBuilder.addAction(R.drawable.ic_action_call,
                context.getString(R.string.call_action),
                pCallCaller);

        mBuilder.addAction(R.drawable.ic_action_email,
                context.getString(R.string.sms_action),
                pTextCaller);

        //Show notification
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, mBuilder.build());
    }
}

