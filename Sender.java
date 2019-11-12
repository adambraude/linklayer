package wifi;
import rf.RF;
import java.io.PrintWriter;
import java.util.concurrent.ArrayBlockingQueue;

public class Sender implements Runnable {

	private RF theRF;
	private short ourMAC;
	private PrintWriter output;
	private ArrayBlockingQueue<Packet> toSend;
	
	public Sender(RF theRF, short ourMAC, PrintWriter output, ArrayBlockingQueue<Packet> toSend) {
		this.theRF = theRF;
		this.ourMAC = ourMAC;
		this.output = output;
		this.toSend = toSend;
	}
	
	@Override
	public void run() {
		while (true) {
			Packet packet;
			try {
				packet = toSend.take();
			} catch (Exception e) {
				output.print("Sender: error while retrieving packet");
				continue;
			}
			
			boolean inUse = theRF.inUse();
			
			while (inUse) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
					output.println("Sender: error while sleeping");
				}

				inUse = theRF.inUse();
			}
			theRF.transmit(packet.getPacket());
		}
		
	}

}
