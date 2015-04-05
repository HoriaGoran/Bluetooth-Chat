package test.bluetooth.gga.com.bluetoothtest;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class ClientThread extends Thread
{
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothSocket mSocket;
    private BluetoothDevice mDevice;
    private OutputStream mOutStream;
    private ClientReadThread clientReadThread;
    private Handler handler;
    private Semaphore semaphore;
    private UUID my_uuid;
    private String client_name = null;
    private volatile boolean started = false;


    public boolean isStrated()
    {
        return started;
    }


    @SuppressLint("NewApi")
    public ClientThread(BluetoothAdapter bluetoothAdapter, BluetoothDevice device, Handler handler)
    {
        this.started = true;
        this.mDevice = device;
        this.mBluetoothAdapter = bluetoothAdapter;

        // Get a BluetoothSocket to connect with the given BluetoothDevice
        // MY_UUID is the app's UUID string, also used by the server code
//        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
//        {
//            try
//            {
//                this.my_uuid = mDevice.getUuids()[0].getUuid();
//            }
//            catch(Exception e)
//            {
//                e.printStackTrace();
//                this.my_uuid = DeviceUuidFactory.getDefaultUUID();
//            }
//        }
//        else
//        {
            this.my_uuid = DeviceUuidFactory.getDefaultUUID();
//        }

        this.handler = handler;
        this.semaphore = new Semaphore(0);
    }


    @Override
    public void run()
    {
        super.run();

        doLog("ClientThread run 0");

        if(null == mSocket)
        {
            try
            {
                mSocket = mDevice.createRfcommSocketToServiceRecord(my_uuid);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        if(null == mSocket)
        {
            doLog("ClientThread run ERROR : null == mSocket -> EXITING");
            return;
        }

        doLog("ClientThread run 1");

        // Cancel discovery because it will slow down the connection
        mBluetoothAdapter.cancelDiscovery();

        try
        {
            sleep(500);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        try
        {
            doLog("ClientThread run 2");

            // Do work to manage the connection (in a separate thread)
            clientReadThread = new ClientReadThread();
            clientReadThread.start();

            try
            {//wait here until the clientReadThread is ready
                semaphore.acquire();
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }

            // Connect the device through the socket.
            // This will block until it succeeds or throws an exception
            mSocket.connect();
        }
        catch(IOException e)
        {
            e.printStackTrace();
            doLog("ClientThread exception : " + e.getMessage());

            try
            {// Unable to connect; close the socket and get out
                mSocket.close();
            }
            catch(IOException e1)
            {
                e1.printStackTrace();
            }

            mSocket = null;
            doLog("ClientThread exception EXIT");
            return;
        }

        doLog("ClientThread EXIT");
    }


    /** Will cancel an in-progress connection, and close the socket */
    /* Call this from the main activity to shutdown the connection */
    public void cancel()
    {
        clean();
    }


    public void clean()
    {
        doLog("ClientReadThread clean 1");

        try
        {
            if(null != mSocket)
            {
                mSocket.close();
                mSocket = null;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            doLog("ClientReadThread clean exception1 : " + e.getMessage());
        }

        try
        {
            if(null != clientReadThread)
            {
                clientReadThread.terminate();
                clientReadThread = null;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            doLog("ClientReadThread clean exception2 : " + e.getMessage());
        }

        try
        {
            if(null != mOutStream)
            {
                mOutStream.flush();
                mOutStream.close();
                mOutStream = null;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            doLog("ClientReadThread clean exception3 : " + e.getMessage());
        }

        doLog("ClientReadThread clean 2");
    }


    /* Call this from the main activity to send data to the remote device */
    public void write(String str)
    {
        if(null == str)
        {
            doLog("ClientReadThread write str null");
            return;
        }

        try
        {
            if(null == mOutStream)
            {
                mOutStream = mSocket.getOutputStream();
            }

            if(null != mOutStream)
            {
                String str2 = client_name + str;
                mOutStream.write(str2.getBytes());

                //Show the message in my chat as well
                Message msg = handler.obtainMessage(MainActivity.What.RECEIVED.get());
                Bundle b = new Bundle();
                b.putSerializable(MainActivity.who, MainActivity.ChatPerson.ME);
                b.putString(MainActivity.mesg, str);
                msg.setData(b);
                handler.sendMessage(msg);
            }
            else
            {
                doLog("ClientReadThread write null == mOutStream");
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            doLog("ClientReadThread write exception: " + e.getMessage());
        }
    }


    private class ClientReadThread extends Thread
    {
        private InputStream mInStream;
        private volatile boolean alive = true;

        public ClientReadThread()
        {
            doLog("ClientReadThread constructor");
        }

        @Override
        public void run()
        {
            super.run();

            doLog("ClientReadThread CONNECTED");

            try
            {
                mInStream = mSocket.getInputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            //Switch app mode in chat
            Message msg = handler.obtainMessage(MainActivity.What.CHAT.get());
            handler.sendMessage(msg);

            byte[] buffer = new byte[1024]; //buffer store for the stream
            int readSize; //bytes returned from read()

            //notify the other thread that we are ready
            semaphore.release();

            // Keep listening to the InputStream until an exception occurs
            while(alive)
            {
                try
                {
                    // Read from the InputStream
                    readSize = mInStream.read(buffer);

                    // Send the obtained bytes to the UI activity
                    if(readSize > 0)
                    {
                        byte[] read = Arrays.copyOf(buffer, readSize);
                        String str = new String(read);

                        if(str.trim().length() == 0)
                        {
                            continue;
                        }

                        if(null == client_name)
                        {
                            client_name = str;

                            doLog("ClientReadThread we have its name : " + client_name);
                        }
                        else
                        {
                            // Send the obtained bytes to the UI activity
                            msg = handler.obtainMessage(MainActivity.What.RECEIVED.get());

                            Bundle b = new Bundle();
                            b.putSerializable(MainActivity.who, MainActivity.ChatPerson.OTHER);
                            b.putString(MainActivity.mesg, str);
                            msg.setData(b);

                            handler.sendMessage(msg);
                        }
                    }
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                    break;
                }
            }

            if(null != mInStream)
            {
                try
                {
                    mInStream.close();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }

        public void terminate()
        {
            alive = false;
        }
    }


    private void doLog(String text)
    {
        Message msg = handler.obtainMessage(MainActivity.What.LOG.get());

        Bundle b = new Bundle();
        b.putString(MainActivity.log, text);
        msg.setData(b);

        handler.sendMessage(msg);
    }
}
