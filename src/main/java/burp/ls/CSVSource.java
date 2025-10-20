package burp.ls;

import java.io.FileReader;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import burp.model.Iteration;
import com.opencsv.CSVWriter;

class CSVSource extends FileBasedLogicalSource {

	public char delimiter = ',';
	public Boolean firstLineIsHeader = true;

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<>();

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

    // Use a LinkedHashMap to preserve a correspondence between keys and values
	private final Map<String, String> map = new LinkedHashMap<>();
	
	protected CSVIteration(String[] header, String[] rec, Set<Object> nulls) {
		super(nulls);

		for(int i = 0; i < header.length; i++) {
			map.put(header[i], rec[i]);
		}
	}

	@Override
	public List<Object> getValuesFor(String reference) {
		List<Object> l = new ArrayList<>();
		if(!map.containsKey(reference))
			throw new RuntimeException("Attribute " + reference + " does not exist.");
		
		String o = map.get(reference);
		if(nulls == null || !nulls.contains(o))
			l.add(o);
		
		return l;
	}

	@Override
	public List<String> getStringsFor(String reference) {
		return getValuesFor(reference).stream().map(Object::toString).collect(Collectors.toList());
	}

    @Override
    public String asString() {
        StringWriter stringWriter = new StringWriter();
        try (CSVWriter writer = new CSVWriter(stringWriter)) {
            String[] header = map.keySet().toArray(new String[0]);
            writer.writeNext(header);
            String[] rec = map.values().toArray(new String[0]);
            writer.writeNext(rec);
        } catch(Exception e) {
            throw new RuntimeException("Error representing CSV iteration as CSV.");
        }
        return stringWriter.toString();
    }

}