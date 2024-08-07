package burp.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;

import burp.model.fnmlutil.Functions;

public class FunctionExecution extends Expression {
	
	public FunctionMap functionMap;
	public List<Input> inputs = new ArrayList<Input>();
	public ReturnMap returnMap;

	public List<Object> values(Iteration i, String baseIRI) {
		List<Object> list = new ArrayList<Object>();
		
		// TODO: We assume that function maps, parameter maps, and input value maps only yield one value
		List<RDFNode> functions = functionMap.generateIRIs(i, baseIRI);
		if(functions.size() != 1)
			throw new RuntimeException("Function map should generate exactly one value.");
		
		String function = functions.get(0).asResource().getURI();
		
		// Bind parameters via a map
		Map<String, Object> map = new HashMap<String, Object>();
		
		for(Input input : inputs) {
			List<RDFNode> parameters = input.parameterMap.generateIRIs(i, baseIRI);
			if(parameters.size() != 1)
				throw new RuntimeException("Parameter map should generate exactly one value.");
			
			String parameter = parameters.get(0).asResource().getURI();
			
			List<RDFNode> inputs = input.inputValueMap.generateTerms(i, baseIRI);
			if(inputs.size() != 1)
				throw new RuntimeException("Input value map should generate exactly one value.");
			
			Object in = inputs.get(0).isResource() ? inputs.get(0) : inputs.get(0).asLiteral();
			
			map.put(parameter, in);
		}
		
		for(Return o : Functions.execute(function, map)) {
			// if return map is null, then we return the default return value
			// Otherwise, look for the value identified by the return map
			if(returnMap == null) {
				list.add(o.defaultValue);				
			} else {
				List<RDFNode> returns = returnMap.generateIRIs(i, baseIRI);
				if(returns.size() != 1)
					throw new RuntimeException("Input value map should generate exactly one value.");
				
				Object v = o.get(returns.get(0));
				if(v == null)
					throw new RuntimeException("Return value %s no known.".formatted(returns.get(0)));
				
				list.add(v);
			}
		}
		
		return list;	
	}

}