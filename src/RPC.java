/**
 * Created by huy on 4/21/17.
 */

import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * The RPC object handles or delegates network communication for all RPC requests.
 * RPC requests are stored in a public class variable, and RPC responses are placed into another public class variable.
 * In the future, a queue system will exist, allowing for high-volume RPC calls.
 * Additionally, the RPC currently provides no security. In the final release, RPC will require authentication.
 * RPC calls are used to send and receive coins, as well as publish blocks.
 */

public class RPC extends Thread {
    private int listenPort;
    public String request = null;

    public ArrayList<RPCThread> rpcThreads;

    public boolean shouldRun = true;

    /**
     * Standerd RPC port is 8016, one aove the P2P networking port,
     **/
    public RPC() {
        this.listenPort = 8016;
        this.rpcThreads = new ArrayList<RPCThread>();
    }

    /**
     * Alternate, currently-unused constructor to listen on a non-static
     *
     * @param listenPort Port to listen on
     */
    public RPC(int listenPort) {
        this.listenPort = listenPort;
        this.rpcThreads = new ArrayList<RPCThread>();
    }

    /**
     * Starts listening and handles RPCThreads.
     **/
    public void run() {
        try {
            ServerSocket socket = new ServerSocket(listenPort);
            while (shouldRun) {
                rpcThreads.add(new RPCThread(socket.accept()));
                rpcThreads.get(rpcThreads.size() - 1).start();
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
