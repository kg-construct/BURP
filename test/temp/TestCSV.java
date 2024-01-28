package temp;

import urml.Main;

public class TestCSV {

	public static void main(String[] args) throws Exception {
		Main.main(new String[] { "-m", "test/temp/mapping-csv.ttl", "-o", "output-csv.ttl" });
	}
	
}
