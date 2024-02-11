package burp;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "urml")
public class Configuration {

	@Option(names = {"-h", "--help" }, usageHelp = true, description = "Display a help message")
	public boolean help = false;

	@Option(names= {"-m", "--mappingFile"}, description = "The R2RML mapping file", required = true)
	public String mappingFile = null;

	@Option(names= {"-o", "--outputFile"}, description = "The output file", required = false)
	public String outputFile = null;

	@Option(names = {"-b", "--baseIRI"}, description = "Used in resolving relative IRIs produced by the R2RML mapping" )
	public String baseIRI = null;
	
	public Configuration(String[] args) throws Exception {
		try {
			Configuration conf = CommandLine.populateCommand(this, args);
			if(conf.help) {
				new CommandLine(this).usage(System.out);
				System.exit(0);
			}
			
			if(conf.mappingFile == null) {
				throw new Exception("An RML mapping file is mandatory.");
			}
		} catch (CommandLine.ParameterException pe) {
			System.out.println(pe.getMessage());
			new CommandLine(this).usage(System.out);
			throw pe;
		}
	}
}