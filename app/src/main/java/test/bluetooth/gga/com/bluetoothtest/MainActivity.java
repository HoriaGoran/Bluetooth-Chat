package test.bluetooth.gga.com.bluetoothtest;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends Activity implements View.OnClickListener, Handler.Callback
{
    public static final String who = "who";
    public static final String mesg = "mesg";
    public static final String log = "log";
    public static enum ChatPerson { ME, OTHER };
    private What [] wa = What.values();
    private final String TAG = "Horia";
    private final int REQUEST_ENABLE_BT = 110;
    private final int REQUEST_VISIBILITY_TIME = 300;
    private Context context = MainActivity.this;
    private RelativeLayout layout_chat_functionality;
    private Button btn_find;
    private Button btn_insert;
    private EditText edit_insert;
    private EditText edit_log;
    private ListView listview_device;
    private ListView listview_chat;
    private DeviceListAdapter deviceListAdapter;
    private ChatListAdapter chatListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private ClientThread client;
    private ServerThread server;
    private Handler handler;

    public static enum What
    {
        LOG(0),
        RECEIVED(1),
        KILL_CLIENT(2),
        CHAT(3);

        private int a;

        What(int a)
        {
            this.a = a;
        }

        public int get()
        {
            return a;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetoothBroadcast, filter);

        layout_chat_functionality = (RelativeLayout) findViewById(R.id.layout_chat_functionality);
        listview_device = (ListView) findViewById(R.id.listview_device);
        listview_chat = (ListView) findViewById(R.id.listview_chat);
        btn_find = (Button) findViewById(R.id.btn_find);
        btn_insert = (Button) findViewById(R.id.btn_insert);
        edit_insert = (EditText) findViewById(R.id.edit_insert);
        edit_log = (EditText) findViewById(R.id.edit_log);

        btn_find.setOnClickListener(this);
        btn_insert.setOnClickListener(this);

        ArrayList<BluetoothDevice> list = new ArrayList<BluetoothDevice>();
        deviceListAdapter = new DeviceListAdapter(context, list);
        listview_device.setAdapter(deviceListAdapter);
        listview_device.setOnItemClickListener(bluetoothItemClickListener);

        chatListAdapter = new ChatListAdapter(context);
        listview_chat.setAdapter(chatListAdapter);
        listview_chat.setOnItemClickListener(chatItemClickListener);
        listview_chat.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        listview_chat.setStackFromBottom(true);

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
        {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        else
        {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setCancelable(false)
                .setMessage("Choose mode: Client or Server.")
                .setPositiveButton("Server", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        makeDeviceVisible();

                        server = new ServerThread(getString(R.string.app_name), mBluetoothAdapter, handler);
                        server.start();
                    }
                })
                .setNeutralButton("Client", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        btn_find.setVisibility(View.VISIBLE);
                    }
                })
                .setNegativeButton("Exit", new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        finish();
                    }
                })
                .create();
        dialog.show();
    }


    private AdapterView.OnItemClickListener bluetoothItemClickListener = new AdapterView.OnItemClickListener()
    {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id)
        {
            client = new ClientThread(mBluetoothAdapter, deviceListAdapter.getItem(position), handler);
            client.start();

            deviceListAdapter.removeItem(position);
        }
    };

    private AdapterView.OnItemClickListener chatItemClickListener = new AdapterView.OnItemClickListener()
    {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id)
        {
            doLog("chatItemClickListener position: " + position);
        }
    };


    @Override
    public boolean handleMessage(Message msg)
    {
        switch(wa[msg.what])
        {
            case RECEIVED:
            {
                Bundle b = msg.getData();
                ChatPerson cp = (ChatPerson) b.getSerializable(who);
                String text = b.getString(mesg);

                ChatItem ci = new ChatItem(cp, text);
                chatListAdapter.add(ci);
                chatListAdapter.notifyDataSetChanged();
                break;
            }
            case LOG:
            {
                Bundle b = msg.getData();
                String text = b.getString(log);
                doLog(text);
                break;
            }
            case KILL_CLIENT:
            {
                //the user requested to connect the app as a server, so kill the client.
                if(null != client)
                {
                    client.cancel();
                    client = null;
                }

                //also disable the possibility to find other devices, since that is a feature of a client
                btn_find.setVisibility(View.GONE);
                break;
            }
            case CHAT:
            {
                edit_log.setVisibility(View.GONE);
                btn_find.setVisibility(View.GONE);
                listview_device.setVisibility(View.GONE);
                listview_chat.setVisibility(View.VISIBLE);
                layout_chat_functionality.setVisibility(View.VISIBLE);
                break;
            }
        }

        return false;
    }

    @Override
    public void onClick(View view)
    {
        switch(view.getId())
        {
            case R.id.btn_find:
            {
                if(mBluetoothAdapter == null)
                {
                    doLog("mBluetoothAdapter == null");
                    return;
                }

                if(!mBluetoothAdapter.isEnabled())
                {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                else
                {
                    getBoundedDevicesAndStartDiscovery();
                }

                break;
            }
            case R.id.btn_insert:
            {
                String send = edit_insert.getText().toString();
                edit_insert.setText("");

                if(null != server && null != client)
                {
                    doLog("insert both good - bad??");
                }
                else if(null != server && server.isStrated())
                {
                    server.write(send);
                }
                else if(null != client && client.isStrated())
                {
                    client.write(send);
                }
                else
                {
                    doLog("insert none bad??");
                }

                break;
            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        doLog("onActivityResult...");

        switch(requestCode)
        {
            case REQUEST_ENABLE_BT:
            {
                switch(resultCode)
                {
                    case RESULT_OK:
                    {
                        doLog("onActivityResult REQUEST_ENABLE_BT - RESULT_OK");

                        getBoundedDevicesAndStartDiscovery();

                        break;
                    }
                    case RESULT_CANCELED:
                    {
                        doLog("onActivityResult REQUEST_ENABLE_BT - RESULT_CANCELED");
                        break;
                    }
                    default:
                        doLog("onActivityResult REQUEST_ENABLE_BT - other result" + resultCode);
                        break;
                }

                break;
            }
            case REQUEST_VISIBILITY_TIME:
            {
                switch(resultCode)
                {
                    case RESULT_OK:
                    {
                        doLog("onActivityResult REQUEST_VISIBILITY_TIME - RESULT_OK");

                        break;
                    }
                    case RESULT_CANCELED:
                    {
                        doLog("onActivityResult REQUEST_VISIBILITY_TIME - RESULT_CANCELED");
                        break;
                    }
                    default:
                        doLog("onActivityResult REQUEST_VISIBILITY_TIME - other result" + resultCode);
                        break;
                }
                break;
            }
            default:
                doLog("onActivityResult code: " + requestCode);
                break;
        }
    }


    private BroadcastReceiver bluetoothBroadcast = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();

            if(BluetoothAdapter.ACTION_STATE_CHANGED.equalsIgnoreCase(action))
            {
                int new_state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                int old_state = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, 0);

                switch(new_state)
                {
                    case BluetoothAdapter.STATE_TURNING_ON:
                        doLog("ACTION_STATE_CHANGED - Bluetooth turning on");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        doLog("ACTION_STATE_CHANGED - Bluetooth turning off");
                        break;
                    default:
                        doLog("ACTION_STATE_CHANGED - default");
                        break;
                }

//              STATE_TURNING_ON, STATE_ON, STATE_TURNING_OFF, and STATE_OFF.
            }
            else if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equalsIgnoreCase(action))
            {
                        int new_mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
                        int old_mode = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, 0);

                        switch(new_mode)
                        {
                            case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
                                doLog("ACTION_SCAN_MODE_CHANGED - CONNECTABLE_DISCOVERABLE");
                                break;
                            case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
                                doLog("ACTION_SCAN_MODE_CHANGED - CONNECTABLE");
                                break;
                            case BluetoothAdapter.SCAN_MODE_NONE:
                                doLog("ACTION_SCAN_MODE_CHANGED - MODE_NONE");
                                break;
                            default:
                        doLog("ACTION_SCAN_MODE_CHANGED - default");
                        break;
                }
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_STARTED.equalsIgnoreCase(action))
            {
                doLog("ACTION_DISCOVERY_STARTED");
            }
            else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equalsIgnoreCase(action))
            {
                doLog("ACTION_DISCOVERY_FINISHED");

                if(deviceListAdapter.getCount() == 0)
                {
                    TextView tv = new TextView(context);
                    tv.setText("no device found");
                    listview_device.setEmptyView(tv);
                }
            }
            else if(BluetoothDevice.ACTION_FOUND.equalsIgnoreCase(action))
            {
                doLog("ACTION_FOUND");

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(!deviceListAdapter.contains(device))
                {
                    deviceListAdapter.addItem(device);
                }
            }
            else if(BluetoothDevice.ACTION_BOND_STATE_CHANGED.equalsIgnoreCase(action))
            {
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

                switch(state)
                {
                    case BluetoothDevice.BOND_BONDED:
                        doLog("ACTION_BOND_STATE_CHANGED - BOND_BONDED");

//                        btn_find.setVisibility(View.GONE);
//                        listview_device.setVisibility(View.GONE);
//                        listview_chat.setVisibility(View.VISIBLE);
//                        layout_chat_functionality.setVisibility(View.VISIBLE);

                        break;
                    case BluetoothDevice.BOND_BONDING:
                        doLog("ACTION_BOND_STATE_CHANGED - BOND_BONDING");
                        break;
                    case BluetoothDevice.BOND_NONE:
                        doLog("ACTION_BOND_STATE_CHANGED - BOND_NONE");
                        break;
                    default:
                        doLog("ACTION_BOND_STATE_CHANGED - default");
                        break;
                }
            }

        }
    };

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        try
        {
            if(null != server)
            {
                server.cancel();
                server = null;
            }

            if(null != client)
            {
                client.cancel();
                client = null;
            }

            unregisterReceiver(bluetoothBroadcast);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    private void getBoundedDevicesAndStartDiscovery()
    {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (null != pairedDevices && pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices)
            {
                if(!deviceListAdapter.contains(device))
                {
                    deviceListAdapter.add(device);
                }
            }
        }
        else
        {
            doLog("0 Bounded Devices");
        }

        boolean a = mBluetoothAdapter.startDiscovery();

        if(a)
            doLog("startDiscovery SUCCESS");
        else
            doLog("startDiscovery FAILED");
    }

    private class DeviceListAdapter extends ArrayAdapter<BluetoothDevice>
    {
        private ArrayList<BluetoothDevice> devices;//used just for contains() method

        public DeviceListAdapter(Context context, ArrayList<BluetoothDevice> objects)
        {
            super(context, 0, objects);
            devices = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            BluetoothDevice device = getItem(position);
            String dev_info = device.getName() + " - " + device.getAddress();

            TextView tv = (TextView) View.inflate(context, R.layout.device_row, null).findViewById(R.id.row_device);
            tv.setText(dev_info);

            return tv;
        }

        public boolean contains(BluetoothDevice dev)
        {
            if(null == dev)
            {
                return false;
            }

            if((null == devices) || (null != devices && devices.size() == 0))
            {
                return false;
            }
            else
            {
                boolean a = false;

                for(BluetoothDevice bd : devices)
                {
                    if(bd.getAddress().equalsIgnoreCase(dev.getAddress()))
                    {
                        a = true;
                        break;
                    }
                }

                return a;
            }
        }

        public void removeItem(int position)
        {
            try
            {
                this.remove(this.getItem(position));
                notifyDataSetChanged();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        public void addItem(BluetoothDevice dev)
        {
            if(null == dev)
            {
                doLog("addItem null == dev");
                return;
            }

            this.add(dev);
            notifyDataSetChanged();
        }
    }


    private class ChatListAdapter extends ArrayAdapter<ChatItem>
    {
        public ChatListAdapter(Context context)
        {
            super(context, 0);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            ViewHolder holder;

            if(convertView == null)
            {
                holder = new ViewHolder();
                holder.tv = new TextView(context);
                convertView = holder.tv;

                convertView.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) convertView.getTag();
            }

            ChatItem ci = getItem(position);
            holder.tv.setText(ci.text + "");

            switch(ci.who)
            {
                case ME:
                    holder.tv.setGravity(Gravity.RIGHT);
                    holder.tv.setTextColor(Color.GREEN);
                    break;
                case OTHER:
                    holder.tv.setGravity(Gravity.LEFT);
                    holder.tv.setTextColor(Color.RED);
                    break;
            }

            return convertView;
        }

        private class ViewHolder
        {
            public TextView tv;
        }
    }

    private class ChatItem
    {
        private ChatPerson who;
        private String text;

        public ChatItem(ChatPerson who, String text)
        {
            this.who = who;
            this.text = text;
        }
    }


    private void doLog(String str)
    {
        edit_log.append(str + "\n");
        Log.e(TAG, str + "");
    }


    private void makeDeviceVisible()
    {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, REQUEST_VISIBILITY_TIME);
        startActivity(discoverableIntent);
    }
}