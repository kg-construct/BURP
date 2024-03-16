package burp.ls;

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

import burp.iteration.JSONIteration;
import burp.model.Iteration;
import net.minidev.json.JSONObject;

class JSONSource extends FileBasedLogicalSource {

	private static Configuration c = Configuration.builder().mappingProvider(new JacksonMappingProvider())
			.jsonProvider(new JacksonJsonProvider()).build().addOptions(Option.ALWAYS_RETURN_LIST);

	@Override
	public Iterator<Iteration> iterator() {
		try {
			if (iterations == null) {
				iterations = new ArrayList<Iteration>();
				String contents = Files.readString(Paths.get(getDecompressedFile()), encoding);

				List<Map<String, Object>> nodes = JsonPath.using(c).parse(contents).read(iterator);
				for (Map<String, Object> n : nodes) {
					iterations.add(new JSONIteration(JSONObject.toJSONString(n), nulls));
				}
			}
			return iterations.iterator();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

}