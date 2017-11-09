/**
 * Created by huy on 4/21/17.
 */

import java.io.*;
import java.net.*;
import java.util.*;

public class InputThread extends Thread {
    private Socket socket;

    //Private instead of public so that object can control calls to receivedData. Acts as a buffer... the same data shouldn't be read more than once.

    private ArrayList<String> receivedData = new ArrayList<String>();

    /**
     * Constructor to set class socket variable
     **/
    public InputThread(Socket socket) {
        this.socket = socket;
    }

    /**
     * Constantly reads from the input stream of the socket, and save any received data to the ArrayList<String></>
     **/
    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String input;
            while (((input = in.readLine())) != null) {
                receivedData.add(input);
                System.out.println("RUN(): " + in);
                System.out.println("size: " + receivedData.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Doesnt actully 'read data' as that's done asynchronously in the threaded run function
     * However, readData is an easy way to think about it-as eceivedData acts as a buffer, holding received data until the deamon is ready to handle it.
     * Generally, the size of receivedData will be small. However, in some instance
     *
     * @return ArrayList<String> Data pulled from receivedData
     **/
    @SuppressWarnings("unused")
    public ArrayList<String> readData() {
        ArrayList<String> inputBuffer = new ArrayList<String>(receivedData);
        if (inputBuffer == null) {
            inputBuffer = new ArrayList<String>();
        }
        receivedData = new ArrayList<String>();
        return inputBuffer;
    }

}
