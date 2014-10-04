import java.io.InputStream;

import org.apache.tools.ant.types.Commandline;

public class DeviceSystemCommand extends Device {

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



	public DeviceSystemCommand(String n, MemcacheListener m) {
		memcache=m;
		name=n;
	}

	/*
	 * typedef enum _exception{ILLEGAL_FUNCTION=1,ILLEGAL_DATA_ADDRESS=2, 
ILLEGAL_DATA_VALUE=3,SLAVE_DEVICE_FAILURE=4,ACKNOWLEDGE=5,SLAVE_DEVICE_BUSY=6, 
MEMORY_PARITY_ERROR=8,GATEWAY_PATH_UNAVAILABLE=10,GATEWAY_TARGET_NO_RESPONSE=11,
TIMEOUT=12} exception
	 */

	public DataRSTap queryNow(String command) {			
		DataRSTap rsData = new DataRSTap(System.currentTimeMillis(),0,typeWorld,serialNumber);

		/* rsData packets can only be 65535 bytes long ... might as well use that as an upper limit */
		//		byte[] raw = new byte[65535];
		int[] raw = new int[65535];
		int bytesRead=0;

		/* fire off the query packet */
		rsData.time = System.currentTimeMillis();

		System.err.println("# DeviceSystemCommand.queryNow should execute this command: " + command);

		String[] commandArgs = Commandline.translateCommandline(command);

		try { 
			ProcessBuilder builder = new ProcessBuilder(commandArgs);
			java.lang.Process p = builder.start();

			InputStream is = p.getInputStream();

			int ret=1;

			/* read in a byte at a time */
			for ( int i=0 ; ret >= 0 && i<raw.length ; i++ ) {
				ret = is.read();
//				System.err.printf("# [%d] is.read() returned 0x%x\n",i,ret);

				if ( ret < 0 )
					break;

				raw[i]=ret;
				bytesRead++;
			}


			//			while ( ret > 0 ) {
			//				ret = is.read(raw,bytesRead,raw.length-bytesRead);
			//				if ( ret > 0 ) 
			//					bytesRead += ret;
			//			}

			/* wait for program to finish, just in case */
			p.waitFor();

			if ( debug ) {
				//				for ( int i=0 ; i<bytesRead ; i++ ) { 
				//					System.err.printf("[%d] 0x%02X\n",i,raw[i]);
				//				}

				System.err.println("# DeviceSystemCommand process terminated with exit value=" + p.exitValue() + " and " + bytesRead + " bytes read");
			}
		} catch ( Exception e ) {
			System.err.println("# DeviceSystemCommand encountered following exception: " + e);
			return null;
		}



		rsData.qResult=DataRSTap.EXCEPTION_NONE;
		rsData.qbuffFromByteLengthInts(raw, bytesRead);

		
		
		//rsData.qbuffFromBytes(raw, bytesRead);

//		rsData.qBuff = new int[bytesRead];
//		System.arraycopy(raw, 0, rsData.qBuff, 0, bytesRead);


		if ( debug ) {
			for ( int i=0 ; i<bytesRead ; i++ ) { 
				System.err.printf("# [%d] raw=0x%02X\n",i,raw[i]);
			}
		}
		

		return rsData;
	}

}
