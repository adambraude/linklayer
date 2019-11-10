package wifi;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

import rf.RF;

/**
 * Use this layer as a starting point for your project code.  See {@link Dot11Interface} for more
 * details on these routines.
 * @author richards
 */
public class LinkLayer implements Dot11Interface 
{
	private static final int queue_size = 16;
	
	public static ArrayBlockingQueue<Packet> received = new ArrayBlockingQueue<>(queue_size);
	public static ArrayBlockingQueue<Packet> outgoingQueue = new ArrayBlockingQueue<>(queue_size);
	
	private RF theRF;           // You'll need one of these eventually
	private short ourMAC;       // Our MAC address
	private PrintWriter output; // The output stream we'll write to

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
		output.println("LinkLayer: Constructor ran.");
	}

	/**
	 * Send method takes a destination, a buffer (array) of data, and the number
	 * of bytes to send.  See docs for full description.
	 */
	public int send(short dest, byte[] data, int len) {
		output.println("LinkLayer: Sending "+len+" bytes to "+dest);
		theRF.transmit(data);
		return len;
	}

	/**
	 * Recv method blocks until data arrives, then writes it an address info into
	 * the Transmission object.  See docs for full description.
	 */
	public int recv(Transmission t) {
		output.println("LinkLayer: blocking on recv()");
		Packet incoming = null;
		boolean ours = false;

		// Block until we recieve the data meant for us
		while (!ours) {
			try {
				//Should block until data is available
				incoming = received.take();

				//If the data is meant for us, or for everyone, leave the loop
				if (incoming.getDest() == this.ourMAC || incoming.getDest() == -1) {
					ours = true;
				}
				
			} catch (Exception e){
				System.out.println("LinkLayer: rec interrupted!");
			}
		}

		try {
			t.setSourceAddr(incoming.getSrc());
			t.setDestAddr(incoming.getDest());
			int l = t.getBuf().length;
			for (int i = 0; i<l; i++) {
				t.getBuf()[i] = incoming.getData()[i];
			}
			//As per the specification, the remaining data is discarded
		} catch (Exception e) {
			output.print("LinkLayer: recv interrupted!");
		}
		if (incoming.getPacket().length  <= t.getBuf().length) {
			return incoming.getPacket().length;
		} else {
			return t.getBuf().length;
		}
	}

	/**
	 * Returns a current status code.  See docs for full description.
	 */
	public int status() {
		output.println("LinkLayer: Faking a status() return value of 0");
		return 0;
	}

	/**
	 * Passes command info to your link layer.  See docs for full description.
	 */
	public int command(int cmd, int val) {
		output.println("LinkLayer: Sending command "+cmd+" with value "+val);
		return 0;
	}
}
