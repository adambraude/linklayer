package wifi;

public class Packet {

	public static final int MAX_DATA = 2038;
	
	private byte[] packet;
	
	/**
	 * Given appropriate info, build a packet
	 * @param src source address
	 * @param dest destination address
	 * @param data up to 
	 */
	public Packet (short src, short dest, byte [] data) {
		if (data.length > MAX_DATA) {
			throw new IllegalArgumentException("Maximum packet length of " + MAX_DATA + " exceeded.");
		}
		//TODO: convert params to byte[]
	}
	
	/**
	 * Converts a received packet into a packet object
	 * @param received the packet recieved from a transmission
	 */
	public Packet (byte[] received) {
		packet = received;
	}

	/**
	 * Returns the source from the packet
	 * @return A short that is the source destination
	 */
	public short getSrc() 
	{
		//TODO: extract from packet
		return 0;
	}

	/**
	 * Returns a short that is the destination for the packet
	 * @return a short of the destination for the packet
	 */
	public short getDest() {
		//TODO: extract from packet
		return 0;
	}

	/**
	 * Method that extracts the data from the packet
	 * @return a byte array that is the data from the packet
	 */
	public byte[] getData() {
		//TODO: extract from packet
		return new byte[1];
	}

	/**
	 * Returns the packet in the form of a byte array
	 * @return the packet in byte array form
	 */
	public byte[] getPacket() {
		//This one is good
		return packet;
	}
	
	/**
	 * Verifies the integrity of the packet using the CRC
	 * Not required for CP2
	 * @return true if the packet passes the CRC check
	 */
	public boolean integrityCheck () {
		//TODO: entire method
		return true;
	}
	
	public static void main(String[] args) {
		// Unit-testing main TODO

	}

}
