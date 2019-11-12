package wifi;
import rf.RF;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

public class Receiver implements Runnable {

	private RF theRF;
	private short ourMAC;
	private PrintWriter output;
	private ArrayBlockingQueue<Packet> received;
	
	public Receiver(RF theRF, short ourMAC, PrintWriter output, ArrayBlockingQueue<Packet> received) {
		this.theRF = theRF;
		this.ourMAC = ourMAC;
		this.output = output;
		this.received = received;
	}
	
	@Override
	public void run() {
		// TODO good things
		boolean ours = false;
		Packet incoming = null;

		// Block until we recieve data meant for us
		while (!ours) {
			try {
				//Should block until data is available
				byte[] packet = theRF.receive();
				incoming = new Packet(packet);

				//If the data is meant for us, or for everyone, leave the loop
				if (incoming.getDest() == this.ourMAC || incoming.getDest() == -1) {
					ours = true;
				}

			} catch (Exception e){
				output.println("LinkLayer: rec interrupted!");
			}

			try {
				if (incoming == null) {
					output.println("Error in viewing incoming packet");
					return;
				}
				received.put(incoming);
			} catch (Exception e){
				output.println("Error returning incoming packet");
			}
		}
	}

}
