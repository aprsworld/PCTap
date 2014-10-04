public class MicrochipFloat32 {

	static double microchipFloat32ToDouble(int a, int b, int c, int d) { 
		/* test for special case of zero */
		if ( 0==a && 0==b && 0==c && 0==d ) {
			return 0.0;
		} 

		
		int m_Exp = a - 127;	// removing bias

		// 23 is number of bits in mantissa, thus the 23 bit right shift
		double mantissa = ((double)(((b & 0x7F) << 16) + (c << 8) + d)) / Math.pow(2, 23) + 1.0;

		/* test for sign bit */
		if ( (b & 0x80) != 0 )	
			mantissa *= -1;
				
		/* non-zero */
		return (mantissa * Math.pow(2,m_Exp));
	}
	
	static int[] doubleToMicrochipFloat32(double d) {
		int[] a = new int[4];
		int mantissa=0;
		int exponent;
		
		if ( d == 0.0 || d == -0.0 ) {
			a[0]=0;
			a[1]=0;
			a[2]=0;
			a[3]=0;
			return a;
		}
		
		double z;
		z=Math.log(d) / Math.log(2.0);
		exponent = (int) z;
		if ( exponent > z ) {
			exponent--;
		}
		
		int biasedE = exponent + 127;
		
		double x = d / Math.pow(2.0, (double) exponent);
		
		for ( int k=0; k>-23; k--) {
			if (x >= Math.pow(2.0, k)) {
				mantissa++;		// set bit
				x -= Math.pow(2.0, k);	// update remainder
			}
			mantissa = mantissa << 1;
		}

		a[0]=biasedE;

		/* sign bit */
		if (d<0) 
			a[1] = 0x80;
		else 
			a[1] = 0;
			
		a[1] += ((int)mantissa & 0x7F0000) >> 16;
		a[2] = ((int)mantissa & 0xFF00) >> 8;
		a[3] = (int)mantissa & 0xFF;
	
		return a;
	}
	
	
}
