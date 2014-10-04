
public class Device {
	public int type;
	public int typeWorld;
	public int transmitEvery;
	public int serialNumber;
	
	
	public final static int DEV_TYPE_DISABLED                 =0;
	public final static int DEV_TYPE_MODBUS_1                 =1;
	public final static int DEV_TYPE_MODBUS_2                 =2;
	public final static int DEV_TYPE_MODBUS_3                 =3;
	public final static int DEV_TYPE_MODBUS_4                 =4;
	public final static int DEV_TYPE_MODBUS_5                 =5;
	public final static int DEV_TYPE_MODBUS_6                 =6;
	public final static int DEV_TYPE_MODBUS_16                =7;
	public final static int DEV_TYPE_SOUNDMETER_CENTER        =8;
	public final static int DEV_TYPE_SYSTEM_COMMAND           =9;
	public final static int DEV_TYPE_MODBUS_ADAM_4150_COUNTER =10;
	public final static int DEV_TYPE_FTULTRASONIC             =11;
	
	
	public static String getDeviceStringByValue(int n) {
		switch (n) {
			case DEV_TYPE_DISABLED:                 return "DEV_TYPE_DISABLED";
			case DEV_TYPE_MODBUS_1:                 return "DEV_TYPE_MODBUS_1";
			case DEV_TYPE_MODBUS_2:                 return "DEV_TYPE_MODBUS_2";
			case DEV_TYPE_MODBUS_3:                 return "DEV_TYPE_MODBUS_3";
			case DEV_TYPE_MODBUS_4:                 return "DEV_TYPE_MODBUS_4";
			case DEV_TYPE_MODBUS_5:                 return "DEV_TYPE_MODBUS_5";
			case DEV_TYPE_MODBUS_6:                 return "DEV_TYPE_MODBUS_6";
			case DEV_TYPE_MODBUS_16:                return "DEV_TYPE_MODBUS_16";
			case DEV_TYPE_SOUNDMETER_CENTER:        return "DEV_TYPE_SOUNDMETER_CENTER";
			case DEV_TYPE_SYSTEM_COMMAND:           return "DEV_TYPE_SYSTEM_COMMAND";
			case DEV_TYPE_MODBUS_ADAM_4150_COUNTER: return "DEV_TYPE_MODBUS_ADAM_4150_COUNTER";
			case DEV_TYPE_FTULTRASONIC:        return "DEV_TYPE_FTULTRASONIC";
			
			default: return "Unknown deviceType of " + n;
		}
	}
	
	public static int getDeviceTypeByName(String s) {
		s=s.toUpperCase();
		
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_1") ) 
			return DEV_TYPE_MODBUS_1;
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_2") ) 
			return DEV_TYPE_MODBUS_2;
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_3") ) 
			return DEV_TYPE_MODBUS_3;
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_4") ) 
			return DEV_TYPE_MODBUS_4;
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_5") ) 
			return DEV_TYPE_MODBUS_5;
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_6") ) 
			return DEV_TYPE_MODBUS_6;
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_16") ) 
			return DEV_TYPE_MODBUS_16;
		if ( 0==s.compareTo("DEV_TYPE_SOUNDMETER_CENTER") )
			return DEV_TYPE_SOUNDMETER_CENTER;
		if ( 0==s.compareTo("DEV_TYPE_SYSTEM_COMMAND") )
			return DEV_TYPE_SYSTEM_COMMAND;
		if ( 0==s.compareTo("DEV_TYPE_MODBUS_ADAM_4150_COUNTER") )
			return DEV_TYPE_MODBUS_ADAM_4150_COUNTER;
		if ( 0==s.compareTo("DEV_TYPE_FTULTRASONIC") )
			return DEV_TYPE_FTULTRASONIC;
		
		return DEV_TYPE_DISABLED;
	}
}
