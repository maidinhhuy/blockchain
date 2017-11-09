import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by huy on 4/21/17.
 */
public class OutputThread extends Thread {
    private Socket socket;

    // Private to mirror InputThread's structure. For OOP model it makes more sense for a method to simulate 'writing' data (evem though it is util the thread writes

    private ArrayList<String> outputBuffer;
    private boolean shouldContinue = true;

    public OutputThread(Socket socket) {
        this.socket = socket;
    }

    /**
     * Constantly checks outputBuffer for contents, and writes any contents in outputBuffer
     **/
    public void run() {
        try {
            outputBuffer = new ArrayList<String>();
            PrintWriter out = new PrintWriter(socket.getOutputStream());
            while (shouldContinue) {
                if (outputBuffer.size() > 0) {
                    if (outputBuffer.get(0) != null) {
                        for (int i = 0; i < outputBuffer.size(); i++) {
                            if (outputBuffer.get(i).length() > 20) {
                                System.out.println("Sending " + outputBuffer.get(i).substring(0, 20) + " to " + socket.getInetAddress());
                            } else {
                                System.out.println("Sending " + outputBuffer.get(i) + " to " + socket.getInetAddress());
                            }
                            out.print(outputBuffer.get(i));
                        }
                        outputBuffer = new ArrayList<String>();
                        outputBuffer.add(null);
                    }
                }
                Thread.sleep(100);

            }
            System.out.println("WHY AM I HERE");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Technically not writing to the network socket, but instead putting the passed-in data in a buffer to be written to the socket as soon as possible.
     *
     * @param data Data to write
     */
    public void write(String data) {
        if (data.length() > 20) {
            System.out.println("PUTTING INTO WRITE BUFFER: " + data.substring(0, 20) + "....");
        } else {
            System.out.println("PUTTING INTO WRITE BUFFER: " + data);
        }
        File f = new File("writebuffer");
        try {
            PrintWriter out = new PrintWriter(f);
            out.println("SENDING: " + data);
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (outputBuffer.size() > 0) {
            if (outputBuffer.get(0) == null) {
                outputBuffer.remove(0);
            }
        }
        outputBuffer.add(data);
    }


    /**
     * Stops thread during the next write cycle. I could'nt call it
     */
    public void shutdown() {
        shouldContinue = false;
    }

}
