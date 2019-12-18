package differ;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.DefaultParser;

public class RelaxedParser extends DefaultParser {

	private static final List<String> leftoverArgs = new ArrayList<String>();
	
    @Override
    public CommandLine parse(Options options, String[] arguments) throws ParseException {
        List<String> knownArguments = new ArrayList<>();
		//this is only ok bc we know all of our options have an arg
		for (int i = 0; i < arguments.length; i++) {
            if (options.hasOption(arguments[i])) {
                knownArguments.add(arguments[i]);
				knownArguments.add(arguments[i+1]);
				i++;
            }else{
				leftoverArgs.add(arguments[i]);
			}
        }
        return super.parse(options, knownArguments.toArray(new String[knownArguments.size()]));
    }

	//doesnt want to simply return the reference, others could then modify
	public void getLeftovers(List<String> newUnknowns){
		for(String arg: leftoverArgs){
			newUnknowns.add(arg);
		}
	}
}
