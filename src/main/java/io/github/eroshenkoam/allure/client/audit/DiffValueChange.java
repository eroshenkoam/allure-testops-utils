package io.github.eroshenkoam.allure.client.audit;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @param <T> the type of changed field.
 */
@Data
@Accessors(chain = true)
public class DiffValueChange<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonAlias("old_value")
    private T oldValue;

    @JsonAlias("new_value")
    private T newValue;

}
