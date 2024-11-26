import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<!DOCTYPE html>\n<html>\n\t<body>\n\t\t<h5>A simple html page!<h5>\n\t\t<a href \"https://youtu.be/dQw4w9WgXcQ?si=VlUh43Ag7CVSVUCc\">funny cat videos</a><br/>\n\t\t<button onClick \"doSomething()\">do something</button>\n\t</body>\n\t<script>\n\t\tfunction doSomething()\n\t\t{\n\t\t\talert(\"I did something!\");\n\t\t}\n\t</script>\n</html>";

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        runCommand(builder, seedInput, getMutatedInputs(seedInput, List.of(
                input -> List.of(input.replace("<html", "a")), // this is just a placeholder, mutators should not only do hard-coded string replacement
                input -> List.of(input.replace("<html", "")),
				input -> List.of(input.replace("=", " "))
        )));
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true); // redirect stderr to stdout
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, String seedInput, List<String> mutatedInputs) {
        Stream.concat(Stream.of(seedInput), mutatedInputs.stream()).forEach(
                input -> {
					System.out.println("input: " + input);
					try
					{
						Process process = builder.start();
						OutputStream processOut = process.getOutputStream();
						processOut.write(input.getBytes());
						processOut.flush();
						processOut.close();
						int exitcode = process.waitFor();
						System.out.println("exitcode: " + exitcode);
					}
					catch(IOException | InterruptedException e)
					{
						System.out.println("exception: " + e.getMessage());
						System.out.println("stacktrace:\n" + e.getStackTrace().toString());
					}
				}
        );
    }

    private static String readStreamIntoString(InputStream inputStream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        return reader.lines()
                .map(line -> line + System.lineSeparator())
                .collect(Collectors.joining());
    }

    private static List<String> getMutatedInputs(String seedInput, Collection<Function<String, List<String>>> mutators) {
        return mutators.stream().map(mutator -> mutator.apply(seedInput)).flatMap(List::stream).collect(Collectors.toList());
    }
}
