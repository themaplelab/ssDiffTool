package differ;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class SemanticOptions extends Options {


	public SemanticOptions() {
		Option redefinitionClass = Option.builder().longOpt("redefcp").hasArg().required().desc("Specify the classpath of the redefinition class.").build();
		addOption(redefinitionClass);

		Option firstDest = Option.builder().longOpt("firstDest").hasArg().desc("Specify the first directory for the renamed class to go to.").build();
		addOption(firstDest);
		
		Option altDest = Option.builder().longOpt("altDest").hasArg().desc("Specify the directory for the diff phase output to go to.").build();
		addOption(altDest);
		
	}

}
