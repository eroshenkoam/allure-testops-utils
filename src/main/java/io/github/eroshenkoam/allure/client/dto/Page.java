package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * @author eroshenkoam (Artem Eroshenko).
 * @param <T> page body
 */
@Data
@Accessors(chain = true)
public class Page<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> content;

    private int size;
    private int number;

    private int totalElements;
    private int totalPages;

}
