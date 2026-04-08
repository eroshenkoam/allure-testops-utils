package io.github.eroshenkoam.allure.model;

import io.github.eroshenkoam.allure.client.dto.CustomFieldValue;
import io.github.eroshenkoam.allure.client.dto.Issue;
import io.github.eroshenkoam.allure.client.dto.Member;
import io.github.eroshenkoam.allure.client.dto.TestCase;
import io.github.eroshenkoam.allure.client.dto.TestCaseRelation;
import io.github.eroshenkoam.allure.client.dto.TestCaseScenario;
import io.github.eroshenkoam.allure.client.dto.TestTag;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class TestCaseBackupDto {

    private TestCase testCase;
    private List<Issue> issues;
    private List<Member> members;
    private List<CustomFieldValue> customFields;

    private TestCaseScenario scenario;

}
