package urml;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import com.opencsv.CSVReader;

public abstract class LogicalSource {

	public List<Iteration> iterations = new ArrayList<Iteration>();
	public abstract void load() throws Exception;

}

class CSVSource extends LogicalSource {
	
	public String file;

	@Override
	public void load() throws Exception {
		FileReader fr = new FileReader(file); 
		CSVReader reader = new CSVReader(fr); 
		List<String[]> all = reader.readAll();
		reader.close();
		
		String[] header = all.remove(0);
		for(String[] rec : all) {
			iterations.add(new CSVIteration(header, rec));
		}
		
	}
	
}