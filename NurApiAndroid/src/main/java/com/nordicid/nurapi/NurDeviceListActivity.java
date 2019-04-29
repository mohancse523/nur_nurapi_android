/* 
  Copyright 2016- Nordic ID 
  NORDIC ID DEMO SOFTWARE DISCLAIMER

  You are about to use Nordic ID Demo Software ("Software"). 
  It is explicitly stated that Nordic ID does not give any kind of warranties, 
  expressed or implied, for this Software. Software is provided "as is" and with 
  all faults. Under no circumstances is Nordic ID liable for any direct, special, 
  incidental or indirect damages or for any economic consequential damages to you 
  or to any third party.

  The use of this software indicates your complete and unconditional understanding 
  of the terms of this disclaimer. 
  
  IF YOU DO NOT AGREE OF THE TERMS OF THIS DISCLAIMER, DO NOT USE THE SOFTWARE.  
*/

package com.nordicid.nurapi;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nordic ID on 18.7.2016.
 */

public class NurDeviceListActivity extends Activity implements NurDeviceScanner.NurDeviceScannerListener {
    public static final String TAG = "NurDeviceListActivity";

    public NurDeviceScanner mDeviceScanner;

    public static final String REQUESTED_DEVICE_TYPES = "TYPE_LIST";
    public static final int REQUEST_SELECT_DEVICE = 0x800A;

    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_OK = 1;

    public static final int REQ_BLE_DEVICES = (1 << 0);
    public static final int REQ_USB_DEVICES = (1 << 1);
    public static final int REQ_ETH_DEVICES = (1 << 2);
    public static final int LAST_DEVICE = REQ_ETH_DEVICES;
    public static final int ALL_DEVICES = (LAST_DEVICE << 1) - 1;
    public static final String STR_SCANTIMEOUT = "SCAN_TIMEOUT";
    public static final String STR_CHECK_NID = "NID_FILTER_CHECK";
    public static final String SPECSTR = "SPECSTR";
    private int mRequestedDevices = 0;
    private boolean mCheckNordicID = false;
    List<NurDeviceSpec> mDeviceList;
    private DeviceAdapter deviceAdapter;
    private static final long DEF_SCAN_PERIOD = 5000;
    // Default
    private long mScanPeriod = DEF_SCAN_PERIOD;
    private boolean mScanning = false;
    //private ProgressBar mScanProgress;
    private TextView mCancelButton, mScanButton;
    private TextView listEmptyMessage;
    private static NurApi mApi;

    public void onScanStarted(){
        Log.d(TAG,"Scan for devices started");
        //mScanProgress.setVisibility(View.VISIBLE);
        mScanButton.setText("");
        mScanning = true;
    }

    public void onDeviceFound(NurDeviceSpec device){
        mDeviceList.add(device);
        deviceAdapter.notifyDataSetChanged();
        if (mDeviceList.size() == 0){
            listEmptyMessage.setVisibility(View.VISIBLE);
        }else {
            listEmptyMessage.setVisibility(View.GONE);
            mScanButton.setText(R.string.select_device);
        }
    }

    public void onScanFinished(){
        Log.d(TAG,"Scan for devices finished");
        mScanButton.setText(R.string.text_scan);
        mScanning = false;
        if (mDeviceList.size() == 1){
            NurDeviceSpec deviceSpec;
            mDeviceScanner.stopScan();
            deviceSpec = mDeviceList.get(0);
            Bundle b = new Bundle();
            // e.g. "type=BLE;addr=00:00:00:00:00:00;name=XXX;rssi=-44"
            b.putString(SPECSTR, deviceSpec.getSpec());
            Intent result = new Intent();
            result.putExtras(b);
            setResult(RESULT_OK, result);
            finish();
        }
        //mScanProgress.setVisibility(View.GONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_nur_device_list);
        /*android.view.WindowManager.LayoutParams layoutParams = this.getWindow().getAttributes();
        layoutParams.gravity = Gravity.TOP;
        layoutParams.y = 200;*/


        //mScanProgress = (ProgressBar) findViewById(R.id.scan_progress);
        //mScanProgress.setVisibility(View.VISIBLE);
        //mScanProgress.setScaleY(0.5f);
        //mScanProgress.setScaleX(0.5f);
        mCancelButton = findViewById(R.id.cancel);
        mScanButton = findViewById(R.id.scan);
        listEmptyMessage = findViewById(R.id.list_empty_message);
        mRequestedDevices = getIntent().getIntExtra(REQUESTED_DEVICE_TYPES, ALL_DEVICES);
        mScanPeriod = getIntent().getLongExtra(STR_SCANTIMEOUT, DEF_SCAN_PERIOD);
        mCheckNordicID = getIntent().getBooleanExtra(STR_CHECK_NID, true);

        /** Device scanner **/
        mDeviceScanner = new NurDeviceScanner(this,mRequestedDevices,this, mApi);
        /** **/

        if ((mRequestedDevices & REQ_BLE_DEVICES) != 0) {
            mScanButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mScanning) {
                        mDeviceScanner.scanDevices();
                    }
                }
            });
            mCancelButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDeviceScanner.stopScan();
                    if (!mDeviceScanner.isEthQueryRunning())
                        finish();
                    else
                        showMessage("Ethernet query not ready...");
                }
            });
        }
        populateList();
    }

    private void populateList() {
        /* Initialize device list container */
        Log.d(TAG, "populateList");
        ListView newDevicesListView;
        mDeviceList = new ArrayList<NurDeviceSpec>();
        deviceAdapter = new DeviceAdapter(this, mDeviceList);
        newDevicesListView = findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(deviceAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);
        mDeviceScanner.scanDevices();
        if (mDeviceList.size() == 0){
            listEmptyMessage.setVisibility(View.VISIBLE);
        }else {
            listEmptyMessage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
    }

    @Override
    public void onStop() {
        super.onStop();
        mDeviceScanner.stopScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDeviceScanner.stopScan();
    }

    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            NurDeviceSpec deviceSpec;
            mDeviceScanner.stopScan();
            deviceSpec = mDeviceList.get(position);
            Bundle b = new Bundle();
            // e.g. "type=BLE;addr=00:00:00:00:00:00;name=XXX;rssi=-44"
            b.putString(SPECSTR, deviceSpec.getSpec());
            Intent result = new Intent();
            result.putExtras(b);
            setResult(RESULT_OK, result);
            finish();
        }
    };

    protected void onPause() {
        super.onPause();
        mDeviceScanner.stopScan();
    }

    class DeviceAdapter extends BaseAdapter {
        Context context;
        List<NurDeviceSpec> devices;
        LayoutInflater inflater;

        public DeviceAdapter(Context context, List<NurDeviceSpec> devices) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            this.devices = devices;
        }

        @Override
        public int getCount() {
            return devices.size();
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewGroup vg;

            if (convertView != null) {
                vg = (ViewGroup) convertView;
            } else {
                vg = (ViewGroup) inflater.inflate(com.nordicid.nurapi.R.layout.item_device, null);
            }
            NurDeviceSpec deviceSpec = devices.get(position);
            final TextView deviceName = vg.findViewById(R.id.device_name);
            deviceName.setText(deviceSpec.getName());
            if (position == getCount()-1){
                vg.setBackgroundResource(R.drawable.border_top_left_bottom_right_gray);
            }else {
                vg.setBackgroundResource(R.drawable.border_top_left_right_gray);
            }
            return vg;
        }
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public static void startDeviceRequest(Activity activity, NurApi api) throws InvalidParameterException
    {
        startDeviceRequest(activity, ALL_DEVICES, 0, false,api);
    }

    public static void startDeviceRequest(Activity activity, int devMask, NurApi api) throws InvalidParameterException
    {
        startDeviceRequest(activity, devMask, 0, false,api);
    }

    public static void startDeviceRequest(Activity activity, int devMask, long scanTimeout, boolean filterNID, NurApi api) throws InvalidParameterException
    {
        if (devMask == 0 || (devMask & ALL_DEVICES) == 0)
            throw new InvalidParameterException("startDeviceRequest(): no devices specified or context is invalid");
        mApi = api;
        Intent newIntent = new Intent(activity.getApplicationContext(), NurDeviceListActivity.class);
        newIntent.putExtra(REQUESTED_DEVICE_TYPES, devMask & ALL_DEVICES);
        newIntent.putExtra(STR_SCANTIMEOUT, scanTimeout);
        newIntent.putExtra(STR_CHECK_NID, filterNID);
        activity.startActivityForResult(newIntent, NurDeviceListActivity.REQUEST_SELECT_DEVICE);
    }
}
