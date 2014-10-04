

public class DeviceFTUltrasonic extends Device {

	/* Memcache client for logging */
	protected MemcacheListener memcache;
	protected String name;

	public boolean debug=false;

	public String toString() {
		return	"type: " + Device.getDeviceStringByValue(type) +
				" typeWorld: " + typeWorld + 
				" transmitEvery: " + transmitEvery + "\n" +
				" deviceSerialNumber: " + serialNumber;
	}



	public DeviceFTUltrasonic(String n, MemcacheListener m) {
		memcache=m;
		name=n;

	}

	/*
	 * typedef enum _exception{ILLEGAL_FUNCTION=1,ILLEGAL_DATA_ADDRESS=2, 
ILLEGAL_DATA_VALUE=3,SLAVE_DEVICE_FAILURE=4,ACKNOWLEDGE=5,SLAVE_DEVICE_BUSY=6, 
MEMORY_PARITY_ERROR=8,GATEWAY_PATH_UNAVAILABLE=10,GATEWAY_TARGET_NO_RESPONSE=11,
TIMEOUT=12} exception
	 */

	/* The checksum value is calculated by Exclusive OR�ing (XOR�ing) all the bytes between (but not including) the 
	�$� and the �*� characters of the message. The resulting single byte value is then represented by 2 HEX 
	characters in the message string. The most significant character is transmitted first.
	Note: since a message only contains ASCII characters (which have values in the range 0-7F) the checksum 
	value will always be between 0 and 7F. */
	public static int calcChecksum(String s) {
		int checksum=0;

		/* must start with '$' */
		if ( '$' != s.charAt(0) )
			return -1;

		/* must end with '*' */
		if ( '*' != s.charAt(s.length()-1) )
			return -2;

		for ( int i=1 ; i<s.length()-1 ; i++ ) {
			//			System.err.printf("# xoring '%c'\n",s.charAt(i));
			checksum = checksum ^ ( (int) s.charAt(i) & 0xff );
		}

		return (checksum & 0xff);
	}

	public static boolean checkChecksum(String s) {
		// System.err.println("checkChecksum got s='" + s + "'");

		/* have to at least have "$*ab" */
		if ( s.length() < 4 ) {
			return false;
		}

		String body = s.substring(0,s.length()-2);

		int lChecksum = calcChecksum(body);
		int rChecksum = Integer.parseInt(s.substring(s.length()-2,s.length()), 16);

		//	System.err.printf("# s='%s' body='%s' lChecksum=0x%02x rChecksum=0x%02x\n", s,body, lChecksum, rChecksum );

		return (lChecksum == rChecksum);

	}

	public DataRSTap queryNow(SerialReaderFTUltrasonic ftu) {			
		//		if ( debug )
		//			System.err.println("# DeviceFTUltrasonic queryNow called");

		DataRSTap rsData = new DataRSTap(System.currentTimeMillis(),0,typeWorld,serialNumber);

		if ( debug )
			System.err.println("#DeviceFTUltrasonic created new rsData packet as:\n" + rsData);

		/* fire off the query packet */
		rsData.time = System.currentTimeMillis();


		/* ask for current conditions */
		String query="$01,WV?*";
		query = query + String.format("%02X",calcChecksum(query));
		ftu.setDataRSTap(rsData);
		ftu.sendPacket(query);

		try {
			Thread.sleep(100);
		} catch ( Exception e ) {
			System.err.println("# Caught exception while pausing between FT Ultrasonic commands.\n" + e);
		}


		/* ask for wind gust */
		query="$01,MM?*";
		query = query + String.format("%02X",calcChecksum(query));
		ftu.sendPacket(query);


		try {
			Thread.sleep(100);
		} catch ( Exception e ) {
			System.err.println("# Caught exception while pausing between FT Ultrasonic commands.\n" + e);
		}

		/* reset wind gust */
		query="$01,MMR*";
		query = query + String.format("%02X",calcChecksum(query));
		ftu.setDataRSTap(rsData);
		ftu.sendPacket(query);

		try {
			Thread.sleep(100);
		} catch ( Exception e ) {
			System.err.println("# Caught exception while pausing between FT Ultrasonic commands.\n" + e);
		}


		/* read temperature */ 
		query="$01,HT?*";
		query = query + String.format("%02X",calcChecksum(query));
		ftu.sendPacket(query);


		return rsData;
	}

}
