package burp.model;

import burp.model.fnmlutil.Return;

import java.util.List;
import java.util.Map;

public abstract class RMLFunction {
	
	abstract public List<Return> apply(Map<String, Object> map);

}
