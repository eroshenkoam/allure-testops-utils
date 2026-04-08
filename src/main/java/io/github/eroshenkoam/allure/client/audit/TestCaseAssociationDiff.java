package io.github.eroshenkoam.allure.client.audit;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Set;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class TestCaseAssociationDiff implements Diff, Serializable {

    private static final long serialVersionUID = 1L;

    private DiffValueChange<Set<Long>> ids;

}
