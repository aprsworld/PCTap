import com.focus_sw.fieldtalk.ChecksumException;
import com.focus_sw.fieldtalk.MbusMasterFunctions;
import com.focus_sw.fieldtalk.ReplyTimeoutException;

public class DeviceModbusSlave extends Device {
	/*
type=DEV_TYPE_MODBUS_4
typeWorld=DEVICE_TYPE_WORLD_WRENDAQ4_BASIC
transmitEvery=1
networkAddress=31
startRegister=0
nRegisters=39
	 */

	public int networkAddress;
	public int startRegister;
	public int nRegisters;
	final static boolean stdOut = false;
	final static boolean stdErr = false;

	public boolean debug=false;
	
	/* Memcache client for logging */
	protected MemcacheListener memcache;
	protected String name;
	
	public String toString() {
		return	"type: " + Device.getDeviceStringByValue(type) +
				" typeWorld: " + typeWorld + 
				" transmitEvery: " + transmitEvery + "\n" +
				" networkAddress: " + networkAddress + 
				" startRegister: " + startRegister + 
				" nRegisters: " + nRegisters +
				" deviceSerialNumber: " + serialNumber;
	}


	public DeviceModbusSlave(String n, MemcacheListener m) {
		memcache=m;
		name=n;
	}

	/*
	 * typedef enum _exception{ILLEGAL_FUNCTION=1,ILLEGAL_DATA_ADDRESS=2, 
ILLEGAL_DATA_VALUE=3,SLAVE_DEVICE_FAILURE=4,ACKNOWLEDGE=5,SLAVE_DEVICE_BUSY=6, 
MEMORY_PARITY_ERROR=8,GATEWAY_PATH_UNAVAILABLE=10,GATEWAY_TARGET_NO_RESPONSE=11,
TIMEOUT=12} exception
	 */

	public DataRSTap queryNow(MbusMasterFunctions mbus) {			
		short result[] = new short[nRegisters];
		int resultReal[] = new int[nRegisters];
		boolean resultBoolean[] = new boolean[nRegisters];

		DataRSTap rsData = new DataRSTap(System.currentTimeMillis(),0,typeWorld,serialNumber);

		try {

			if ( Device.DEV_TYPE_MODBUS_3 == type ) {
				mbus.readMultipleRegisters(networkAddress, startRegister+1, result);
			} else if ( Device.DEV_TYPE_MODBUS_4 == type ) {
				mbus.readInputRegisters(networkAddress, startRegister+1, result);
			} else if ( Device.DEV_TYPE_MODBUS_1 == type ) {
				mbus.readCoils(networkAddress, startRegister+1, resultBoolean);
			} else if ( Device.DEV_TYPE_MODBUS_2 == type ) {
				mbus.readInputDiscretes(networkAddress, startRegister+1, resultBoolean);
			} else if ( Device.DEV_TYPE_MODBUS_ADAM_4150_COUNTER == type ) {
				/* this is a special case for reading the counters on the ADAM_4150 and then immediately clearing their values */
				/* read the counter values */
				mbus.readInputRegisters(networkAddress, startRegister+1, result);

				if ( true ) {
					/* clear the counters */
					mbus.writeCoil(networkAddress, 33+1, true);
					mbus.writeCoil(networkAddress, 37+1, true);
					mbus.writeCoil(networkAddress, 41+1, true);
					mbus.writeCoil(networkAddress, 45+1, true);
					mbus.writeCoil(networkAddress, 49+1, true);
					mbus.writeCoil(networkAddress, 53+1, true);
					mbus.writeCoil(networkAddress, 57+1, true);
				}
				/* TODO: measure time since last pass and put that in element 0  */
			} else {
				if (stdErr) System.err.println("# ERROR! unimplemented protocol! Device information to follow:");
				if (stdErr) System.err.println(toString());
				return null;
			}
		} catch ( ReplyTimeoutException re ) {
			if (stdErr) System.err.println("# Device timed out waiting for reply. Device information to follow:");
			if (stdErr) System.err.println(toString());
			rsData.qResult=DataRSTap.EXCEPTION_TIMEOUT;
			return rsData;
		} catch ( ChecksumException ce ) {
			if (stdErr) System.err.println("# Checksum Exception. Exception.toString=" + ce.toString());
			if (stdErr) System.err.println(toString());
			ce.printStackTrace();
			rsData.qResult=DataRSTap.EXCEPTION_MEMORY_PARITY_ERROR;
			return rsData;
		} catch ( Exception e ) {
			if (stdErr) System.err.println("# queryNow caught: " + e);
			if (stdErr) System.err.println(toString());
			rsData.qResult=DataRSTap.EXCEPTION_SLAVE_DEVICE_FAILURE;
			return rsData;
		}

		rsData.time = System.currentTimeMillis();

		if ( Device.DEV_TYPE_MODBUS_3 == type || Device.DEV_TYPE_MODBUS_4 == type || Device.DEV_TYPE_MODBUS_ADAM_4150_COUNTER == type ) {
			/* convert from signed shorts to unsigned ... ie ints */
			for ( int i=0 ; i<result.length ; i++ ) {
				resultReal[i] = (0x0000FFFF & ((int)result[i]));
			}
			rsData.qBuff=resultReal;
		} else if ( Device.DEV_TYPE_MODBUS_1 == type || Device.DEV_TYPE_MODBUS_2 == type ) {
			int words16required = (int) Math.ceil(resultBoolean.length/16.0);


			if ( debug ) {
				if (stdErr) System.err.println("# Calculated " + words16required + " 16-bit words required for " + resultBoolean.length + " coils");
				for ( int i=0 ; i<resultBoolean.length ; i++ ) {
					if (stdErr) System.err.println("# boolean[" + i + "]=" + resultBoolean[i]);
				}
			}

			int wbuff[] = new int[words16required];

			int j=0;
			for ( int i=0 ; i<resultBoolean.length ; i++ ) {
				if ( i>0 && 0==(i%16) )
					j++;

				if ( resultBoolean[i] )
					wbuff[j] |= (1 << (i-j*16));

			}

			/* for some frickin reason we have to flip everything around */
			for ( int i=0 ; i<wbuff.length ; i++ ) {
				wbuff[i] = wbuff[i] | ((wbuff[i]&0xff)<<16);
				wbuff[i] = wbuff[i]>>8;

			}


			if ( debug ){
				for ( int i=0 ; i<wbuff.length ; i++ ) {
					if (stdErr) System.err.println("# wbuff[" + i + "]=" + String.format("%16s",Integer.toBinaryString(wbuff[i])).replace(' ', '0'));
					//				if (stdErr) System.err.println("# wbuff[" + i + "]=" + Integer.toBinaryString(wbuff[i]));
				}
			}

			//wbuff[0]=0x11223344;
			rsData.qBuff=wbuff;
		}


		if ( debug ) {
			if (stdErr) System.err.println("# raw results from DeviceModbusSlave: ");
			for ( int i=0 ; i<rsData.qBuff.length ; i++ ) {
				if (stdErr) System.err.format("[%d] 0x%04x %d\n",i,rsData.qBuff[i],rsData.qBuff[i]);
			}
		}


		return rsData;
	}

}
