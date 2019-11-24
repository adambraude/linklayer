package wifi;
import rf.RF;
import java.io.PrintWriter;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This thread takes packets from a queue and sends them
 * across the network.
 * 
 * @author Braude and Corpron
 *
 */
public class Sender implements Runnable {

	private RF theRF;
	private short ourMAC;
	private PrintWriter output;
	private ArrayBlockingQueue<Packet> toSend;
	private ArrayBlockingQueue<Packet> ackQueue;

    // DIFS is defined as the SIFS time + 2*SlotTime
	private static int DIFS = RF.aSIFSTime + 2*RF.aSlotTime;

	
	public Sender(RF theRF, short ourMAC, PrintWriter output, ArrayBlockingQueue<Packet> toSend,ArrayBlockingQueue<Packet> ackQueue) {
		this.theRF = theRF;
		this.ourMAC = ourMAC;
		this.output = output;
		this.toSend = toSend;
		this.ackQueue = ackQueue;
	}
	
	@Override
	public void run() {
	    // Outer while loop for sending new packets of data
        while (true) {
            Packet packet;
            // Wait for data to send
            try {
                packet = toSend.take();
            } catch (Exception e) {
                if (LinkLayer.debugLevel() > 0) output.println("Sender: error while retrieving packet");
                continue;
            }

            // Need to find out when expCounter is supposed to increment, currently always waits aCWmin
            // resets on new packet to send
            int expCounter = 0;
            int sendCount = 1;

            boolean sent = false;
            // If a packet sent doesn't receive an ack, always go down right side of chart
            boolean cantSkip = false;
            // Inner while loop in case need to resend current packet of data
            if (LinkLayer.debugLevel() == 3) output.println("Sender: Sending Packet");
            while (!sent) {
                if (LinkLayer.debugLevel() == 3) output.println("Sender: Sending Packet attempt #"+sendCount);
                if (sendCount > RF.dot11RetryLimit) {
                    if (LinkLayer.debugLevel() == 3) output.print("Sender: Packet reached send attempt limit");
                    break;
                }
                // Do left half of the diagram
                if (LinkLayer.debugLevel() == 3) output.println("Sender: Starting left half of flow chart");
                boolean jumpToACK = false;
                // if left side is viable, attempt it
                if (!cantSkip) {
                    // If true, can skip to sending
                    if (LinkLayer.debugLevel() == 3) output.println("Sender: Medium idle, send early");
                    jumpToACK = leftHalf(packet);
                }

                // Starting right part of diagram
                if (LinkLayer.debugLevel() == 3) output.println("Sender: Start right half of flow diagram");

                // If jumpToACK is true skip right DIFS waiting
                if (!jumpToACK) rightDIFSWait();

                // If packet hasn't been sent, go through exponential backoff wait time and send the packet
                if (!jumpToACK) {
                    if (LinkLayer.debugLevel() == 3) output.println("Sender: Starting Exponential Backoff");
                    int slotsToWait = calculateSlots(expCounter);
                    expBackoff(slotsToWait);
                }

                // Done waiting for exponential backoff, or is able to send early, so send data.
                if (LinkLayer.debugLevel() == 3) output.println("Sender: Sent Data");
                theRF.transmit(packet.getPacket());

                // now need to wait for an ack to appear in the ack queue
                if (LinkLayer.debugLevel() == 3) output.println("Sender: Waiting for ACK");
                boolean gotACK = waitForACK(packet);

                // Either move on to next packet, or remain on current
                // If we got the wrong ack, increment exp and make sure packet has resent bit
                if (!gotACK) {
                    if (LinkLayer.debugLevel() == 3) output.println("Sender: Didn't receive ack, resending");
                    if (!packet.getRetry()) {
                        packet = new Packet(packet.getSrc(), packet.getDest(), packet.getData(), packet.getType(), packet.getSeq(), true);
                    }
                    expCounter ++;
                    sendCount ++;
                    cantSkip = true;
                } else {
                    // If it is the correct ack, move on to the next packet.
                    if (LinkLayer.debugLevel() == 3) output.print("Sender: Received ACK, moving onto next packet");
                    sent = true;
                }
            }
        }
	}

	// Goes through left half of diagram
	private boolean leftHalf(Packet packet) {
        // Check if medium is idle
        boolean inUse = theRF.inUse();

        // Check if the medium is initially idle
        boolean firstTry = !inUse;

        // if the medium is immediately idle, wait DIFS
        if (firstTry) {
            try {
                Thread.sleep(DIFS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                if (LinkLayer.debugLevel() > 0) output.println("Sender: error while sleeping DIFS");
            }

            // If medium is idle again, skip to sending the data, else go through right side
            inUse = theRF.inUse();
            return !inUse;
        }

        //Else move to right half of diagram
        return false;
    }

    private void rightDIFSWait() {
	    //If we are in here, then the medium is in use
        boolean inUse = true;
        while (inUse) {
            // If medium was busy first try, or after the second check, wait for medium to not be idle
            while (inUse) {
                try {
                    // If medium not idle, wait sifs and a slot time as specified
                    Thread.sleep(RF.aSIFSTime+RF.aSlotTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    if (LinkLayer.debugLevel() > 0) output.println("Sender: error while sleeping");
                }

                inUse = theRF.inUse();
            }

            // Then wait DIFS
            try {
                Thread.sleep(DIFS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                if (LinkLayer.debugLevel() > 0) output.println("Sender: error while sleeping DIFS");
            }

            // Then check if the thread is in use. If it is, reset back. If not, skip to send.
            inUse = theRF.inUse();
        }
    }

	private int calculateSlots(int expCounter) {
        // exp backoff time is calculated by Random()*aSlotTime

        // Slots are always 1 less than a power of two, so to find the base exponent:
        // base = log(aCWmin + 1)
        // and (2^(base+0))-1 = aCWmin
        int base = (int) (Math.log(RF.aCWmin + 1) / Math.log(2));
        int totalSlots = (int) Math.pow(2, base+expCounter)-1;

        // If the exponential backoff is too large, use the max value
        if (totalSlots > RF.aCWmax) totalSlots = RF.aCWmax;

        Random rng = new Random();
        // Generate a random number of slots to wait
        int slotsToWait = (int) (rng.nextDouble()*totalSlots);

        // Make sure we wait at least 1 slot
        slotsToWait += 1;

        return slotsToWait;
    }

    private void expBackoff(int slotsToWait) {
	    // Go through loop of time waiting here
        // Decrement by a slot time, then check if medium is still idle, if not, wait until it is
        while (slotsToWait != 0) {
            // If the medium is idle, wait for a slot, and count down
            if (!theRF.inUse()) {
                try {
                    Thread.sleep(RF.aSlotTime);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (LinkLayer.debugLevel() > 0) output.println("Sender: error while sleeping");
                }

                slotsToWait --;
            }
            // If the medium is not idle, wait for it to be idle
            else {
                // Wait for medium to be idle again
                try {
                    // wait sifs and a slot time as specified
                    Thread.sleep(RF.aSIFSTime+RF.aSlotTime);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (LinkLayer.debugLevel() > 0) output.println("Sender: error while sleeping");
                }
            }
        }
    }

    private boolean waitForACK(Packet packet) {
	    if (packet.getDest() == -1) return true;

        Packet ack = null;
        // wait for ack for the timeout time
        int CONSTANT = 1000;
        try {
            ack = ackQueue.poll(RF.aSlotTime+RF.aSIFSTime+CONSTANT, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            if (LinkLayer.debugLevel() > 0) output.println("Sender: Error in waiting for ACK");
        }

        // If we received an ACK, and it is for the correct packet, return true.
        return (ack != null && ack.getSeq() == packet.getSeq());
    }
}
