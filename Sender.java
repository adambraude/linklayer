package wifi;
import rf.RF;
import java.io.PrintWriter;
import java.util.Date;
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
	
	private static final int ACKTIME = 1190;
	
	//Measured with Win10/2.5Ghz i5/8GB RAM
	private static final int BEACONTIME = 1820;
	
	//absolute time of the next beacon, in ms
	private long nextBeacon;
	
	//used internally to calculate debug statistics
	private long btotal = 0;
	private long bnum = 0;
	private float bvg=0;
	private long atotal = 0;
	private long anum = 0;
	private float aavg=0;

	
	public Sender(RF theRF, short ourMAC, PrintWriter output, ArrayBlockingQueue<Packet> toSend,ArrayBlockingQueue<Packet> ackQueue) {
		this.theRF = theRF;
		this.ourMAC = ourMAC;
		this.output = output;
		this.toSend = toSend;
		this.ackQueue = ackQueue;
		nextBeacon = LinkLayer.getTime(theRF) + LinkLayer.beaconInterval();
	}
	
	@Override
	public void run() {
	    // Outer while loop for sending new packets of data
        while (true) {
            Packet packet;
            // Wait for data to send
            if (LinkLayer.beaconInterval() <= 0) {
            	//Beacons disabled
            	try {
                    packet = toSend.take();
                } catch (Exception e) {
                    if (LinkLayer.debugLevel() > 0) output.println("Sender: error while retrieving packet");
                    continue;
                }
            } else {
            	if (LinkLayer.getTime(theRF)>nextBeacon) {
                	long t = LinkLayer.getTime(theRF)+BEACONTIME;
                	packet = Packet.makeBeacon(ourMAC, t);
                } else {
                	try {
                        packet = toSend.poll(nextBeacon-LinkLayer.getTime(theRF),TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        if (LinkLayer.debugLevel() > 0) output.println("Sender: error while retrieving packet");
                        continue;
                    }
                	if (packet == null) {
                		long t = LinkLayer.getTime(theRF)+BEACONTIME;
                    	packet = Packet.makeBeacon(ourMAC, t);
                	}
                }
            }
            if (packet.getType() == Packet.FT_BEACON) {
            	while (nextBeacon < LinkLayer.getTime(theRF) && LinkLayer.beaconInterval() >0) {
            		nextBeacon+=LinkLayer.beaconInterval();
            	}
            }

            // Need to find out when expCounter is supposed to increment, currently always waits aCWmin
            // resets on new packet to send
            int expCounter = 0;
            int sendCount = 0;

            boolean sent = false;
            // If a packet sent doesn't receive an ack, always go down right side of chart
            boolean canSkip = true;
            // Inner while loop in case need to resend current packet of data
            if (LinkLayer.debugLevel() == 3 && packet.getType()!=Packet.FT_BEACON) output.println("Sender: Sending Packet");
            while (!sent) {
                if (sendCount > RF.dot11RetryLimit) {
                    if (LinkLayer.debugLevel() == 3&& packet.getType()!=Packet.FT_BEACON) output.print("Sender: Packet reached send attempt limit");
                    LinkLayer.setStatus(LinkLayer.STATUS_TX_FAILED);
                    break;
                }
                if (LinkLayer.debugLevel() == 3 && packet.getType()!=Packet.FT_BEACON) output.println("Sender: Sending Packet attempt #"+sendCount);
                // Do left half of the diagram
                if (LinkLayer.debugLevel() == 3 && packet.getType()!=Packet.FT_BEACON) output.println("Sender: Starting left half of flow chart");
                boolean jumpToSend = false;
                // if left side is viable, attempt it
                if (canSkip) {
                    // If true, can skip to sending
                    if (LinkLayer.debugLevel() == 3 && packet.getType()!=Packet.FT_BEACON) output.println("Sender: Medium idle, send early");
                    jumpToSend = leftHalf();
                }

                // Starting right part of diagram

                // If jumpToSend is true skip right DIFS waiting
                if (!jumpToSend) {
                    if (LinkLayer.debugLevel() == 3 && packet.getType()!=Packet.FT_BEACON) output.println("Sender: Start right half of flow diagram");
                    rightDIFSWait();
                }

                // If packet hasn't been sent, go through exponential backoff wait time and send the packet
                if (!jumpToSend) {
                    if (LinkLayer.debugLevel() == 3 && packet.getType()!=Packet.FT_BEACON) output.println("Sender: Starting Exponential Backoff");
                    int slotsToWait = calculateSlots(expCounter);
                    expBackoff(slotsToWait);
                }

                // Done waiting for exponential backoff, or is able to send early, so send data.
                if (LinkLayer.debugLevel() == 3 && packet.getType()!=Packet.FT_BEACON) output.println("Sender: Sending Data");
                long st=LinkLayer.getTime(theRF);
                if (packet.getType() == Packet.FT_BEACON) {
                	//Per Brad's instructions, rebuild the packet right before sending
                	packet = Packet.makeBeacon(ourMAC, LinkLayer.getTime(theRF)+BEACONTIME);
                }
                
                theRF.transmit(packet.getPacket());
                
                if (packet.getType() == Packet.FT_BEACON) {
            		if (LinkLayer.debugLevel() == 5) {
            			output.println("Sent beacon with time " +packet.getBeaconTime());
            			long ed = LinkLayer.getTime(theRF);
            			bnum++;
            			btotal += ed-st;
            			bvg = btotal/(float)bnum;
            			output.println("Took " + (ed-st) + " ms to send.");
            			output.println("Average send time for all beacons: " +bvg + " ms.");
            		}
                }

                // now need to wait for an ack to appear in the ack queue
                if (LinkLayer.debugLevel() == 3 && packet.getDest()!=-1) output.println("Sender: Waiting for ACK");
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
                    canSkip = false;
                } else {
                    // If it is the correct ack, move on to the next packet.
                    if (LinkLayer.debugLevel() == 3 && packet.getDest()!=-1) output.print("Sender: Received ACK, moving onto next packet");
                    if (LinkLayer.debugLevel() == 3 && packet.getDest()==-1 && packet.getType()!=Packet.FT_BEACON) output.print("Sender: Broadcast packet sent, moving to next");
                    LinkLayer.setStatus(LinkLayer.STATUS_TX_DELIVERED);
                    sent = true;
                }
            }
        }
	}

	// Goes through left half of diagram
	private boolean leftHalf() {
        // Check if medium is idle
        boolean inUse = theRF.inUse();

        // Check if the medium is initially idle
        boolean firstTry = !inUse;

        // if the medium is immediately idle, wait DIFS
        if (firstTry) {
            sleepRounded(DIFS);

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
                // If medium not idle, wait sifs and a slot time as specified
                sleepRounded(RF.aSIFSTime+RF.aSlotTime);

                inUse = theRF.inUse();
            }

            sleepRounded(DIFS);

            // Then check if the thread is in use. If it is, reset back. If not, skip to send.
            inUse = theRF.inUse();
        }
    }

	private int calculateSlots(int expCounter) {
        // exp backoff time is calculated by Random()*aSlotTime

        // Slots are always 1 less than a power of two, so to find the base exponent:
        // base = log(aCWmin + 1)
        // and (2^(base+0))-1 = aCWmin
        // Wait time can be zero
        int base = (int) (Math.log(RF.aCWmin + 1) / Math.log(2));
        int totalSlots = (int) Math.pow(2, base+expCounter)-1;

        // If the exponential backoff is too large, use the max value
        if (totalSlots > RF.aCWmax) totalSlots = RF.aCWmax;

        Random rng = new Random();
        
        int toReturn = 0;
        if (LinkLayer.slotSelection() == LinkLayer.SS_RANDOM) {
            // The plus one makes the range of slots [1, totalSlots+1], so
            // multiplying by a double makes the range for possible slots [0, totalSlots]
        	toReturn = (int) (rng.nextDouble()*(totalSlots+1));
        } else {
        	toReturn = totalSlots;
        }
        
        if (LinkLayer.debugLevel() == 3) output.print("Sender: Setting exponential backoff to " + toReturn +  " slots.");
        // Generate a random number of slots to wait
        return toReturn;
    }

    private void expBackoff(int slotsToWait) {
	    // Go through loop of time waiting here
        // Decrement by a slot time, then check if medium is still idle, if not, wait until it is
        while (slotsToWait != 0) {
            // If the medium is idle, wait for a slot, and count down
            if (!theRF.inUse()) {
                sleepRounded(RF.aSlotTime);
                slotsToWait --;
            }
            // If the medium is not idle, wait for it to be idle
            else {
                // Wait for medium to be idle again
                sleepRounded(RF.aSIFSTime+RF.aSlotTime);
            }
        }
    }

    private boolean waitForACK(Packet packet) {
	    if (packet.getDest() == -1) return true;

        int waitTime = RF.aSlotTime + RF.aSIFSTime + ACKTIME;
        Packet ack;
        // wait for ack for the timeout time
        while (waitTime > 0) {
            long start = LinkLayer.getTime(theRF);
            try {
                ack = ackQueue.poll(waitTime, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
                if (LinkLayer.debugLevel() > 0) output.println("Sender: Error in waiting for ACK");
                LinkLayer.setStatus(LinkLayer.STATUS_UNSPECIFIED_ERROR);
                break;
            }
            // Only get here if we have an ack, or wait entire poll time, in which case ack is null

            // If we don't get an ack, return false
            if (ack == null) {
                return false;
            }
            long end = LinkLayer.getTime(theRF);
            
            atotal += end-start;
            anum++;
            aavg = atotal/(float)anum;
            if (LinkLayer.debugLevel() == 3) output.println("Average ACK wait time: " + aavg + ".");

            // If we received the correct ACK return true.
            if (ack.getSeq() == packet.getSeq()) {
                return true;
            }

            // else we received an incorrect ACK.
            // Ignore ACK and wait again, but with reduced time
            
            long timeWaiting = end-start;

            waitTime -= (int) timeWaiting;
        }
        return false;
    }
    
    //Rounds a long number up to the nearest 50
    private long roundToFifty(long in) {
    	//Ex: 87%50=37. 50-37=13. 87+13 = 100.
    	long out = in+(50-(in%50));
    	return out;
    }
    
    private void sleepRounded(long waitTime) {
    	long t = LinkLayer.getTime(theRF);
    	long endTime = roundToFifty(t + waitTime);
    	try {
    		Thread.sleep(endTime-t);
    	} catch (Exception e) {
    		LinkLayer.setStatus(LinkLayer.STATUS_UNSPECIFIED_ERROR);
    	}
    }
}
