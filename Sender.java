import rf.RF;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates a thread that broadcasts a MAC address and timestamp
 * every 0-7 seconds.
 * 
 * @author Adam Braude
 */
public class Sender implements Runnable {

	private RF rf;
	private int mac;
	
	private static final int maxSleep = 7000;
	
	public Sender(RF rf, int mac) {
		this.rf = rf;
		this.mac = mac;
	}
	
	@Override
	public void run() {
		System.out.println("Sender has been summoned for duty.");
		
		while (true) {
			// Put together an array of bytes to do a test send
	        byte[] buf = new byte[10];
	        buf[0] = (byte)(mac>>8);
	        buf[1] = (byte)mac;
	        
	        long time = rf.clock();
	        for (int i = 0; i < Long.BYTES; i++) {
	        	buf[buf.length - i - 1] = (byte)(time>>8*i);
	        }
	        
	        // Try to send it and see if it went out.
	        int bytesSent = rf.transmit(buf);
	        if (bytesSent != buf.length) {
	            System.err.println("Sender: only sent "+bytesSent+" bytes of data!");
	        } 
	        else {
	            System.out.print("Sent packet: " + mac + " " + time + "	" + TimeServer.bytesToString(buf) + "\n");
	        }
	        try {
				Thread.sleep(ThreadLocalRandom.current().nextInt(0, maxSleep));
			} catch (InterruptedException e) {
				System.out.println("Sender interrupted while trying to sleep! How rude!");
			}
		}

	}

}
