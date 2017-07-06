package org.cloudgarden.dribbbletoplikers;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

public class Launcher {

    @Parameter(names = "-clientAccessToken", required = true)
    private String clientAccessToken;

    public static void main(String[] args) {
        final Launcher launcher = new Launcher();
        try {
            JCommander.newBuilder().addObject(launcher).build().parse(args);
        } catch (ParameterException e) {
            System.err.println("Could not start the application due to incomplete configuration: " + e.getMessage());
            System.exit(253);
        }
        launcher.run();
    }

    private void run() {

    }
}
