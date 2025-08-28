///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 11+
//DEPS org.openjdk.nashorn:nashorn-core:15.4
//DEPS org.jline:jline:3.26.0

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.script.*;
import org.jline.reader.*;
import org.jline.reader.impl.*;
import org.jline.reader.impl.completer.*;
import org.jline.terminal.*;
import org.openjdk.nashorn.api.scripting.*;

class DynamicWhitelistFilter implements ClassFilter {

    private final String filePath;
    public Set<String> allowedClasses;

    public DynamicWhitelistFilter(String filePath) {
        this.filePath = filePath;
        reload();
    }

    public void reload() {
        Set<String> set = new HashSet<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) set.add(line);
            }
        } catch (IOException e) {
            System.err.println(
                "\u001B[31mFailed to read whitelist.txt, defaulting to java.lang.Math only.\u001B[0m"
            );
            set.add("java.lang.Math");
        }
        this.allowedClasses = set;
    }

    @Override
    public boolean exposeToScripts(String className) {
        reload(); // reload before each eval
        return allowedClasses.contains(className);
    }
}

public class NashornCLI {

    public static void main(String... args) throws Exception {
        DynamicWhitelistFilter filter = new DynamicWhitelistFilter(
            "whitelist.txt"
        );
        NashornScriptEngineFactory factory = new NashornScriptEngineFactory();
        ScriptEngine engine = factory.getScriptEngine(filter);

        final String RED = "\u001B[31m",
            YELLOW = "\u001B[33m",
            CYAN = "\u001B[36m",
            RESET = "\u001B[0m";

        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader reader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(new StringsCompleter(filter.allowedClasses))
            .build();

        System.out.println(
            CYAN +
            "Nashorn CLI with live whitelist & autocomplete (type 'exit' to quit)" +
            RESET
        );

        while (true) {
            String line;
            try {
                line = reader.readLine(YELLOW + "> " + RESET);
            } catch (UserInterruptException | EndOfFileException e) {
                break;
            }

            if (line == null || line.trim().equalsIgnoreCase("exit")) break;

            try {
                Object result = engine.eval(line);
                if (result != null) System.out.println(result);
            } catch (ScriptException e) {
                if (e.getMessage().contains("Access to class")) {
                    System.out.println(
                        RED +
                        "⛔ Blocked class access: " +
                        e.getMessage() +
                        RESET
                    );
                } else {
                    System.out.println(
                        RED + "⚠ Error: " + e.getMessage() + RESET
                    );
                }
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ClassNotFoundException) {
                    System.out.println(
                        RED +
                        "⛔ Class not found / blocked: " +
                        cause.getMessage() +
                        RESET
                    );
                } else {
                    System.out.println(
                        RED + "⚠ Runtime error: " + e.getMessage() + RESET
                    );
                }
            }
        }
    }
}
