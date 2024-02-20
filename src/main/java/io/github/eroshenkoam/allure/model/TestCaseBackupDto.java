package io.github.eroshenkoam.allure.model;

import io.qameta.allure.ee.client.dto.CustomFieldValue;
import io.qameta.allure.ee.client.dto.Issue;
import io.qameta.allure.ee.client.dto.Member;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseRelation;
import io.qameta.allure.ee.client.dto.TestCaseScenario;
import io.qameta.allure.ee.client.dto.TestTag;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TestCaseBackupDto {

    private TestCase testCase;
    private List<TestTag> tags;
    private List<Issue> issues;
    private List<Member> members;
    private List<TestCaseRelation> relations;
    private List<CustomFieldValue> customFields;

    private TestCaseScenario scenario;

}
