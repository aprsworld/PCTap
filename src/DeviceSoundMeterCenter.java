

public class DeviceSoundMeterCenter extends Device {

	/* Memcache client for logging */
	protected MemcacheListener memcache;
	protected String name;
	
	public boolean debug=true;

	public String toString() {
		return	"type: " + Device.getDeviceStringByValue(type) +
				" typeWorld: " + typeWorld + 
				" transmitEvery: " + transmitEvery + "\n" +
				" deviceSerialNumber: " + serialNumber;
	}

	

	public DeviceSoundMeterCenter(String n, MemcacheListener m) {
		memcache=m;
		name=n;
	}

	/*
	 * typedef enum _exception{ILLEGAL_FUNCTION=1,ILLEGAL_DATA_ADDRESS=2, 
ILLEGAL_DATA_VALUE=3,SLAVE_DEVICE_FAILURE=4,ACKNOWLEDGE=5,SLAVE_DEVICE_BUSY=6, 
MEMORY_PARITY_ERROR=8,GATEWAY_PATH_UNAVAILABLE=10,GATEWAY_TARGET_NO_RESPONSE=11,
TIMEOUT=12} exception
	 */

	public DataRSTap queryNow(SerialReaderSoundmeterCenter sm) {			
		DataRSTap rsData = new DataRSTap(System.currentTimeMillis(),0,typeWorld,serialNumber);
//		System.err.println("#DeviceSoundmeterCenter created new rsData packet as:\n" + rsData);
		
		/* fire off the query packet */
		rsData.time = System.currentTimeMillis();


//		System.err.println("# Querying sound meter");
		
		/* assemble magic packet to query sound meter */
		int buff[]=new int[8];
		buff[0]=0x02;
		buff[1]=0x41;
		buff[2]=0x00;
		buff[3]=0x00;
		buff[4]=0x00;
		buff[5]=0x00;
		buff[6]=0x00;
		buff[7]=0x03;
		
		sm.setDataRSTap(rsData);
		sm.sendPacket(buff);


		return rsData;
	}

}
