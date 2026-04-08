package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class TestCaseScenario implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<TestCaseStep> steps;

}
