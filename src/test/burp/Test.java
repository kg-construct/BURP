package burp;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class Test {
	
    public static void main(String[] args) throws Exception {
//    	System.out.println(doubleCanonicalMap(Double.valueOf("20.3050")));	
//    	System.out.println(doubleCanonicalMap(Double.valueOf("20.000000000")));		
//    	System.out.println(doubleCanonicalMap(Double.valueOf("20300")));	
//    	System.out.println(doubleCanonicalMap(Double.valueOf("-1305345345353")));
//    	
//    	System.out.println(doubleCanonicalMap2(Double.valueOf("20.3050")));	
//    	System.out.println(doubleCanonicalMap2(Double.valueOf("20.000000000")));		
//    	System.out.println(doubleCanonicalMap2(Double.valueOf("20300")));	
//    	System.out.println(doubleCanonicalMap2(Double.valueOf("-1305345345353")));
    	
    	Float f = Float.valueOf("1.76");
    	System.out.println(f);
    	System.out.println(Double.valueOf(f.toString()));
    	
    }

    // See: https://www.w3.org/TR/xmlschema11-2/#f-doubleCan
	private static String doubleCanonicalMap(Double d) {
		// We need to strip trailing zeros to ensure that 20 does not become 20.0
		BigDecimal f = BigDecimal.valueOf(d).stripTrailingZeros();
		
		// The number of digits in the unscaled value
		int p = f.precision();
		
		// We start from two digits 
		StringBuilder x = new StringBuilder("0.0");

		// Add the remaining digits to the pattern
		for (int i = 2; i < p; i++) {
            x.append("#");
        }
        x.append("E0");
        
        NumberFormat formatter = new DecimalFormat(x.toString());		
		return formatter.format(d);
	}
	
	private static String doubleCanonicalMap2(Double d) {
		BigDecimal f = BigDecimal.valueOf(d);
		
		// The number of digits in the unscaled value
		int p = f.precision();
		
		// We start from two digits 
		StringBuilder x = new StringBuilder("0.0");

		// Add the remaining digits to the pattern
		for (int i = 2; i < p; i++)
            x.append("#");
        
		// Let's not forget the e-notation
		x.append("E0");
        
		return new DecimalFormat(x.toString()).format(d);
	}
    
}
