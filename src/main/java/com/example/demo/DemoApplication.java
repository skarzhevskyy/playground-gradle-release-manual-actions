package com.example.demo;

import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;

@Command(name = "demo", mixinStandardHelpOptions = true, versionProvider = DemoApplication.ManifestVersionProvider.class,
        description = "A simple demo application.")
public class DemoApplication implements Callable<Integer> {

    @Option(names = {"-n", "--name"}, description = "Your name.", defaultValue = "World")
    private String name;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DemoApplication()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Hello, " + name + "!");
        return 0;
    }

    static class ManifestVersionProvider implements IVersionProvider {
        public String[] getVersion() throws Exception {
            Package aPackage = DemoApplication.class.getPackage();
            String implementationVersion = aPackage != null ? aPackage.getImplementationVersion() : null;
            if (implementationVersion == null) {
                return new String[]{"No version information available"};
            }
            return new String[]{implementationVersion};
        }
    }
}
