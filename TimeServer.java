import rf.RF;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A simple networked program that sends and receives
 * MAC addresses and timestamps over the BradCo patented RF layer
 * 
 * @author Adam Braude
 */
public class TimeServer 
{
	/**
	 * Converts an array of bytes into a string of unsigned bytes
	 * @param bytes the array to read
	 * @return a string in the form [b1, b2, b3, ...] where bx is the xth byte
	 */
	public static String bytesToString(byte[] bytes) {
		String out = "[";
         for (byte b : bytes) {
         	int nxt = b;
         	if (b < 0) {
         		nxt = b + 256;
         	} else {
         		nxt = b;
         	}
         	out += " " + nxt;
         }
         out +=" ]";
         return out;
	}
	
    public static void main(String[] args)
    {
    	int mac;
    	int maxUnsignedShort = 2*Short.MAX_VALUE+1;
    	if (args.length == 0) {
    		mac = ThreadLocalRandom.current().nextInt(0, maxUnsignedShort);
    		System.out.println("Using random MAC address: " + mac);
    	} else if (args.length == 1) {
    		try {
    			mac = Integer.parseInt(args[0]);
    			if (mac > maxUnsignedShort || mac < 0) {
    				System.out.println("MAC address " + mac + " too big (>"+ maxUnsignedShort + ")or negative.");
    				return;
    			}
    			System.out.println("Using specified MAC address: " + mac);
    		} catch (Exception e) {
    			System.out.println("Invalid MAC address.");
    			return;
    		}
    	} else {
    		System.out.println("Usage: TimeServer [mac address (optional)]");
    		return;
    	}
    	// Create an instance of the RF layer. See documentation for
    	// info on parameters, but they're null here since we don't need
    	// to override any of its default settings.
        RF theRF = new RF(null, null);  
        
        Sender sender = new Sender(theRF, mac);
        Listener listener = new Listener(theRF);
        
        Thread t1 = new Thread(sender);
        Thread t2 = new Thread(listener);
        t1.start();
        t2.start();
        
        while(true); //keep going until the user says stop
        //System.exit(0);
    }
}