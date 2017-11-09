/**
 * Created by huy on 4/21/17.
 */

import java.net.*;

public class PeerThread extends Thread {
    private Socket socket;
    public InputThread inputThread;
    public OutputThread outputThread;

    public PeerThread(Socket socket) {
        this.socket = socket;
    }

    /**
     * As the name might suggest, each PeerThread runs on it's own thread, Additionally, each child network IO thread
     * runs on it own thread.
     **/
    public void run() {
        System.out.println("Gor connection from " + socket.getInetAddress() + ".");
        inputThread = new InputThread(socket);
        inputThread.start();
        outputThread = new OutputThread(socket);
        outputThread.start();
    }

    /**
     * Used to send data to a peer. Passthrough to outputthread.send
     *
     * @param data String of data to send
     **/
    public void send(String data) {
        if (outputThread == null) {
            System.out.println("Couldn't send " + data + " !!!!");
        } else {
            outputThread.write(data);
        }
    }

}
