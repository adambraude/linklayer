package wifi;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import rf.RF;

/**
 * This layer is an implementation of {@link Dot11Interface} using the patented BradCo RF layer.
 * 
 * @author Braude, Corpron, Richards
 */
public class LinkLayer implements Dot11Interface 
{
	private static final int queue_size = 16;
	private static final int ack_size = 2;
	
	private static ArrayBlockingQueue<Packet> received = new ArrayBlockingQueue<>(queue_size);
	private static ArrayBlockingQueue<Packet> outgoingQueue = new ArrayBlockingQueue<>(queue_size);
	private static ArrayBlockingQueue<Packet> ackQueue = new ArrayBlockingQueue<>(ack_size);
	
	private RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to
	
	private static int debugLevel = 1;

	private Thread read;
	private Thread writer;

	/**
	 * Constructor takes a MAC address and the PrintWriter to which our output will
	 * be written.
	 * @param ourMAC  MAC address
	 * @param output  Output stream associated with GUI
	 */
	public LinkLayer(short ourMAC, PrintWriter output) {
		this.ourMAC = ourMAC;
		this.output = output;      
		theRF = new RF(null, null);
		if (debugLevel>0) output.println("LinkLayer initialized.");
		output.println("Send command 0 for a list of commands");

		// Launch threads
		Receiver rec = new Receiver(theRF, ourMAC, output, received, ackQueue);
		Sender writ = new Sender(theRF, ourMAC, output, outgoingQueue, ackQueue);

		read = new Thread(rec);
		writer = new Thread(writ);
		read.start();
		writer.start();
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {

		if (debugLevel > 0) output.println("LinkLayer: Sending "+len+" bytes to "+dest);

		//output.println("LinkLayer: Sending "+len+" bytes to "+dest);

		// construct packet from dest, data, source is our mac address
		Packet p = new Packet(ourMAC, dest, data, Packet.FT_DATA, 0, false);
		outgoingQueue.add(p);

		return Math.min(len, data.length);
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		if (debugLevel > 0) output.println("LinkLayer: blocking on recv()");
		// Block until we receive the data meant for us
		Packet incoming;
		try {
			incoming = received.take();
		} catch (Exception e) {
			if (debugLevel > 0) output.println("Didn't receive a packet, or ran into an error");
			return -1;
		}

		try {
			//give the transmission the necessary information
			t.setSourceAddr(incoming.getSrc());
			t.setDestAddr(incoming.getDest());

			int l = t.getBuf().length;
			byte[] data = incoming.getData();

			// Copies data to t, while respecting t buff size
            t.setBuf(Arrays.copyOf(data, l));

            //As per the specification, the remaining data is discarded
		} catch (Exception e) {
			if (debugLevel > 0) output.println("LinkLayer: Error when copying data");
			return -1;
		}

		// At this point, know that the message has been successfully received. This would be where
		// the ACK would be made and probably sent

		//Returns the amount of data stored
		return Math.min(incoming.getData().length, t.getBuf().length);
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		if (debugLevel > 0) output.println("LinkLayer: Faking a status() return value of 0");
		return 0;
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmd, int val) {
		if (debugLevel == 4) output.println("LinkLayer: Sending command "+cmd+" with value "+val);
		if (cmd == 0) {
			output.println(
					"Available commands:\n"
					+ "(0): help\n"
					+ "(1,x): set debug level\n"
					+ "\tx<1: silent mode"
					+ "\n\tx=1: default"
					+ "\n\tx=2: receiver details"
					+ "\n\tx=3: sender details"
					);
		}
		if (cmd == 0) {
			debugLevel = val;
		}
		return 0;
	}
	
	protected static int debugLevel() {
		return debugLevel;
	}
}
