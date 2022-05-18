package io.github.eroshenkoam.allure.command;

import io.github.eroshenkoam.allure.model.TestCaseAttachmentDto;
import io.github.eroshenkoam.allure.model.TestCaseDto;
import io.github.eroshenkoam.allure.model.TestCaseStepDto;
import io.github.eroshenkoam.allure.model.TestCasesDto;
import io.github.eroshenkoam.allure.util.FreemarkerUtil;
import io.github.eroshenkoam.allure.util.PDFUtil;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Attachment;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.Scenario;
import io.qameta.allure.ee.client.dto.Step;
import io.qameta.allure.ee.client.dto.TestCase;
import okhttp3.ResponseBody;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@CommandLine.Command(
        name = "export-testcases", mixinStandardHelpOptions = true,
        description = "Export test cases to PDF"
)
public class ExportTestCasesCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--allure.testcase.filter"},
            description = "Test Case filter",
            defaultValue = "${env:ALLURE_TESTCASE_FILTER}",
            required = true
    )
    protected String allureTestCaseFilter;

    @CommandLine.Option(
            names = {"--output-dir"},
            description = "Export output directory",
            defaultValue = "./output"
    )
    protected Path outputPath;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final TestCaseService service = builder.create(TestCaseService.class);
        System.out.printf("Export testcases from project [%s] filter [%s]\n", allureProjectId, allureTestCaseFilter);

        final List<TestCaseDto> testCases = getTestCases(service);
        for (TestCaseDto testCase : testCases) {
            downloadAttachments(service, testCase.getId());
        }

        final TestCasesDto data = new TestCasesDto()
                .setTestCases(testCases);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("data", data);

        final String htmlContent = FreemarkerUtil.render("export-testcases.ftl", parameters);

        Files.createDirectories(outputPath);

        final Path htmlPath = outputPath.resolve("testcases.html");
        Files.write(htmlPath, htmlContent.getBytes(StandardCharsets.UTF_8));

        final Path pdfPath = outputPath.resolve("testcases.pdf");
        PDFUtil.saveToFile(htmlPath, pdfPath);
    }

    private List<TestCaseDto> getTestCases(final TestCaseService service) throws IOException {
        final List<TestCaseDto> testCases = new ArrayList<>();
        Page<TestCase> current = new Page<TestCase>().setNumber(-1);
        do {
            final Response<Page<TestCase>> response = service
                    .findByRql(allureProjectId, allureTestCaseFilter, current.getNumber() + 1, 10)
                    .execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Can not find launches: " + response.message());
            }
            current = response.body();
            for (TestCase item : current.getContent()) {
                final TestCaseDto testCase = convertTestCase(item);
                testCase.setSteps(getTestCaseSteps(service, testCase));
                testCases.add(testCase);
            }
        } while (current.getNumber() < current.getTotalPages());
        return testCases;
    }

    private List<TestCaseStepDto> getTestCaseSteps(final TestCaseService service,
                                                   final TestCaseDto testCase) throws IOException {
        final Response<Scenario> scenarioResponse = testCase.isAutomated()
                ? service.getScenarioFromRun(testCase.getId()).execute()
                : service.getScenario(testCase.getId()).execute();
        if (scenarioResponse.isSuccessful()) {
            return convertSteps(scenarioResponse.body().getSteps());
        }
        return new ArrayList<>();
    }

    private void downloadAttachments(final TestCaseService service, final Long testCaseId) throws IOException {
        System.out.printf("Download attachments for test case [%s]\n", testCaseId);

        final Path baseAttachmentsPath = outputPath.resolve("attachments").resolve(testCaseId.toString());
        Files.createDirectories(baseAttachmentsPath);

        final Response<Page<Attachment>> attachmentsResponse = service.getAttachments(testCaseId, 0, 100).execute();
        if (!attachmentsResponse.isSuccessful()) {
            return;
        }
        final List<TestCaseAttachmentDto> attachments = convertAttachments(attachmentsResponse.body().getContent());
        for (TestCaseAttachmentDto attachment : attachments) {
            System.out.printf("Download attachment [%s] for test case [%s]\n", attachment.getName(), testCaseId);
            final Path attachmentPath = baseAttachmentsPath.resolve(prepareAttachmentName(attachment.getName()));
            final Response<ResponseBody> attachmentResponse = service
                    .getAttachmentContent(attachment.getId()).execute();
            if (attachmentResponse.isSuccessful()) {
                Files.write(attachmentPath, attachmentResponse.body().bytes());
            }
        }
    }

    private TestCaseDto convertTestCase(final TestCase testCase) {
        return new TestCaseDto()
                .setId(testCase.getId())
                .setName(testCase.getName())
                .setAutomated(testCase.getAutomated());
    }

    private List<TestCaseStepDto> convertSteps(final List<Step> steps) {
        final List<TestCaseStepDto> convertedList = new ArrayList<>();
        if (Objects.nonNull(steps)) {
            steps.forEach(step -> {
                final TestCaseStepDto convertedItem = new TestCaseStepDto()
                        .setName(step.getName())
                        .setAttachments(convertAttachments(step.getAttachments()));
                convertedItem.setSteps(convertSteps(step.getSteps()));
                convertedList.add(convertedItem);
            });
        }
        return convertedList;
    }

    private List<TestCaseAttachmentDto> convertAttachments(final List<Attachment> attachments) {
        final List<TestCaseAttachmentDto> convertedList = new ArrayList<>();
        if (Objects.nonNull(attachments)) {
            attachments.stream().filter(this::filterAttachment).forEach(attachment -> {
                final TestCaseAttachmentDto convertedItem = new TestCaseAttachmentDto()
                        .setId(attachment.getId())
                        .setName(prepareAttachmentName(attachment.getName()))
                        .setContentType(attachment.getContentType())
                        .setContentLength(attachment.getContentLength());
                convertedList.add(convertedItem);
            });
        }
        return convertedList;
    }

    private boolean filterAttachment(final Attachment attachment) {
        return attachment.getContentType().equals("image/png");
    }

    private String prepareAttachmentName(final String name) {
        return name.replace(" ", "_");
    }
}
