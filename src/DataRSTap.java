
public class DataRSTap {
	public final static boolean debug=true;

	public long time;
	public int typeWorld;
	public int deviceSerialNumber;



	public int qBuff[]; /* really 16 bit values */
	public int qResult;

	public final static int EXCEPTION_NONE                     = 0;
	public final static int EXCEPTION_ILLEGAL_FUNCTION         = 1;
	public final static int EXCEPTION_ILLEGAL_DATA_ADDRESS     = 2;
	public final static int EXCEPTION_ILLEGAL_DATA_VALUE       = 3;
	public final static int EXCEPTION_SLAVE_DEVICE_FAILURE     = 4;
	public final static int EXCEPTION_ACKNOWLEDGE              = 5;
	public final static int EXCEPTION_SLAVE_DEVICE_BUSY        = 6;
	public final static int EXCEPTION_MEMORY_PARITY_ERROR      = 8;
	public final static int EXCEPTION_GATEWAY_PATH_UNAVAILABLE =10;
	public final static int EXCEPTION_TIMEOUT                  =12;


	public static String getExceptionByValue(int v) {
		switch (v) {
		case EXCEPTION_NONE: return "Success";
		case EXCEPTION_ILLEGAL_FUNCTION: return "Illegal Function";
		case EXCEPTION_ILLEGAL_DATA_ADDRESS: return "Illegal Data Address";
		case EXCEPTION_ILLEGAL_DATA_VALUE: return "Illegal Data Value";
		case EXCEPTION_SLAVE_DEVICE_FAILURE : return "Slave Device Failure";
		case EXCEPTION_ACKNOWLEDGE: return "Acknowledge";
		case EXCEPTION_SLAVE_DEVICE_BUSY: return "Slave Device Busy";
		case EXCEPTION_MEMORY_PARITY_ERROR: return "Memory Parity / Check Error";
		case EXCEPTION_GATEWAY_PATH_UNAVAILABLE: return "Gateway Path Unavailable";
		case EXCEPTION_TIMEOUT: return "Timeout";
		default: return "Unknown Exception";
		}
	}

	public static int crc_chk(int data[],int start, int length) {
		int j;
		int reg_crc=0xFFFF;

		for ( int i=start ; i<(length+start) ; i++ ) {
			reg_crc ^= data[i];

			for ( j=0 ; j<8 ; j++ ) {
				if ( (reg_crc&0x01) == 1 ) {
					reg_crc=(reg_crc>>1) ^ 0xA001;
				} else {
					reg_crc=reg_crc>>1;
				}
			}
		}

		return reg_crc;
	}
	
	public void qbuffFromBytes(byte[] b, int len) {
		int ua, ub;
		int shortSize = (int) Math.ceil(len/2.0);

		qBuff = new int[shortSize];
		
		for ( int bc=0, sc=0; sc < shortSize ; sc++, bc+=2 ) {
			/* stupid java doesn't have the concept of an unsigned byte */
			ua=b[bc];
			if ( ua < 0 )
				ua = 0 - ua;
			
			if ( bc+1 < len ) {
				/* stupid java doesn't have the concept of an unsigned byte */
				ub=b[bc+1];
				if ( ub < 0 )
					ub = 0 - ub;
				
				qBuff[sc] = (ub<<8) + ua;
//				System.err.printf("# bc=%d sc=%d ua=0x%02x ub=0x%02x qBuff[%d]=0x%04x\n", bc, sc, ua, ub, sc, qBuff[sc]);
			} else {
				qBuff[sc] = (ua&0xff);
//				System.err.printf("# bc=%d sc=%d ua=0x%02x qBuff[%d]=0x%04x\n", bc, sc, ua, sc, qBuff[sc]);
			}
		}
	}

	public void qbuffFromByteLengthInts(int[] b, int len) {
		int ua, ub;
		int shortSize = (int) Math.ceil(len/2.0);

		qBuff = new int[shortSize];
		
		for ( int bc=0, sc=0; sc < shortSize ; sc++, bc+=2 ) {
			ua=b[bc];
			
			if ( bc+1 < len ) {
				ub=b[bc+1];
				
				qBuff[sc] = (ub<<8) + ua;
			} else {
				qBuff[sc] = (ua&0xff);
			}
		}
	}

	public byte[] assemblePacket(int serialPrefix, int serialNumber, int measurementNumber) {
		if ( null == qBuff )
			return null;

		int completeLength=17 + qBuff.length*2 + 2;
		int[] buff=new int[completeLength];


		buff[0] ='#';
		buff[1] =(serialPrefix & 0xff);
		buff[2] =((serialNumber&0xff00)>>8);
		buff[3] =(serialNumber & 0xff);
		buff[4] =255; /* tell packet length to be read from 6 and 7 */
		buff[5] =18; /* packet type */
		buff[6] =((completeLength&0xff00)>>8);
		buff[7] =(completeLength & 0xff);

		/* measurement sequence number */
		buff[8] =((measurementNumber&0xff00)>>8);
		buff[9] =(measurementNumber&0xff);

		/* worldData type */
		buff[10]=((typeWorld&0xff00)>>8);
		buff[11]=(typeWorld & 0xff);

		/* device manufacturers serial number */
		buff[12]=((deviceSerialNumber&0xff000000)>>24);
		buff[13]=((deviceSerialNumber&0xff0000)>>16);
		buff[14]=((deviceSerialNumber&0xff00)>>8);
		buff[15]= (deviceSerialNumber&0xff);

		/* status of the data read */
		buff[16]=(qResult & 0xff);

		int j=17;
		for ( int i=0 ; i<qBuff.length ; i++,j+=2  ) {
			buff[j]  =((qBuff[i] & 0xff00)>>8);
			buff[j+1]=((qBuff[i] & 0xff));
		}

		int crc = crc_chk(buff, 1, buff.length-3);
		buff[buff.length-2]=(byte) ((crc>>8) & 0xff);
		buff[buff.length-1]=(byte) (crc & 0xff);

		byte bbuff[] = new byte[buff.length];
		for ( int i=0 ; i<buff.length ; i++ )
			bbuff[i]=(byte) buff[i];

		return bbuff;
	}


	/*
	 * @param t Time query started 
	 * @param mn measurementNumber or sequence number (16-bit)
	 * @param tw WorldData type (8-bit)
	 * @param sn Serial number (32-bit)
	 */
	public DataRSTap(long t, int mn, int tw, int sn) {
		time=t;
		typeWorld=tw;
		deviceSerialNumber=sn;
		qBuff=null;
	}

	public String toString() {
		/*
			System.err.printf("# assemblePacket serialPrefix=%d serialNumber=%d completeLength=%d measurementNumber=%d\n",
					serialPrefix,
					serialNumber,
					completeLength,
					measurementNumber
				);
		 */
		
		if ( null == qBuff )
			return String.format("\ttypeWorld=%d deviceSerialNumber=%d qResult=%d qBuff is null\n",
					typeWorld,
					deviceSerialNumber,
					qResult
				);


		return String.format("\ttypeWorld=%d deviceSerialNumber=%d qResult=%d qBuff.length=%d\n",
				typeWorld,
				deviceSerialNumber,
				qResult,
				qBuff.length
			);
		
	}


}
