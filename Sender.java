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
 * @author Braude
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
                if (LinkLayer.debugLevel() > 0) output.print("Sender: error while retrieving packet");
                continue;
            }

            // Need to find out when expCounter is supposed to increment, currently always waits aCWmin
            // resets on new packet to send
            int expCounter = 0;

            boolean sent = false;
            // Inner while loop in case need to resend current packet of data
            while (!sent) {
                // When there is data to send, start
                boolean inUse = theRF.inUse();

                while (inUse) {
                    try {
                        Thread.sleep(RF.aSIFSTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        if (LinkLayer.debugLevel() > 0) output.println("Sender: error while sleeping");
                    }

                    inUse = theRF.inUse();
                }
                theRF.transmit(packet.getPacket());

                // Do left half of the diagram
                // If  true, package has been sent, now waiting on ACK
                // If false, medium was idle
                boolean jumpToACK = leftHalf(packet);

                // Starting right part of diagram

                // If jumpToACK is true skip right DIFS waiting
                if (!jumpToACK) rightDIFSWait();

                // If packet hasn't been sent, go through exponential backoff wait time and send the packet
                if (!jumpToACK) {
                    int slotsToWait = calculateSlots(expCounter);
                    expBackoff(slotsToWait);

                    // Done waiting for exponential backoff, so send data.
                    theRF.transmit(packet.getPacket());
                }

                // now need to wait for an ack to appear in the ack queue
                int sequence = packet.getSeq();
                boolean gotACK = waitForACK(sequence);

                // Either move on to next packet, or remain on current
                // If  we got the wrong ack, increment exp and make sure packet has resent bit
                if (!gotACK) {
                    if (!packet.getRetry()) {
                        packet = new Packet(packet.getSrc(), packet.getDest(), packet.getData(), packet.getType(), packet.getSeq(), true);
                    }
                    expCounter ++;
                } else {
                    // If it is the correct ack, move on to the next packet.
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
                output.println("Sender: error while sleeping DIFS");
            }

            // If medium is still idle, send. This bypasses waiting through exp. backoff
            // Either have a parameter, or deal with sending here. Have to skip to ACK section either way
            inUse = theRF.inUse();
            if (!inUse) {
                theRF.transmit(packet.getPacket());
                return true;
            }
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
                    // SIFS time is 100 milliseconds, just replacing with a class constant
                    Thread.sleep(RF.aSIFSTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    output.println("Sender: error while sleeping");
                }

                inUse = theRF.inUse();
            }

            // Then wait DIFS
            try {
                Thread.sleep(DIFS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                output.println("Sender: error while sleeping DIFS");
            }

            // Then check if the thread is in use. If it is, reset back. If not, move forward.
            inUse = theRF.inUse();
        }
    }

	private int calculateSlots(int expCounter) {
        // Pick exponential time here
        // exp backoff time is calculated by Random()*aSlotTime
        // aSlotTime is constant

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
                    output.println("Sender: error while sleeping");
                }

                slotsToWait --;
            }
            // If the medium is not idle, wait for it to be idle
            else {
                // Wait for medium to be idle again
                try {
                    Thread.sleep(RF.aSIFSTime);
                } catch (Exception e) {
                    e.printStackTrace();
                    output.println("Sender: error while sleeping");
                }
            }
        }
    }

    private boolean waitForACK(int sqnc) {
        Packet ack = null;
        try {
            ack = ackQueue.poll(10000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
            if (LinkLayer.debugLevel() > 0) output.println("Sender: Error in waiting for ACK");
        }

        // Create the packet with resend if it doesn't have it already
        // If it is the correct ack, move on to the next packet.
        return (ack != null && ack.getSeq() == sqnc);
    }
}
