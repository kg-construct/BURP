package burp;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.opencsv.CSVReader;

import net.minidev.json.JSONObject;

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

class JSONSource extends LogicalSource {
	
	private List<Iteration> iterations = null;
	public String file;
	public String iterator;
	
	private static Configuration c = Configuration
			.builder()
            .mappingProvider(new JacksonMappingProvider())
            .jsonProvider(new JacksonJsonProvider())
            .build()
            .addOptions(Option.ALWAYS_RETURN_LIST)
			;

	@Override
	protected Iterator<Iteration> iterator() {
		try {
			if(iterations == null) {
				iterations = new ArrayList<Iteration>();
				String contents = Files.readString(Paths.get(file));

				List<Map<String, Object>> nodes = JsonPath.using(c).parse(contents).read(iterator);
				for(Map<String,Object> n: nodes) {
					iterations.add(new JSONIteration(JSONObject.toJSONString(n)));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
}

class RDBSource extends LogicalSource {
	
	private List<Iteration> iterations = null;
	//public String file;

	@Override
	protected Iterator<Iteration> iterator() {
		try {
			if(iterations == null) {
				iterations = new ArrayList<Iteration>();

				
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}
	
}