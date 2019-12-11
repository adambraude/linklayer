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
	
	private static final int BEACONTIME = 1000; //placeholder
    private static final int MAX_PACKETS = 4;
	
	private HashMap<Short, Integer> incomingSeq = new HashMap<>();
	private HashMap<Short, Integer> broadcastSeq = new HashMap<>();

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
            LinkLayer.setStatus(LinkLayer.STATUS_UNSPECIFIED_ERROR);
		}
	}
	
	private void handleACK(Packet ack) {
		if (LinkLayer.debugLevel() == 2) output.println("Received an ACK, passing to sender");
		ackQueue.add(ack);
	}
	
	private void handleData(Packet incoming) {
		boolean duplicate = false;
		if (LinkLayer.debugLevel() == 2) output.println("Received a data packet");
		if (incoming.getDest() != -1) {
			//If we don't know the sender, start from 0
			if (!incomingSeq.containsKey(incoming.getSrc())) {
				incomingSeq.put(incoming.getSrc(), -1);
			}
			//Get sequence number of last seen packet
			int lastSeq = incomingSeq.get(incoming.getSrc());
			if (incoming.getSeq() > lastSeq+1) {
				if (LinkLayer.debugLevel() > 0) output.println("Warning: detected a gap in transmissions.");
			}
			if (incoming.getSeq() <= lastSeq) {
				if (LinkLayer.debugLevel() == 2) output.println("Received a duplicate packet.");
				duplicate = true;
			}
			incomingSeq.put(incoming.getSrc(), (int)incoming.getSeq());
		} else {
			//There may have been broadcasts from this sender before we joined, so assume
			//the first one we see has correct seq
			if (!broadcastSeq.containsKey(incoming.getSrc())) {
				broadcastSeq.put(incoming.getSrc(), (int)incoming.getSeq());
				if (LinkLayer.debugLevel() == 2) output.println("New broadcast sender: " + incoming.getSrc());
			} else {
				int lastSeq = broadcastSeq.get(incoming.getSrc());
				if (incoming.getSeq() > lastSeq+1) {
					if (LinkLayer.debugLevel() > 0) output.println("Warning: broadcast packet seq out of order.");
				}
				if (incoming.getSeq() <= lastSeq) {
					if (LinkLayer.debugLevel() == 2) output.println("Received a duplicate broadcast packet.");
					duplicate = true;
				}
				broadcastSeq.put(incoming.getSrc(), (int)incoming.getSeq());
			}
		}
		try {
			if (!duplicate) received.put(incoming);
		}
		catch (Exception e) {
			if (LinkLayer.debugLevel() == 2) output.println("Receiver: error passing packet to LinkLayer");
            LinkLayer.setStatus(LinkLayer.STATUS_UNSPECIFIED_ERROR);
		}
		if (incoming.getDest() == this.ourMAC) {
			sendAck(incoming);
		}
	}

	private void adjustClock(Packet packet, long time) {
	    long beaconTime = packet.getBeaconTime();
	    if (beaconTime != -1) {
	        long unpackTime = LinkLayer.getTime(theRF)-time;
	        long adjustedTime = beaconTime + unpackTime;
	        long dif = adjustedTime-LinkLayer.getTime(theRF);
	        if (adjustedTime > 0) {
                if (LinkLayer.debugLevel() == 2) output.println("Receiver: Clock Time adjusted");
	            LinkLayer.addToOffset(dif);
            }
            return;
        }
        if (LinkLayer.debugLevel() == 2) output.println("Receiver: adjustClock called on a packet that isn't a Beacon");
        LinkLayer.setStatus(LinkLayer.STATUS_UNSPECIFIED_ERROR);
    }

	@Override
	public void run() {
		Packet incoming;

		// Always check for incoming data
		while (true) {
			try {
				//Should block until data comes in
				byte[] packet = theRF.receive();
				long beaconTime = LinkLayer.getTime(theRF);
				incoming = new Packet(packet);
				
				if (!incoming.integrityCheck()) {
					if (LinkLayer.debugLevel() == 2) output.println("Receiver: received a damaged packet");
					continue;
				}
				
				//If the data is meant for us, or for everyone, mark it
				if (incoming.getDest() == this.ourMAC || incoming.getDest() == -1) {
					if (LinkLayer.debugLevel() == 2) output.println("Receiver: received a packet!");
					if (incoming.getType() == Packet.FT_ACK) {
						handleACK(incoming);
					} else if (incoming.getType() == Packet.FT_DATA && received.size() <= MAX_PACKETS) {
						handleData(incoming);
					}  else if (incoming.getType() == Packet.FT_BEACON) {
                        if (incoming.getType() == Packet.FT_BEACON) {
                            if (LinkLayer.debugLevel() == 2) output.println("Receiver: received a Beacon!");
                            adjustClock(incoming, beaconTime);
                        }
                    }
				} else {
					if (LinkLayer.debugLevel() == 2) output.println("Receiver: packet received, but it's not ours.");
				}

			} catch (Exception e){
				if (LinkLayer.debugLevel() == 2) output.println("Receiver: error receiving packet!");
                LinkLayer.setStatus(LinkLayer.STATUS_UNSPECIFIED_ERROR);
			}
		}
	}
}
