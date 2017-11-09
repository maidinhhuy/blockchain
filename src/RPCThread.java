/**
 * Created by huy on 4/21/17.
 */

import java.io.*;
import java.net.*;

public class RPCThread extends Thread {
    private Socket socket;

    public String request;
    public String response;

    /**
     * RPC Threads require a socket to function
     *
     * @param socket Socket
     */
    public RPCThread(Socket socket) {
        this.socket = socket;
    }

    /**
     * Manages the server<-->client communication.
     */
    public void run() {
        try {
            request = null;
            response = null;
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String input = "";
            out.println("Curecoin 2.0.0a3 daemon RPC");
            while ((input = in.readLine()) != null) {
                if (input.equalsIgnoreCase("HELP")) {
                    out.println("Commands: ");
                    out.println("send <amount> <destination>");
                    out.println("getinfo");
                    out.println("getbalance <address>");
                    out.println("submittx <rawtx>");
                    out.println("submitcert <cert>");
                    out.println("gethistory <address>");
                    out.println("");
                } else {
                    request = input;
                    while (response == null) {
                        while (response == null) {
                            Thread.sleep(25);
                        }
                        out.println(response + "\n</>");
                        request = null;
                        response = null;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("An RPC client has disconnected");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
