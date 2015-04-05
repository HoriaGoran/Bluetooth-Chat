package test.bluetooth.gga.com.bluetoothtest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

public class ServerThread extends Thread
{
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mServerSocket;
    private List<ServerReadThread> serverReadThread_list;
    private Handler handler;
    private UUID my_uuid;
    private String app_name;
    private Semaphore semaphore;
    private volatile boolean running = false;

    public boolean isStrated()
    {
        return running;
    }

    public ServerThread(String app_name, BluetoothAdapter bluetoothAdapter, Handler handler)
    {
        this.running = true;
        this.mBluetoothAdapter = bluetoothAdapter;
        this.my_uuid = DeviceUuidFactory.getDefaultUUID();
        this.handler = handler;
        this.app_name = app_name;
        this.serverReadThread_list = new ArrayList<ServerReadThread>();
        this.semaphore = new Semaphore(0);
    }

    @Override
    public void run()
    {
        super.run();
        int client_counter = 0;

        // Keep listening until exception occurs or a mSocket is returned
        while(running)
        {
            if(null == mServerSocket)
            {
                try
                {
                    // MY_UUID is the app's UUID string, also used by the client code
                    mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(app_name, my_uuid);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }

            if(null == mServerSocket)
            {
                doLog("ServerThread run ERROR : null == mSocket -> trying again");

                try
                {
                    Thread.sleep(500);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }

                continue;
            }

            try
            {
                if(client_counter >= 10) // we support a maximum of 10 clients, numbered from 0 to 9
                {
                    doLog("ServerThread run max no of clients reached, server can not accept more");

                    mServerSocket.close();
                    mServerSocket = null;
                    break;
                }

                doLog("ServerThread run BEFORE accept");

                BluetoothSocket mSocket = mServerSocket.accept();

                doLog("ServerThread we had accept");

                // If a connection was accepted
                if(mSocket != null)
                {
                    // Do work to manage the connection (in a separate thread)
                    ServerReadThread serverReadThread = new ServerReadThread(mSocket);
                    serverReadThread.start();

                    try
                    {//wait here until the serverReadThread is ready
                        semaphore.acquire();
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace();
                    }

                    // Send an unique ID back to the client, that will be the client's name as long as he is connected to the server.
                    // When the client talks with the server, it will present his name first and the server will know how to differentiate between clients.
                    String client_name = "" + client_counter++;
                    serverReadThread.write(client_name);
                    serverReadThread.setClientName(client_name);

                    serverReadThread_list.add(serverReadThread);
                }
                else
                {
                    doLog("ServerThread mSocket == null");
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
                doLog("ServerThread exception : " + e.getMessage());
            }
        }

        doLog("ServerThread EXIT");
    }


//  Will cancel the listening mSocket, and cause the thread to finish
    public void cancel()
    {
        clean();
    }


    public void clean()
    {
        doLog("ServerThread clean 1");

        try
        {
            if(null != mServerSocket)
            {
                mServerSocket.close();
                mServerSocket = null;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            doLog("ServerThread clean exception1 : " + e.getMessage());
        }

        try
        {
            for(ServerReadThread srt : serverReadThread_list)
            {
                srt.terminate();
                srt = null;
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            doLog("ServerThread clean exception3 : " + e.getMessage());
        }

        running = false;

        doLog("ServerThread clean 2");
    }


    // Call this from the main activity to send data to the clients
    public void write(String str)
    {
        doLog("ServerThread write clinets no: " + serverReadThread_list.size());

        //Show the message in my chat as well
        Message msg = handler.obtainMessage(MainActivity.What.RECEIVED.get());
        Bundle b = new Bundle();
        b.putSerializable(MainActivity.who, MainActivity.ChatPerson.ME);
        b.putString(MainActivity.mesg, str);
        msg.setData(b);
        handler.sendMessage(msg);

        //Send the data to all connected clients
        for(ServerReadThread srt : serverReadThread_list)
        {
            srt.write(str);
        }
    }


    private class ServerReadThread extends Thread
    {
        private InputStream mInStream;
        private BluetoothSocket mSocket;
        private OutputStream mOutStream;
        private String client_name;
        private volatile boolean alive = true;

        public ServerReadThread(BluetoothSocket mSocket)
        {
            this.mSocket = mSocket;

            doLog("ServerReadThread constructor");
        }

        @Override
        public void run()
        {
            super.run();

            doLog("ServerReadThread CONNECTED");

            try
            {
                mOutStream = mSocket.getOutputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            //Inform the app to kill the client thread since it's a server now
            Message msg = handler.obtainMessage(MainActivity.What.KILL_CLIENT.get());
            handler.sendMessage(msg);

            //Switch app mode in chat
            msg = handler.obtainMessage(MainActivity.What.CHAT.get());
            handler.sendMessage(msg);

            byte[] buffer = new byte[1024]; //buffer store for the stream
            int readSize; //bytes returned from read()

            try
            {
                mInStream = mSocket.getInputStream();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

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

                        //get the client name
                        char client_name = str.charAt(0);

                        //remove the client name from the message
                        str = str.substring(1);

                        // Send the obtained bytes to the UI activity
                        msg = handler.obtainMessage(MainActivity.What.RECEIVED.get());

                        Bundle b = new Bundle();
                        b.putSerializable(MainActivity.who, MainActivity.ChatPerson.OTHER);
                        b.putString(MainActivity.mesg, str);
                        msg.setData(b);

                        handler.sendMessage(msg);


                        //Send the data to all connected clients
                        for(ServerReadThread sr : serverReadThread_list)
                        {//but not to the source
                            if(!sr.getClientName().equalsIgnoreCase(client_name + ""))
                            {
                                sr.write(str);
                            }
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


        public void setClientName(String client_name)
        {
            this.client_name = client_name;
        }


        public String getClientName()
        {
            return this.client_name;
        }


        public void terminate()
        {
            alive = false;

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
                doLog("ServerReadThread clean exception2 : " + e.getMessage());
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
                doLog("ServerThread clean exception4 : " + e.getMessage());
            }
        }


        public void write(String str)
        {
            if(null == str)
            {
                doLog("ServerReadThread write str null");
                return;
            }

            try
            {
                if(null != mOutStream)
                {
                    mOutStream.write(str.getBytes());
                }
                else
                {
                    doLog("ServerReadThread write null == mOutStream");
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
                doLog("ServerReadThread write exception: " + e.getMessage());
            }
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
