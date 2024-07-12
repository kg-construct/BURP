package burp.model.fnmlutil;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import burp.model.RMLFunction;
import burp.model.Return;
import org.apache.commons.text.WordUtils;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.vocabulary.XSD;

public class Functions {
	
	public static Map<String, RMLFunction> functions = prepareFunctions();

	static public List<Return> execute(String function, Map<String, Object> map) {
		
		RMLFunction f = functions.get(function);
		if(f != null)
			return f.apply(map);
		
		throw new RuntimeException(String.format("Function %s not yet supported.", function));
	}
	
	private static Map<String, RMLFunction> prepareFunctions() {
		Map<String, RMLFunction> functions = new HashMap<String, RMLFunction>();

		// Hello World Function

		functions.put("http://example.com/functions/helloworld", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					l.add(new Return("Hello World!"));
					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function helloworld.", e);
				}
			}
		});

		// Schema Function

		functions.put("http://example.com/functions/schema", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://example.com/functions/stringParameter").toString();

					String out = "https://schema.org/" + s;

					Return r = new Return(out);
					r.put("http://example.com/functions/stringOutput", out);
					l.add(r);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function schema.", e);
				}
			}
		});

		// parseURL Function

		functions.put("http://example.com/functions/parseURL", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				String s = map.get("http://example.com/functions/stringParameter").toString();

				try {
					List<Return> l = new ArrayList<Return>();
					URL url = new URL(s);

					String protocol = url.getProtocol();
					String domain = url.getHost();
					String path = url.getPath();

					Return r = new Return(path);
					r.put("http://example.com/functions/stringOutput", path);
					r.put("http://example.com/functions/protocolOutput", protocol);
					r.put("http://example.com/functions/domainOutput", domain);
					l.add(r);

					return l;
				} catch(Exception e) {
					throw new RuntimeException("Invalid URL given as input" + s, e);
				}
			}
		});

		// UUID Function

		functions.put("https://github.com/morph-kgc/morph-kgc/function/built-in.ttl#uuid", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try{
					List<Return> l = new ArrayList<Return>();
					l.add(new Return(UUID.randomUUID().toString()));
					return l;
				} catch(Exception e) {
					throw new RuntimeException("Problem calling function UUID.", e);
				}
			}
		});
		
		// GREL functions
		// https://openrefine.org/docs/manual/grelfunctions

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_chomp", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					String f = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sep").toString();

					String out = StringUtils.removeEnd(s, f);
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_chomp.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_contains", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					String f = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub").toString();

					boolean out = s.contains(f);
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_contains.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_contains_pattern", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					String p = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_regex").toString();

					boolean out = s.matches(p);
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_contains_pattern.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#endsWith", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					String f = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub").toString();

					boolean out = s.endsWith(f);
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function endsWith.", e);
				}
			}
		});
		
		// Escapes s in the given escaping mode. The mode can be one of: "html", "xml", "csv", "url", "javascript".
		// Note that quotes are required around your mode. See the recipes for examples of escaping and unescaping.
		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#escape", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					String p = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#modeParam").toString();

					String out = null;
					if("html".equals(p.toLowerCase()))
						out = StringEscapeUtils.escapeHtml4(s);
					else if("xml".equals(p.toLowerCase()))
						out = StringEscapeUtils.escapeXml11(s);
					else if("csv".equals(p.toLowerCase()))
						out = StringEscapeUtils.escapeCsv(s);
					else if("javascript".equals(p.toLowerCase()))
						out = StringEscapeUtils.escapeEcmaScript(s);
					else if("url".equals(p.toLowerCase()))
						out = URLEncoder.encode(s, StandardCharsets.UTF_8);
					else
						throw new RuntimeException(String.format("Mode %s not supported in GREL's escape function.", p));

					Return r = new Return(out);
					r.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(r);

					return l;
				} catch (RuntimeException e) {
					throw new RuntimeException("Problem calling function escape.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#length", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();

					int out = s.length();
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function length.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#math_abs", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					Literal s = (Literal) map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n");

					Object out = null;
					String duri = s.getDatatypeURI();

					if(XSD.integer.getURI().equals(duri) || XSD.xint.getURI().equals(duri)) {
						out = Math.abs(Integer.parseInt(s.getLexicalForm()));
					} else if(XSD.xdouble.getURI().equals(duri)) {
						out = Math.abs(Double.parseDouble(s.getLexicalForm()));
					} else if(XSD.xlong.getURI().equals(duri)) {
						out = Math.abs(Long.parseLong(s.getLexicalForm()));
					} else if(XSD.xfloat.getURI().equals(duri)) {
						out = Math.abs(Float.parseFloat(s.getLexicalForm()));
					} else if(XSD.xshort.getURI().equals(duri)) {
						out = Math.abs(Short.parseShort(s.getLexicalForm()));
					} else {
						throw new RuntimeException(String.format("Mode %s not supported in GREL's abs function.", s));
					}

					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_decimal", out);
					l.add(re);

					return l;
				} catch (RuntimeException e) {
					throw new RuntimeException("Problem calling function math_abs.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#math_ceil", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					Literal s = (Literal) map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n");

					Object out = null;
					String duri = s.getDatatypeURI();

					if(XSD.integer.getURI().equals(duri) || XSD.xint.getURI().equals(duri)) {
						out = (int) Math.ceil(Integer.parseInt(s.getLexicalForm()));
					} else if(XSD.xdouble.getURI().equals(duri)) {
						out = (int) Math.ceil(Double.parseDouble(s.getLexicalForm()));
					} else if(XSD.xlong.getURI().equals(duri)) {
						out = (int) Math.ceil(Long.parseLong(s.getLexicalForm()));
					} else if(XSD.xfloat.getURI().equals(duri)) {
						out = (int) Math.ceil(Float.parseFloat(s.getLexicalForm()));
					} else if(XSD.xshort.getURI().equals(duri)) {
						out = (int) Math.ceil(Short.parseShort(s.getLexicalForm()));
					} else {
						throw new RuntimeException(String.format("Mode %s not supported in GREL's ceil function.", s));
					}

					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out);
					l.add(re);

					return l;
				} catch (RuntimeException e) {
					throw new RuntimeException("Problem calling function math_ceil.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#math_floor", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					Literal s = (Literal) map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_dec_n");

					Object out = null;
					String duri = s.getDatatypeURI();

					if(XSD.integer.getURI().equals(duri) || XSD.xint.getURI().equals(duri)) {
						out = (int) Math.floor(Integer.parseInt(s.getLexicalForm()));
					} else if(XSD.xdouble.getURI().equals(duri)) {
						out = (int) Math.floor(Double.parseDouble(s.getLexicalForm()));
					} else if(XSD.xlong.getURI().equals(duri)) {
						out = (int) Math.floor(Long.parseLong(s.getLexicalForm()));
					} else if(XSD.xfloat.getURI().equals(duri)) {
						out = (int) Math.floor(Float.parseFloat(s.getLexicalForm()));
					} else if(XSD.xshort.getURI().equals(duri)) {
						out = (int) Math.floor(Short.parseShort(s.getLexicalForm()));
					} else {
						throw new RuntimeException(String.format("Mode %s not supported in GREL's floor function.", s));
					}

					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_number", out);
					l.add(re);

					return l;
				} catch (RuntimeException e) {
					throw new RuntimeException("Problem calling function math_floor.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#startsWith", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					String f = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_string_sub").toString();

					boolean out = s.startsWith(f);
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#output_bool", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function starts_with.", e);
				}
			}
		});

		// https://openrefine.org/docs/manual/grelfunctions#gets-n-from-n-to-optional
		// Similar to substring() when used in relation to strings, but when using get in the case
		// that the second argument n to is omitted a single character will be returned.
		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_get", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					Literal from = (Literal) map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_from");
					Literal to = (Literal) map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_opt_to");

					String out = null;
					int f = from.getInt();
					if(to != null) {
						int t = to.getInt();
						if(t > 0)
							out = s.substring(f, t);
						else
							// A negative character index counts from the end of the string.
							out = s.substring(f, s.length() + t);
					}
					else
						out = s.substring(f, f + 1);

					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_substring.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_replace", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					String f = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_find").toString();
					String r = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#param_replace").toString();

					String out = s.replaceAll(f, r);
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_replace.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_strip", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();

					String out = s.trim();
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_strip.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_substring", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();
					Literal from = (Literal) map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_from");
					Literal to = (Literal) map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#p_int_i_opt_to");

					String out = null;
					int f = from.getInt();
					if(to != null) {
						int t = to.getInt();
						if(t > 0)
							out = s.substring(f, t);
						else
							// A negative character index counts from the end of the string.
							out = s.substring(f, s.length() + t);
					}
					else
						out = s.substring(f);

					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_substring.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#string_trim", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();

					String out = s.trim();
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function string_trim.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#toLowerCase", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();

					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();

					String out = s.toLowerCase();
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function toLowerCase.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#toUpperCase", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();

					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();

					String out = s.toUpperCase();
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function toUpperCase.", e);
				}
			}
		});

		functions.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#toTitleCase", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();

					String s = map.get("http://users.ugent.be/~bjdmeest/function/grel.ttl#valueParam").toString();

					// GREL transforms FOO into Foo
					String out = WordUtils.capitalize(s.toLowerCase());
					Return re = new Return(out);
					re.put("http://users.ugent.be/~bjdmeest/function/grel.ttl#stringOut", out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function toTitleCase.", e);
				}
			}
		});
		
		
		// ID-Lab functions
		
		functions.put("http://example.com/idlab/function/toUpperCaseURL", new RMLFunction() {
			@Override
			public List<Return> apply(Map<String, Object> map) {
				try {
					List<Return> l = new ArrayList<Return>();
					String str = map.get("http://example.com/idlab/function/str").toString().toUpperCase();

					String out = null;
					if(str.startsWith("HTTP://"))
						out = str;
					else
						out = "http://" + str;

					Return re = new Return(out);
					l.add(re);

					return l;
				} catch (Exception e) {
					throw new RuntimeException("Problem calling function toUpperCaseURL.", e);
				}
			}
		});
		
		System.out.println("The following functions were loaded:");
		functions.keySet().forEach(s -> System.out.println("- %s".formatted(s)));
		
		return functions;
	}
	
}
