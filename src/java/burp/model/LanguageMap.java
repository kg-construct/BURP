package burp.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LanguageMap extends ExpressionMap {

	public List<String> generateStrings(Iteration i) {
		List<String> set = new ArrayList<String>();
		
		if(expression instanceof RDFNodeConstant) {
			// It is assumed to be a string, otherwise the shapes
			// Would have caught the error.
			set.add(((RDFNodeConstant) expression).constant.toString());
		}
		else if(expression instanceof Template) {
			set.addAll(((Template) expression).values(i));
		}
		else if(expression instanceof Reference) {
			for(Object o : ((Reference) expression).values(i))
				set.add(o.toString());
		}
		
		set.forEach((l) -> {
			if(!isValidLanguageCode(l))
				throw new RuntimeException("Invalid language code: " + l);
		});
		
		return set;
	}

	// Source of REGEX based on, but reduced: https://www.regextester.com/103066
	private static Pattern p = Pattern.compile("^(((?:([A-Za-z]{2,3}(-(?:[A-Za-z]{3}(-[A-Za-z]{3}){0,2}))?))(-(?:[A-Za-z]{4}))?(-(?:[A-Za-z]{2}|[0-9]{3}))?(-(?:[A-Za-z0-9]{5,8}|[0-9][A-Za-z0-9]{3}))*(-(?:[0-9A-WY-Za-wy-z](-[A-Za-z0-9]{2,8})+))*(-(?:x(-[A-Za-z0-9]{1,8})+))?)|(?:x(-[A-Za-z0-9]{1,8})+))$");
	private boolean isValidLanguageCode(String lang) {
		return p.matcher(lang).find();
	}

}