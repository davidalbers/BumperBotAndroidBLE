package com.redbear.simplecontrols;

import java.util.ArrayList;
import java.util.Locale;

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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

public class BumperBotController extends Activity implements SensorEventListener{
	private final static String TAG = BumperBotController.class.getSimpleName();


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
	
	private SensorManager sensorManager;
	private Sensor sensor;
	
	private Button forwardButton;
	private Button leftButton;
	private Button rightButton;
	private final double STEERING_SENSITIVITY = 1.75;
	private boolean useSteeringWheel = false;

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
			} else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				Toast.makeText(getApplicationContext(), "Connected",
						Toast.LENGTH_SHORT).show();

				getGattService(mBluetoothLeService.getSupportedGattService());
			} else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
				data = intent.getByteArrayExtra(RBLService.EXTRA_DATA);

				readAnalogInValue(data);
			} else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
				displayData(intent.getStringExtra(RBLService.EXTRA_DATA));
			}
		}
	};
	int speed = 0;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	    
		setContentView(R.layout.activity_controller_with_reverse);
		
		if (!getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_BLUETOOTH_LE)) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
					.show();
			finish();
		}

/*		leftButton = (Button)findViewById(R.id.left_button);
		leftButton.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					if(forwardButton.isPressed())
						slightLeft();
					else 
						left();
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					if(!forwardButton.isPressed())
						stop();
					else
						forward();
				}
				return false;
			}
		});
		
		
		rightButton = (Button)findViewById(R.id.right_button);
		rightButton.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					if(forwardButton.isPressed())
						slightRight();
					else
						right();
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					if(!forwardButton.isPressed())
						stop();
					else 
						forward();
				}
				return false;
			}
		});*/
		
		forwardButton = (Button)findViewById(R.id.up_button);
		forwardButton.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN) {
					if(!leftButton.isPressed() && !rightButton.isPressed())
						forward();
				}
				else if(event.getAction() == MotionEvent.ACTION_UP) {
					if(!leftButton.isPressed() && !rightButton.isPressed())
						stop();
				}
				return false;
			}
		});
		
//		((Button)findViewById(R.id.down_button)).setOnTouchListener(new OnTouchListener() {
//			
//			@Override
//			public boolean onTouch(View v, MotionEvent event) {
//				if(event.getAction() == MotionEvent.ACTION_DOWN) {
//					backward();
//				}
//				else if(event.getAction() == MotionEvent.ACTION_UP) {
//					stop();
//				}
//				return false;
//			}
//		});
//		
//		((Button)findViewById(R.id.beep_button)).setOnTouchListener(new OnTouchListener() {
//			
//			@Override
//			public boolean onTouch(View v, MotionEvent event) {
//				if(event.getAction() == MotionEvent.ACTION_DOWN) {
//					beep();
//				}
//				else if(event.getAction() == MotionEvent.ACTION_UP) {
//					noBeep();
//				}
//				return false;
//			}
//		});
		
		final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);        
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
					.show();
			finish();
			return;
		}
		
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		sensorManager.registerListener(this, sensor, 100000);
		
		Intent gattServiceIntent = new Intent(BumperBotController.this,
				RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		
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
		else if (id == R.id.use_buttons){
			LinearLayout topRow = (LinearLayout)findViewById(R.id.top_row);
			LayoutParams topParams = (LayoutParams) topRow.getLayoutParams();
			topParams.weight = .5f;
			topRow.setLayoutParams(topParams);
			LinearLayout bottomRow = (LinearLayout)findViewById(R.id.bottom_row);
			LayoutParams bottomParams = (LayoutParams) bottomRow.getLayoutParams();
			bottomParams.weight = .5f;
			bottomRow.setLayoutParams(bottomParams);
			bottomRow.setVisibility(View.VISIBLE);
			useSteeringWheel = false;
			return true;
		}
		else if (id == R.id.steering_wheel_mode) {
			LinearLayout topRow = (LinearLayout)findViewById(R.id.top_row);
			LayoutParams topParams = (LayoutParams) topRow.getLayoutParams();
			topParams.weight = 1;
			topRow.setLayoutParams(topParams);
			LinearLayout bottomRow = (LinearLayout)findViewById(R.id.bottom_row);
			LayoutParams bottomParams = (LayoutParams) bottomRow.getLayoutParams();
			bottomParams.weight = 0;
			bottomRow.setLayoutParams(bottomParams);
			bottomRow.setVisibility(View.INVISIBLE);
			useSteeringWheel = true;
			return true;
		}
		return super.onOptionsItemSelected(item);
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

	private void displayData(String data) {
//		if (data != null) {
//			rssiValue.setText(data);
//		}
	}

	private void readAnalogInValue(byte[] data) {
//		for (int i = 0; i < data.length; i += 3) {
//			if (data[i] == 0x0A) {
//				if (data[i + 1] == 0x01)
//					digitalInBtn.setChecked(false);
//				else
//					digitalInBtn.setChecked(true);
//			} else if (data[i] == 0x0B) {
//				int Value;
//
//				Value = ((data[i + 1] << 8) & 0x0000ff00)
//						| (data[i + 2] & 0x000000ff);
//
//				AnalogInValue.setText(Value + "");
//			}
//		}
	}

	private void startReadRssi() {
		new Thread() {
			public void run() {

				while (flag) {
					mBluetoothLeService.readRssi();
					try {
						sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};
		}.start();
	}

	private void getGattService(BluetoothGattService gattService) {
		if (gattService == null)
			return;

		startReadRssi();

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
							AlertDialog.Builder builder = new AlertDialog.Builder(BumperBotController.this);
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
							Toast.makeText(BumperBotController.this, "No devices found", Toast.LENGTH_SHORT).show();
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
						for(BluetoothDevice otherDev : availableArduinos) {
							if(device.getAddress().equals(otherDev.getAddress()))
								return;
						}
						availableArduinos.add(device);
					}
				}
			});
		}
	};

	private void connect() {
		if (scanFlag == false) {
			if (mDevice != null) {
				Toast.makeText(BumperBotController.this, "Connected to: " + mDevice.getName(), Toast.LENGTH_SHORT).show();
				mDeviceAddress = mDevice.getAddress();
				mBluetoothLeService.connect(mDeviceAddress);
				scanFlag = true;
			} else {
				Toast toast = Toast.makeText(BumperBotController.this,"Couldn't connect to Arduino. Try again.",Toast.LENGTH_SHORT);
				toast.show();
			}
		}
	}
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
	
	private void forward() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 165, (byte) 20 };
			Log.d("Wheel", "left 165");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void backward() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 20, (byte) 165 };
			Log.d("Wheel", "left 165");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void left() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 75, (byte) 75 };
			Log.d("Wheel", "left 20");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void right() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 110, (byte) 110 };
			Log.d("Wheel", "left 165");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void slightRight() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 165, (byte) 75 };
			Log.d("Wheel", "left 165");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void slightLeft() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 110, (byte) 20 };
			Log.d("Wheel", "left 165");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void stop() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x03, (byte) 95, (byte) 95 };
			Log.d("Wheel", "left 0");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void beep() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x05, (byte) 02, (byte) 0 };
			Log.d("Beep", "on");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}
	
	private void noBeep() {
		if(characteristicTx != null) {
			byte[] buf = new byte[] { (byte) 0x05, (byte) 01, (byte) 0 };
			Log.d("Beep", "off");
			characteristicTx.setValue(buf);
			mBluetoothLeService.writeCharacteristic(characteristicTx);
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		//right 165
		//left 20
		//forward l 165, r 20
		//horizontal 0
		//tilt left -
		//tilt right +
		if(useSteeringWheel) {
			if(((Button)findViewById(R.id.up_button)).isPressed() && characteristicTx != null) {
				if(event.values[1] > 1) {
					//turning right
					double multiplier = Math.min(Math.abs(event.values[1]), 9.8) / 9.8;
					double speed = 70 * Math.pow(multiplier, 1.0/STEERING_SENSITIVITY);
					int leftSpeed = (int)(165);
					int rightSpeed = (int) (20 + speed);
					byte[] buf = new byte[] { (byte) 0x03, (byte) leftSpeed, (byte) rightSpeed };
					Log.d("Wheel", "left " + leftSpeed);
					characteristicTx.setValue(buf);
					mBluetoothLeService.writeCharacteristic(characteristicTx);
				}
				else if (event.values[1] < -1) {
					double multiplier = Math.min(Math.abs(event.values[1]), 9.8) / 9.8;
					double speed = 70 * Math.pow(multiplier, 1.0/STEERING_SENSITIVITY);
					int leftSpeed = (int)(165 - speed);
					int rightSpeed = (int) (20);
					byte[] buf = new byte[] { (byte) 0x03, (byte) leftSpeed, (byte) rightSpeed };
					Log.d("Wheel", "left " + leftSpeed);
					characteristicTx.setValue(buf);
					mBluetoothLeService.writeCharacteristic(characteristicTx);
					
				}
				else {
					byte[] buf = new byte[] { (byte) 0x03, (byte) 165, (byte) 20 };
					Log.d("Wheel", "left " + 165);
					characteristicTx.setValue(buf);
					mBluetoothLeService.writeCharacteristic(characteristicTx);
					
				}
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}
}
