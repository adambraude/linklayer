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
	 * @param received
	 */
	public Packet (byte[] received) {
		packet = received;
	}
	
	public short getSrc() 
	{
		//TODO: extract from packet
		return 0;
	}
	
	public short getDest() {
		//TODO: extract from packet
		return 0;
	}
	
	public byte[] getData() {
		//TODO: extract from packet
		return new byte[1];
	}
	
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
