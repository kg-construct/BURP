package burp;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.opencsv.CSVReader;

public abstract class LogicalSource {

	public List<Iteration> iterations = new ArrayList<Iteration>();
	protected abstract Iterator<Iteration> iterator();

}

class CSVSource extends LogicalSource {
	
	public String file;

	@Override
	protected Iterator<Iteration> iterator() {
		try {
			FileReader fr = new FileReader(file); 
			CSVReader reader = new CSVReader(fr); 
			List<String[]> all = reader.readAll();
			reader.close();
			
			String[] header = all.remove(0);
			for(String[] rec : all) {
				iterations.add(new CSVIteration(header, rec));
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
}