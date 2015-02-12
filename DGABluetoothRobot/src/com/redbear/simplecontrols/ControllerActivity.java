package com.redbear.simplecontrols;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;

public class ControllerActivity extends Activity {
	private final static String TAG = "DGABluetoothRobot.Controller";

	private Button leftButton;
	private Button upButton;
	private Button rightButton;

	private BluetoothGattCharacteristic characteristicTx = null;
	private RBLService mBluetoothLeService;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice mDevice = null;
	private String mDeviceAddress;

	private boolean flag = true;
	private boolean connState = false;
	private boolean scanFlag = false;

	private byte[] data = new byte[3];
	private static final int REQUEST_ENABLE_BT = 1;
	private static final long SCAN_PERIOD = 2000;

	final private static char[] hexArray = { '0', '1', '2', '3', '4', '5', '6',
		'7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((RBLService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
				Toast.makeText(getApplicationContext(), "Disconnected",
						Toast.LENGTH_SHORT).show();
				disableButtons();
			} else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				Toast.makeText(getApplicationContext(), "Connected",
						Toast.LENGTH_SHORT).show();

				getGattService(mBluetoothLeService.getSupportedGattService());
			} else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
				data = intent.getByteArrayExtra(RBLService.EXTRA_DATA);
				Log.d("btData", String.valueOf(data));
			} else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
				//displayData(intent.getStringExtra(RBLService.EXTRA_DATA));
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_controller);
		leftButton = ((Button)findViewById(R.id.left_button));
//		upButton = ((Button)findViewById(R.id.up_button));
		rightButton = ((Button)findViewById(R.id.right_button));

		//A normal OnClickListener will not do because for this app,
		//the user will touch down and hold the button while they want the robot to move
		//So use a touch listener which will listen for touchDown and touchUp
		leftButton.setOnTouchListener(buttonTouchListener);
//		upButton.setOnTouchListener(buttonTouchListener);
		rightButton.setOnTouchListener(buttonTouchListener);

		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
			.show();
			finish();
		}

		final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
			.show();
			finish();
			return;
		}
//		((SeekBar)findViewById(R.id.seekBar)).setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
//			
//			@Override
//			public void onStopTrackingTouch(SeekBar seekBar) {
//				int progress = seekBar.getProgress();
//				characteristicTx.setValue(new byte[] {0x01,(byte)progress});
//				mBluetoothLeService.writeCharacteristic(characteristicTx);
//				Toast.makeText(getBaseContext(), "speed " + progress, Toast.LENGTH_SHORT).show();
//			}
//			
//			@Override
//			public void onStartTrackingTouch(SeekBar seekBar) {
//				// TODO Auto-generated method stub
//				
//			}
//			
//			@Override
//			public void onProgressChanged(SeekBar seekBar, int progress,
//					boolean fromUser) {
//				
//			}
//		});
		//disableButtons();

		Intent gattServiceIntent = new Intent(ControllerActivity.this,
				RBLService.class);

		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.controller, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		else if (id == R.id.connect) {
			scanLeDevice();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStop() {
		super.onStop();

		flag = false;

		unregisterReceiver(mGattUpdateReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (mServiceConnection != null)
			unbindService(mServiceConnection);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// User chose not to enable Bluetooth.
		if (requestCode == REQUEST_ENABLE_BT
				&& resultCode == Activity.RESULT_CANCELED) {
			finish();
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void getGattService(BluetoothGattService gattService) {
		if (gattService == null)
			return;

		enableButtons();
		//Kept track of signal strength,
		//not really needed for this
		//startReadRssi();

		characteristicTx = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);

		BluetoothGattCharacteristic characteristicRx = gattService
				.getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
		mBluetoothLeService.setCharacteristicNotification(characteristicRx,
				true);
		mBluetoothLeService.readCharacteristic(characteristicRx);
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(RBLService.ACTION_GATT_RSSI);

		return intentFilter;
	}

	private void scanLeDevice() {
		new Thread() {

			@Override
			public void run() {
				mBluetoothAdapter.startLeScan(mLeScanCallback);

				try {
					Thread.sleep(SCAN_PERIOD);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				mBluetoothAdapter.stopLeScan(mLeScanCallback);

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						String[] arduinoNames = new String[availableArduinos.size()];
						for(int i = 0; i < availableArduinos.size(); i++) 
							arduinoNames[i] = availableArduinos.get(i).getName();
						if(availableArduinos.size() > 0) {
							AlertDialog.Builder builder = new AlertDialog.Builder(ControllerActivity.this);
							builder.setTitle("Choose a Device")
							.setItems(arduinoNames, new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									mDevice = availableArduinos.get(which);
									connect();
								}
							});
							builder.create().show();
						}
						else {
							Toast.makeText(ControllerActivity.this, "No devices found", Toast.LENGTH_SHORT).show();
						}
					}
				});
			}
		}.start();
	}
	private ArrayList<BluetoothDevice> availableArduinos = new ArrayList<BluetoothDevice>();

	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi,
				final byte[] scanRecord) {

			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					byte[] serviceUuidBytes = new byte[16];
					String serviceUuid = "";
					for (int i = 32, j = 0; i >= 17; i--, j++) {
						serviceUuidBytes[j] = scanRecord[i];
					}
					serviceUuid = bytesToHex(serviceUuidBytes);
					if (stringToUuidString(serviceUuid).equals(
							RBLGattAttributes.BLE_SHIELD_SERVICE
							.toUpperCase(Locale.ENGLISH))) {
						availableArduinos.add(device);
					}
				}
			});
		}
	};

	private void connect() {
		if (scanFlag == false) {
			if (mDevice != null) {
				Toast.makeText(ControllerActivity.this, "Connected to: " + mDevice.getName(), Toast.LENGTH_SHORT).show();
				mDeviceAddress = mDevice.getAddress();
				mBluetoothLeService.connect(mDeviceAddress);
				scanFlag = true;
			} else {
				Toast toast = Toast.makeText(ControllerActivity.this,"Couldn't connect to Arduino. Try again.",Toast.LENGTH_SHORT);
				toast.show();
			}
		}
	}

	//	private void connect() {
	//		if (scanFlag == false) {
	//			scanLeDevice();
	//
	//			Timer mTimer = new Timer();
	//			mTimer.schedule(new TimerTask() {
	//
	//				@Override
	//				public void run() {
	//					
	//					
	//					if (mDevice != null) {
	//						runOnUiThread(new Runnable() {
	//							@Override
	//							public void run() {Toast.makeText(ControllerActivity.this, "Connected to: " + mDevice.getName(), Toast.LENGTH_SHORT).show();}
	//						});
	//						mDeviceAddress = mDevice.getAddress();
	//						mBluetoothLeService.connect(mDeviceAddress);
	//						scanFlag = true;
	//					} else {
	//						runOnUiThread(new Runnable() {
	//							public void run() {
	//								Toast toast = Toast
	//										.makeText(
	//												ControllerActivity.this,
	//												"Couldn't connect to Arduino. Try again.",
	//												Toast.LENGTH_SHORT);
	//								toast.setGravity(0, 0, Gravity.CENTER);
	//								toast.show();
	//							}
	//						});
	//					}
	//				}
	//			}, SCAN_PERIOD);
	//		}
	//
	//		System.out.println(connState);
	//		if (connState == false) {
	//			mBluetoothLeService.connect(mDeviceAddress);
	//		} else {
	//			mBluetoothLeService.disconnect();
	//			mBluetoothLeService.close();
	//			disableButtons();
	//		}
	//	}

	private String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	private String stringToUuidString(String uuid) {
		StringBuffer newString = new StringBuffer();
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(0, 8));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(8, 12));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(12, 16));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(16, 20));
		newString.append("-");
		newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(20, 32));

		return newString.toString();
	}

	private void disableButtons() {
		flag = false;
		connState = false;

		leftButton.setEnabled(false);
		rightButton.setEnabled(false);
//		upButton.setEnabled(false);
	}

	private void enableButtons() {
		flag = true;
		connState = true;

		leftButton.setEnabled(true);
		rightButton.setEnabled(true);
//		upButton.setEnabled(true);
	}

	OnTouchListener buttonTouchListener = new OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if(scanFlag) {
				if(v.equals(leftButton)) {
					if(event.getAction() == MotionEvent.ACTION_DOWN) {
						//User just pressed left button
						//you won't know they lifted up until ACTION_UP is called
						characteristicTx.setValue(new byte[] {0x01});
						mBluetoothLeService.writeCharacteristic(characteristicTx);
					}
					else if(event.getAction() == MotionEvent.ACTION_UP) {
						//User just lifted up from button
						//you probably want to do something like send a "clear" message
					}
				}
				else if(v.equals(upButton)) {
					if(event.getAction() == MotionEvent.ACTION_DOWN) {
						//User just pressed up button
						characteristicTx.setValue(new byte[] {0x02});
						mBluetoothLeService.writeCharacteristic(characteristicTx);
					}
					else if(event.getAction() == MotionEvent.ACTION_UP) {
						//User just lifted up from button
						//you probably want to do something like send a "clear" message

					}
				}
				else if(v.equals(rightButton)) {
					if(event.getAction() == MotionEvent.ACTION_DOWN) {
						//User just pressed right button
						characteristicTx.setValue(new byte[] {0x03});
						mBluetoothLeService.writeCharacteristic(characteristicTx);
					}
					else if(event.getAction() == MotionEvent.ACTION_UP) {
						//User just lifted up from button
						//you probably want to do something like send a "clear" message

					}
				}
				
			}
			return false;
		}
	};


}
