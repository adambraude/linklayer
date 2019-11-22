package wifi;
import rf.RF;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * This thread listens to the RF layer and delivers
 * incoming messages to a queue.
 * 
 * @author Corpron, Braude
 *
 */
public class Receiver implements Runnable {

	private RF theRF;
	private short ourMAC;
	private PrintWriter output;
	private ArrayBlockingQueue<Packet> received;
	private ArrayBlockingQueue<Packet> ackQueue;
	
	//Unused, so far
	//private HashMap<Short, Integer> incomingSeq = new HashMap<>();
	
	public Receiver(RF theRF, short ourMAC, PrintWriter output, ArrayBlockingQueue<Packet> received, ArrayBlockingQueue<Packet> ackQueue) {
		this.theRF = theRF;
		this.ourMAC = ourMAC;
		this.output = output;
		this.received = received;
		this.ackQueue = ackQueue;
	}
	
	//Given a packet, sends an appropriate ACK
	private void sendAck(Packet p) {
		if (LinkLayer.debugLevel() > 0) output.println("Receiver: sending ack to " + p.getSrc());
		Packet ack = new Packet(ourMAC, p.getSrc(), new byte[0], Packet.FT_ACK, p.getSeq(), false);
		try {
			//For sending an ACK, we can just wait SIFS and then go.
			//This works because we're ignoring PIFS messages for this simulation
			Thread.sleep(theRF.aSIFSTime);
			theRF.transmit(ack.getPacket());
		} catch (Exception e) {
			if (LinkLayer.debugLevel() == 2) output.println("Receiver: error trying to sleep.");
		}
	}
	
	@Override
	public void run() {
		boolean ours = false;
		Packet incoming = null;

		// Always check for incoming data
		while (true) {
			try {
				//Should block until data comes in
				byte[] packet = theRF.receive();
				incoming = new Packet(packet);

				if (!incoming.integrityCheck()) {
					if (LinkLayer.debugLevel() == 2) output.println("Receiver: received a damaged packet");
					continue;
				}
				
				
				//If the data is meant for us, or for everyone, mark it
				if (incoming.getDest() == this.ourMAC || incoming.getDest() == -1) {
					ours = true;
					if (LinkLayer.debugLevel() == 2) output.println("Receiver: received a packet for us!");
				} else {
					if (LinkLayer.debugLevel() == 2) output.println("Receiver: packet received, but it's not ours.");
				}

			} catch (Exception e){
				if (LinkLayer.debugLevel() == 2) output.println("Receiver: rec interrupted!");
			}

			try {
				if (incoming == null) {
					if (LinkLayer.debugLevel() == 2) output.println("Receiver: error in viewing incoming packet");
				} else {
					// if ours, send data up, reset marker
					if (ours) {
						if (incoming.getType() == Packet.FT_ACK) {
							if (LinkLayer.debugLevel() == 2) output.println("Received an ACK, passing to sender");
							ackQueue.add(incoming);
							continue;
						}
						if (incoming.getType() == Packet.FT_DATA) {
							if (LinkLayer.debugLevel() == 2) output.println("Received a data packet");
							received.put(incoming);
							if (incoming.getDest() == this.ourMAC) {
								sendAck(incoming);
							}
						}
						ours = false;
					}
				}
			} catch (Exception e){
				if (LinkLayer.debugLevel() == 2) output.println("Receiver: error returning incoming packet");
			}
		}
	}

}
