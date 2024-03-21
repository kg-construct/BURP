package burp.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;

public class Functions {

	static public List<Object> execute(String function, Map<String, Object> map) {
		if("http://example.com/base/HelloWorld".equals(function))
			return executeHelloWorld(map);
		if("http://users.ugent.be/~bjdmeest/function/grel.ttl#toUpperCase".equals(function))
			return executeGrelToUpperCase(map);
		if("http://users.ugent.be/~bjdmeest/function/grel.ttl#escape".equals(function))
			return executeGrelEscape(map);
		
		throw new RuntimeException(String.format("Function %s not yet supported.", function));
	}
	
	// https://openrefine.org/docs/manual/grelfunctions
	// Escapes s in the given escaping mode. The mode can be one of: "html", "xml", "csv", "url", "javascript". 
	// Note that quotes are required around your mode. See the recipes for examples of escaping and unescaping.
	private static List<Object> executeGrelEscape(Map<String, Object> map) {
		List<Object> l = new ArrayList<Object>();
		String string = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
		String param = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#modeParam").toString();
		if("html".equals(param.toLowerCase()))
			l.add(StringEscapeUtils.escapeHtml4(string));
		else if("xml".equals(param.toLowerCase()))
			l.add(StringEscapeUtils.escapeXml11(string));
		else if("csv".equals(param.toLowerCase()))
			l.add(StringEscapeUtils.escapeCsv(string));
		else if("javascript".equals(param.toLowerCase()))
			l.add(StringEscapeUtils.escapeEcmaScript(string));
		else if("url".equals(param.toLowerCase()))
			l.add(URLEncoder.encode(string, StandardCharsets.UTF_8));
		else
			throw new RuntimeException(String.format("Mode %s not supported in GREL's escape function.", param));
		return l;
	}

	private static List<Object> executeGrelToUpperCase(Map<String, Object> map) {
		List<Object> l = new ArrayList<Object>();
		l.add(map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString().toUpperCase());
		return l;
	}

	static private List<Object> executeHelloWorld(Map<String, Object> map) {
		List<Object> l = new ArrayList<Object>();
		l.add("Hello World!");
		return l;
	}
	
}
