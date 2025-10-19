package burp.model;

public abstract class Field {

	public String fieldName ;

    protected String getPrefix(String parent) {
        return "<i>".equals(parent) ? "" : parent + ".";
    }

}

