package burp;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.opencsv.CSVReader;

public abstract class LogicalSource {

	protected abstract Iterator<Iteration> iterator();

}

class CSVSource extends LogicalSource {
	
	private List<Iteration> iterations = null;
	public String file;

	@Override
	protected Iterator<Iteration> iterator() {
		try {
			if(iterations == null) {
				iterations = new ArrayList<Iteration>();
				
				FileReader fr = new FileReader(file); 
				CSVReader reader = new CSVReader(fr); 
				List<String[]> all = reader.readAll();
				reader.close();
				
				String[] header = all.remove(0);
				for(String[] rec : all) {
					iterations.add(new CSVIteration(header, rec));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
}