package burp.ls;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import burp.model.Iteration;

class CSVSource extends FileBasedLogicalSource {

	public char delimiter = ',';
	public Boolean firstLineIsHeader = true;

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();

				FileReader fr = new FileReader(getDecompressedFile(), encoding);
				
				CSVReader reader = new CSVReaderBuilder(fr)
			    .withCSVParser(new CSVParserBuilder()
			        .withSeparator(delimiter)
			        .build()
			    ).build();
				
				
				List<String[]> all = reader.readAll();
				reader.close();

				String[] header = null;
				
				// IF THE FIRST LINE IS THE HEADER, REMOVE THE FIRST FROM CSV
				// OTHERWISE, CREATE A LIST OF NUMBERED COLUMNS STARTING FROM ONE
				if(firstLineIsHeader)
					header = all.remove(0);
				else {
					int n = all.get(0).length;
					header = new String[n];
					for(int i = 0; i < n; i++)
						header[i] = Integer.toString(i + 1);
				}
				
				for (String[] rec : all) {
					iterations.add(new CSVIteration(header, rec, nulls));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}

class CSVIteration extends Iteration {

	private Map<String, String> map = new HashMap<String, String>();
	
	public CSVIteration(String[] header, String[] rec, Set<Object> nulls) {
		super(nulls);
		
		for(int i = 0; i < header.length; i++) {
			map.put(header[i], rec[i]);
		}
	}

	@Override
	public List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<Object>();
		if(!map.containsKey(reference))
			throw new RuntimeException("Attribute " + reference + " does not exist.");
		
		String o = map.get(reference);
		if(!nulls.contains(o))
			l.add(o);
		
		return l;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		List<String> l = new ArrayList<String>();
		if(!map.containsKey(reference))
			throw new RuntimeException("Attribute " + reference + " does not exist.");
		
		String o = map.get(reference);
		if(!nulls.contains(o))
			l.add(o);
		
		return l;
	}
	
}