package io.github.eroshenkoam.allure.command;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "disable-file-users", mixinStandardHelpOptions = true,
        description = "Disable users in Allure TestOps"
)
public class FileDisableUsersCommand extends AbstractDisableUsersCommand {

    @CommandLine.Option(
            names = {"--users.file"},
            description = "Users File path",
            defaultValue = "${env:USER_FILE}"
    )
    protected String userFile;

    @Override
    public List<String> getUsersForDisable(final List<String> usernames) throws Exception {
        final Path file = Paths.get(userFile);
        if (!Files.exists(file)) {
            throw new RuntimeException(String.format("Can't find file at %s", file));
        }
        final String content = Files.readString(file);
        return Arrays.stream(content.split("\n"))
                .filter(usernames::contains)
                .collect(Collectors.toList());
    }

}
