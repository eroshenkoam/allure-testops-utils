package io.github.eroshenkoam.allure.client.dto.textmarkup.textmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
public class LinkMark implements TextMark {

    @Serial
    private static final long serialVersionUID = 1L;

    private Attrs attrs;

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attrs implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String href;
        private String rel;
        private String target;

    }
}
